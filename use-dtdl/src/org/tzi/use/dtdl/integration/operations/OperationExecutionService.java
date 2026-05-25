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
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class OperationExecutionService {
    private final Session session;
    private final Map<String, OperationExecutionRule> rules = new LinkedHashMap<>();
    private final OperationExecutor executor;
    private final AtomicBoolean dispatching = new AtomicBoolean(false);
    private final AtomicBoolean evaluating = new AtomicBoolean(false);
    private final ArrayDeque<OperationResult> history = new ArrayDeque<>();
    private static final int MAX_HISTORY = 300;

    public OperationExecutionService(Session session, OperationExecutor executor) {
        this.session = Objects.requireNonNull(session, "session");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public List<OperationExecutionRule> rules() {
        return new ArrayList<>(rules.values());
    }

    public void removeRules() {
        rules.clear();
    }

    public List<OperationResult> history() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    public void registerRule(OperationExecutionRule rule) {
        System.out.println("[OP-RULE] register " + rule);
        rules.put(rule.id, rule);
    }

    public void removeRule(String ruleId) {
        rules.remove(ruleId);
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
//                System.out.println("[OP-RULE] evaluating " + rule.id);
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

        rule.lastCheckedAt = Instant.now().toString();

        System.out.println("[OP-RULE] evaluate rule=" + rule.id + " object=" + rule.objectName + " class=" + rule.className + " op=" + rule.operationName
                + " constraint=" + rule.constraintName + " triggerWhenTrue=" + rule.triggerWhenConstraintTrue + " armed=" + rule.armed);

        if (session.system() == null || rule.objectName == null || rule.operationName == null || rule.constraintName == null) {
            rule.lastStatus = "INVALID";
            rule.lastMessage = "Missing rule data";
            return;
        }

        MObject target = session.system().state().objectByName(rule.objectName);
        if (target == null) {
            return;
        }

        MClassInvariant invariant = findInvariant(rule.constraintName);
        if (invariant == null || invariant.flaggedExpression() == null) {
            return;
        }

        boolean satisfied;
        try {
            Evaluator evaluator = new Evaluator();
            Value v = evaluator.eval(invariant.flaggedExpression(), session.system().state());
            satisfied = v != null && v.isBoolean() && ((BooleanValue) v).value();
        } catch (Throwable t) {
            System.err.println("[OP-RULE] evaluate rule failed: " + t.getMessage());
            return;
        }

        rule.lastConstraintValue = satisfied;
        rule.hasLastConstraintValue = true;

        boolean shouldFire = satisfied == rule.triggerWhenConstraintTrue;

        if (!shouldFire) {
            rule.armed = true;
            rule.lastStatus = "WAITING";
            rule.lastMessage = "Condition not met";
            System.out.println("[OP-RULE] re-armed " + rule.id + " because condition is not met");
            return;
        }

        // match condition
        if (!rule.armed) {
            rule.lastStatus = "DISARMED";
            rule.lastMessage = "Already fired for current condition";
            System.out.println("[OP-RULE] skipped " + rule.id + " because it is still disarmed");
            return;
        }

        // match condition and allow to fire
        if (dispatching.get()) {
            rule.lastStatus = "BUSY";
            rule.lastMessage = "Executor busy";
            System.out.println("[OP-RULE] executor busy, skip " + rule.id);
            return;
        }

        dispatching.set(true);
        try {
            OperationResult result = executor.execute(rule.objectName, rule.operationName);
            pushHistory(result);
            rule.lastExecutedAt = Instant.now().toString();
            rule.fireCount++;
            rule.lastStatus = result.success ? "FIRED" : "FAILED";
            rule.lastMessage = result.message;
            rule.armed = false;
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
}