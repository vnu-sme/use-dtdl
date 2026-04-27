package org.tzi.use.dtdl.integration.operations;

import org.tzi.use.main.Session;
import org.tzi.use.parser.shell.ShellCommandCompiler;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.soil.MEnterOperationStatement;
import org.tzi.use.uml.sys.soil.MExitOperationStatement;
import org.tzi.use.uml.sys.soil.MStatement;
import org.tzi.use.util.Log;

import java.io.PrintWriter;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class UseOperationExecutor implements OperationExecutor {
    private final Session session;
    private final OperationCatalog catalog;

    public UseOperationExecutor(Session session, OperationCatalog catalog) {
        this.session = Objects.requireNonNull(session, "session");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    @Override
    public OperationResult execute(String objectName, String operationName) {
        System.out.println("[OP-EXEC] request object=" + objectName + " op=" + operationName);

        if (session.system() == null) {
            return new OperationResult(false, objectName, null, operationName, null, "No system loaded", Instant.now(), null);
        }

        MSystem system = session.system();
        MObject target = system.state().objectByName(objectName);
        if (target == null) {
            return new OperationResult(false, objectName, null, operationName, null, "Object not found", Instant.now(), null);
        }

        logObjectSnapshot("before-exec", target, system);

        MClass cls = target.cls();
        String className = cls == null ? null : cls.name();

        OperationCatalog.OperationDescriptor descriptor = catalog.get(className, operationName).orElse(null);
        if (descriptor == null) {
            System.out.println("[OP-EXEC] descriptor not found for " + className + "::" + operationName);
            return new OperationResult(false, objectName, className, operationName, null,
                    "Operation not registered in catalog for " + className + "::" + operationName,
                    Instant.now(), null);
        }

        if (!descriptor.parameterNames.isEmpty()) {
            System.out.println("[OP-EXEC] parameters required: " + descriptor.parameterNames);
            return new OperationResult(false, objectName, className, operationName, null,
                    "Operation requires parameters: " + descriptor.parameterNames,
                    Instant.now(), null);
        }

        List<String> candidates = List.of(
                objectName + "." + operationName + "()",
                operationName + "(" + objectName + ")",
                operationName + " " + objectName
        );

        for (String command : candidates) {
            System.out.println("[OP-EXEC] trying command: " + command);

            MStatement statement = ShellCommandCompiler.compileShellCommand(
                    system.model(),
                    system.state(),
                    system.getVariableEnvironment(),
                    command,
                    "<operation-exec>",
                    new PrintWriter(System.err),
                    false
            );

            if (statement == null) {
                System.out.println("[OP-EXEC] compile failed: " + command);
                continue;
            }

            System.out.println("[OP-EXEC] compiled statement=" + statement.getClass().getSimpleName());

            try {
                if ((statement instanceof MEnterOperationStatement) || (statement instanceof MExitOperationStatement)) {
                    system.execute(statement, false, true, true);
                } else {
                    system.execute(statement);
                }

                logObjectSnapshot("after-system-execute", target, system);

                session.evaluatedStatement(statement);

                logObjectSnapshot("after-session-evaluatedStatement", target, system);
                System.out.println("[OP-EXEC] success with command: " + command);

                return new OperationResult(true, objectName, className, operationName, command, "Executed", Instant.now(), statement);
            } catch (Exception e) {
                System.out.println("[OP-EXEC] execution failed: " + e.getMessage());
                Log.error(e.getMessage());
                logObjectSnapshot("after-failed-exec", target, system);
                return new OperationResult(false, objectName, className, operationName, command, e.getMessage(), Instant.now(), statement);
            }
        }
        System.out.println("[OP-EXEC] no valid command found");

        logObjectSnapshot("after-no-valid-command", target, system);

        return new OperationResult(false, objectName, className, operationName, null,
                "Could not compile an executable USE command for this operation",
                Instant.now(), null);
    }

    // DEBUG ONLY: DELETED WHEN COMMIT
    private void logObjectSnapshot(String stage, MObject target, MSystem system) {
        if (target == null) {
            System.out.println("[OP-EXEC] " + stage + " target=null");
            return;
        }
        if (system == null) {
            System.out.println("[OP-EXEC] " + stage + " system=null");
            return;
        }

        System.out.println("[OP-EXEC] " + stage + " object=" + target.name() + " class=" + (target.cls() == null ? "null" : target.cls().name()));

        try {
            if (target.cls() == null) {
                return;
            }

            var objState = target.state(system.state());
            for (MAttribute attr : target.cls().allAttributes()) {
                Object value = null;
                try {
                    value = objState.attributeValue(attr);
                } catch (Throwable ignored) {
                }
                System.out.println("[OP-EXEC]   " + attr.name() + " = " + value);
            }
        } catch (Throwable t) {
            System.out.println("[OP-EXEC]   snapshot failed: " + t.getMessage());
        }
    }
}