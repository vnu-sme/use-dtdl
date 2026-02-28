package org.tzi.use.dtdl.telemetry;

import org.tzi.use.dtdl.actions.DTDLPluginState;
import org.tzi.use.dtdl.semantic.DTDLModelRegistry;
import org.tzi.use.dtdl.use.UseRuntimeService;
import org.tzi.use.main.Session;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.ocl.expr.Evaluator;
import org.tzi.use.uml.ocl.expr.MultiplicityViolationException;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.DataTypeValueValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.dtdl.util.Utils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.Objects;

public final class TelemetryApplier {

    private final Session session;
    private final UseRuntimeService useService;
    private final DTDLModelRegistry registry;

    public TelemetryApplier(Session session) {
        this.session = Objects.requireNonNull(session, "session required");
        this.useService = new UseRuntimeService(session);
        this.registry = DTDLPluginState.registry();
    }

    @SuppressWarnings("unchecked")
    public boolean applyAndCheck(TelemetryFact fact) {
        if (fact == null) return false;
        if (session == null || session.system() == null) return false;

        String ifaceId = fact.interfaceId;
        String matchedObject = fact.matchedObjectName;
        Object normalized = fact.normalizedValue;

        if (matchedObject == null) return false;

        MObject target = session.system().state().objectByName(matchedObject);
        if (target == null) {
            System.err.println("[TelemetryApplier] target object not found: " + matchedObject);
            return false;
        }

        String className = null;
        if (ifaceId != null && registry != null) {
            var opt = registry.getClassNameForDtmi(ifaceId);
            if (opt.isPresent()) className = opt.get();
        }
        if (className == null && ifaceId != null) {
            className = Utils.sanitize(ifaceId);
        }

        try {
            UseSystemApi sysApi = UseSystemApi.create(session);
            MModel model = session.system().model();
            MClass cls = target.cls();

            if (normalized instanceof Map<?, ?>) {
                Map<String, Object> map = (Map<String, Object>) normalized;
                for (Map.Entry<String, Object> en : map.entrySet()) {
                    String attrName = en.getKey();
                    Object rawVal = en.getValue();
                    if (attrName == null) continue;

                    MAttribute mAttr = resolveAttribute(cls, attrName);
                    if (mAttr == null) {
                        System.err.println("[TelemetryApplier] attribute not found on class " + (cls != null ? cls.name() : "<null>") + ": " + attrName);
                        continue;
                    }

                    var attrType = mAttr.type();
                    var useVal = useService.buildUseValue(attrType, rawVal);

                    try {
                        sysApi.setAttributeValueEx(session.system().state().objectByName(target.name()), mAttr, useVal);
                    } catch (Exception ex) {
                        System.err.println("[TelemetryApplier] failed setting attribute '" + mAttr.name() + "' : " + ex.getMessage());
                        ex.printStackTrace(System.err);
                    }
                }
            } else {
                String attrName = fact.telemetryName;
                if (attrName != null) {
                    MAttribute mAttr = resolveAttribute(cls, attrName);
                    if (mAttr != null) {
                        var useVal = useService.buildUseValue(mAttr.type(), normalized);
                        try {
                            sysApi.setAttributeValueEx(session.system().state().objectByName(target.name()), mAttr, useVal);
                        } catch (Exception ex) {
                            System.err.println("[TelemetryApplier] failed setting attribute '" + mAttr.name() + "' : " + ex.getMessage());
                            ex.printStackTrace(System.err);
                        }
                    } else {
                        System.err.println("[TelemetryApplier] attribute not found (primitive write): " + attrName);
                    }
                } else {
                    System.err.println("[TelemetryApplier] no attribute name to write primitive telemetry");
                }
            }

            try {
                session.system().ensureStateLinkSetsForModel();
            } catch (Throwable ignored) {}
            try {
                session.system().state().updateDerivedValues(true);
            } catch (Throwable ignored) {}


            boolean classViolationDetected = false;
            StringWriter classDiagSw = new StringWriter();
            PrintWriter classDiagPw = new PrintWriter(classDiagSw, true);

            try {
                Evaluator evaluator = new Evaluator();
                for (MClassInvariant inv : model.classInvariants()) {
                    if (inv == null) continue;
                    if (!inv.isActive()) continue;
                    try {
                        Value v = evaluator.eval(inv.flaggedExpression(), session.system().state());
                        if (v == null) {
                            classViolationDetected = true;
                            classDiagPw.println("Multiplicity violation evaluating invariant " + inv.qualifiedName());
                            fact.addDiag("Multiplicity violation evaluating invariant " + inv.qualifiedName());
                        } else if (!v.isBoolean() || !((BooleanValue) v).value()) {
                            classViolationDetected = true;
                            classDiagPw.println("Invariant violated: " + inv.qualifiedName());
                            classDiagPw.println("  expression: " + inv.flaggedExpression());
                            classDiagPw.println("  result: " + v);
                            fact.addDiag("Invariant violated: " + inv.qualifiedName());
                            fact.addDiag("  result: " + v);
                        } else {
                            // satisfied; optionally log at debug level
                        }
                    } catch (MultiplicityViolationException mve) {
                        classViolationDetected = true;
                        classDiagPw.println("Multiplicity violation evaluating invariant " + inv.qualifiedName() + ": " + mve.getMessage());
                        StringWriter sw = new StringWriter();
                        mve.printStackTrace(new PrintWriter(sw, true));
                        classDiagPw.println(sw.toString());
                        fact.addDiag("Multiplicity violation evaluating invariant " + inv.qualifiedName() + ": " + mve.getMessage());
                    } catch (Throwable t) {
                        classViolationDetected = true;
                        classDiagPw.println("Error evaluating invariant " + inv.qualifiedName() + ": " + t.getMessage());
                        StringWriter sw = new StringWriter();
                        t.printStackTrace(new PrintWriter(sw, true));
                        classDiagPw.println(sw.toString());
                        fact.addDiag("Error evaluating invariant " + inv.qualifiedName() + ": " + t.getMessage());
                    }
                }
            } catch (Throwable t) {
                classDiagPw.println("Failed to evaluate class invariants: " + t.getMessage());
                StringWriter sw = new StringWriter();
                t.printStackTrace(new PrintWriter(sw, true));
                classDiagPw.println(sw.toString());
                fact.addDiag("Failed to evaluate class invariants: " + t.getMessage());
                classViolationDetected = true;
            }

            String classDiag = classDiagSw.toString();
            if (classDiag != null && !classDiag.isBlank()) {
                System.err.println("========== [TelemetryApplier] CLASS INVARIANT DIAGNOSTICS ==========");
                System.err.println(classDiag);
                System.err.println("==============================================================");
            }

            boolean overallOk = !classViolationDetected;


            fact.addDiag("Overall invariant status: " + (overallOk ? "OK" : "VIOLATED"));

            return !overallOk;

        } catch (Throwable t) {
            System.err.println("[TelemetryApplier] unexpected error while applying telemetry: " + t.getMessage());
            t.printStackTrace(System.err);
            fact.addDiag("[TelemetryApplier] unexpected error: " + t.getMessage());
            return false;
        }
    }

    private MAttribute resolveAttribute(MClass cls, String attrName) {
        if (cls == null || attrName == null) return null;
        MAttribute mAttr = cls.attribute(attrName, true);
        if (mAttr != null) return mAttr;
        if (attrName.startsWith(Utils.TELEMETRY_ATTR_PREFIX)) {
            return cls.attribute(attrName, true);
        }
        String telCandidate = Utils.TELEMETRY_ATTR_PREFIX + Utils.sanitize(attrName);
        mAttr = cls.attribute(telCandidate, true);
        if (mAttr != null) return mAttr;
        String sanitized = Utils.sanitize(attrName);
        mAttr = cls.attribute(sanitized, true);
        return mAttr;
    }

    public static void handleViolationAndStop(String adapterId, String details) {
        try {
            DTDLPluginState.detachAndUnregisterAdapter(adapterId);
        } catch (Throwable t) {
            System.err.println("[TelemetryApplier] failed to detach adapter " + adapterId + ": " + t.getMessage());
        }

        System.err.println("Telemetry constraint violation detected. Adapter stopped: " + adapterId);
        System.err.println("Details:\n" + details);

        try {
            String cleanMessage = buildUserMessage(adapterId, details);
            DTDLPluginState.telemetryEngine().fireViolation(adapterId, cleanMessage);
        } catch (Throwable ignored) {}

        throw new RuntimeException("Telemetry constraint violation detected for adapter " + adapterId + ": " + details);
    }

    private static String buildUserMessage(String adapterId, String details) {
        StringBuilder sb = new StringBuilder();
        sb.append("Telemetry stopped for adapter: ").append(adapterId).append("\n\n");

        if (details.contains("Invariant violated")) {
            for (String line : details.split("\\r?\\n")) {
                if (line.startsWith("Invariant violated")) {
                    sb.append("Constraint violated:\n");
                    sb.append(line.replace("Invariant violated:", "").trim());
                    break;
                }
            }
        } else {
            sb.append("A telemetry constraint was violated.");
        }

        return sb.toString();
    }
}