package org.tzi.use.dtdl.integration.operations;


import org.tzi.use.main.Session;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.ocl.expr.Evaluator;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MObject;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class OperationExecutionService {
    private final Session session;
    private final OperationExecutionRegistry registry;
    private final OperationExecutor executor;
    private final AtomicBoolean dispatching = new AtomicBoolean(false);
    private final AtomicBoolean evaluating = new AtomicBoolean(false);
    private final ArrayDeque<OperationResult> history = new ArrayDeque<>();
    private static final int MAX_HISTORY = 300;

    public OperationExecutionService(Session session,
                                     OperationExecutionRegistry registry,
                                     OperationExecutor executor) {
        this.session = Objects.requireNonNull(session, "session");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public OperationExecutionRegistry registry() {
        return registry;
    }

    public List<OperationExecutionRule> rules() {
        return new ArrayList<>(registry.all().values());
    }

    public List<OperationResult> history() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    public void registerRule(OperationExecutionRule rule) {
        System.out.println("[OP-RULE] register " + rule);
        registry.register(rule);
    }

    public void removeRule(String ruleId) {
        System.out.println("[OP-RULE] remove " + ruleId);
        registry.remove(ruleId);
    }

    public OperationResult executeManual(String objectName, String operationName) {
        if (dispatching.get()) {
            return new OperationResult(false, objectName, null, operationName, null, "Busy executing another operation", Instant.now(), null);
        }

        dispatching.set(true);
        try {
            OperationResult result = executor.execute(objectName, operationName);
            pushHistory(result);
            return result;
        } finally {
            dispatching.set(false);
        }
    }

    public void evaluateAll() {
        if (session.system() == null) {
            return;
        }
        if (!evaluating.compareAndSet(false, true)) {
            return;
        }

        try {
            for (OperationExecutionRule rule : rules()) {
                System.out.println("[OP-RULE] evaluating " + rule.id);
                evaluateRule(rule);
            }
        } finally {
            evaluating.set(false);
        }
    }

    private void evaluateRule(OperationExecutionRule rule) {
        if (rule == null) {
            System.out.println("[OP-RULE] evaluateRule skipped: rule=null");
            return;
        }
        if (!rule.active) {
            System.out.println("[OP-RULE] evaluateRule skipped: inactive " + rule.id);
            return;
        }

        rule.lastCheckedAt = Instant.now().toString();

        System.out.println("[OP-RULE] evaluate rule=" + rule.id
                + " object=" + rule.objectName
                + " class=" + rule.className
                + " op=" + rule.operationName
                + " constraint=" + rule.constraintName
                + " triggerWhenTrue=" + rule.triggerWhenConstraintTrue
                + " armed=" + rule.armed);

        if (session.system() == null || rule.objectName == null || rule.operationName == null || rule.constraintName == null) {
            rule.lastStatus = "INVALID";
            rule.lastMessage = "Missing rule data";
            System.out.println("[OP-RULE] invalid rule data for " + rule.id);
            return;
        }

        MObject target = session.system().state().objectByName(rule.objectName);
        if (target == null) {
            rule.lastStatus = "OBJECT_MISSING";
            rule.lastMessage = "Object not found: " + rule.objectName;
            System.out.println("[OP-RULE] target missing for " + rule.id + ": " + rule.objectName);
            return;
        }

        logTargetSnapshot("target", target);

        if (rule.className != null && target.cls() != null && !rule.className.equals(target.cls().name())) {
            rule.lastStatus = "CLASS_MISMATCH";
            rule.lastMessage = "Object class mismatch: expected " + rule.className + ", got " + target.cls().name();
            System.out.println("[OP-RULE] class mismatch: expected=" + rule.className + " actual=" + target.cls().name());
            return;
        }

        MClassInvariant invariant = findInvariant(rule.constraintName);
        if (invariant == null || invariant.flaggedExpression() == null) {
            rule.lastStatus = "CONSTRAINT_MISSING";
            rule.lastMessage = "Constraint not found: " + rule.constraintName;
            System.out.println("[OP-RULE] invariant missing: " + rule.constraintName);
            return;
        }

        boolean satisfied;
        try {
            Evaluator evaluator = new Evaluator();
            Value v = evaluator.eval(invariant.flaggedExpression(), session.system().state());
            satisfied = v != null && v.isBoolean() && ((BooleanValue) v).value();
            System.out.println("[OP-RULE] constraint result=" + satisfied + " raw=" + v);
        } catch (Throwable t) {
            rule.lastStatus = "CONSTRAINT_ERROR";
            rule.lastMessage = t.getMessage();
            System.out.println("[OP-RULE] constraint error: " + t.getMessage());
            return;
        }

        rule.lastConstraintValue = satisfied;
        rule.hasLastConstraintValue = true;

        boolean shouldFire = satisfied == rule.triggerWhenConstraintTrue;
        System.out.println("[OP-RULE] shouldFire=" + shouldFire
                + " armed=" + rule.armed
                + " satisfied=" + satisfied
                + " lastConstraintValue=" + rule.lastConstraintValue
                + " fireCount=" + rule.fireCount);

        if (!shouldFire) {
            rule.armed = true;
            rule.lastStatus = "WAITING";
            rule.lastMessage = "Condition not met";
            System.out.println("[OP-RULE] re-armed " + rule.id + " because condition is not met");
            return;
        }

        if (!rule.armed) {
            rule.lastStatus = "DISARMED";
            rule.lastMessage = "Already fired for current condition";
            System.out.println("[OP-RULE] skipped " + rule.id + " because it is still disarmed");
            return;
        }

        if (dispatching.get()) {
            rule.lastStatus = "BUSY";
            rule.lastMessage = "Executor busy";
            System.out.println("[OP-RULE] executor busy, skip " + rule.id);
            return;
        }

        dispatching.set(true);
        try {
            System.out.println("[OP-RULE] firing " + rule.objectName + "." + rule.operationName);

            OperationResult result = executor.execute(rule.objectName, rule.operationName);
            pushHistory(result);
            rule.lastExecutedAt = Instant.now().toString();
            rule.fireCount++;
            rule.lastStatus = result.success ? "FIRED" : "FAILED";
            rule.lastMessage = result.message;
            rule.armed = false;

            System.out.println("[OP-RULE] result " + result);
            System.out.println("[OP-RULE] after fire armed=" + rule.armed + " fireCount=" + rule.fireCount + " lastStatus=" + rule.lastStatus);
        } finally {
            dispatching.set(false);
        }
    }

    private MClassInvariant findInvariant(String constraintName) {
        if (session.system() == null) {
            return null;
        }

        for (MClassInvariant inv : session.system().model().classInvariants()) {
            if (inv == null) {
                continue;
            }

            if (constraintName.equals(inv.name())) {
                return inv;
            }
            if (inv.qualifiedName() != null && constraintName.equals(inv.qualifiedName())) {
                return inv;
            }
        }
        return null;
    }

    private void pushHistory(OperationResult result) {
        if (result == null) {
            return;
        }
        synchronized (history) {
            history.addLast(result);
            while (history.size() > MAX_HISTORY) {
                history.removeFirst();
            }
        }
    }

    public String buildStatusText() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw, true);
        for (OperationExecutionRule r : rules()) {
            pw.println(r);
            pw.println("  lastStatus=" + r.lastStatus);
            pw.println("  lastMessage=" + r.lastMessage);
            pw.println("  fireCount=" + r.fireCount);
            pw.println("  lastCheckedAt=" + r.lastCheckedAt);
            pw.println("  lastExecutedAt=" + r.lastExecutedAt);
        }
        return sw.toString();
    }

    // DEBUG ONLY: DELETED WHEN COMMIT
    private void logTargetSnapshot(String stage, MObject target) {
        if (target == null) {
            System.out.println("[OP-RULE] " + stage + " target=null");
            return;
        }

        System.out.println("[OP-RULE] " + stage + " object=" + target.name() + " class=" + (target.cls() == null ? "null" : target.cls().name()));
    }
}