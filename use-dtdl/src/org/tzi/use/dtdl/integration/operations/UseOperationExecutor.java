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

        MClass cls = target.cls();
        String className = cls == null ? null : cls.name();

        OperationCatalog.OperationDescriptor descriptor = catalog.get(className, operationName).orElse(null);
        if (descriptor == null) {
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

        String command = objectName + "." + operationName + "()";

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
            return new OperationResult(false, objectName, className, operationName, null,
                    "Could not compile executable USE command", Instant.now(), null);
        }

        try {
            if ((statement instanceof MEnterOperationStatement) || (statement instanceof MExitOperationStatement)) {
                system.execute(statement, false, true, true);
            } else {
                system.execute(statement);
            }

            session.evaluatedStatement(statement);

            System.out.println("[OP-EXEC] success with command: " + command);

            return new OperationResult(true, objectName, className, operationName, command, "Executed", Instant.now(), statement);
        } catch (Exception e) {
            System.out.println("[OP-EXEC] execution failed: " + e.getMessage());
            Log.error(e.getMessage());
            return new OperationResult(false, objectName, className, operationName, command, e.getMessage(), Instant.now(), statement);
        }
    }
}