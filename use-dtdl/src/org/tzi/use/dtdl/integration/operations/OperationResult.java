package org.tzi.use.dtdl.integration.operations;

import org.tzi.use.uml.sys.soil.MStatement;

import java.time.Instant;

public final class OperationResult {
    public final boolean success;
    public final String objectName;
    public final String className;
    public final String operationName;
    public final String command;
    public final String message;
    public final Instant timestamp;
    public final MStatement statement;

    public OperationResult(boolean success,
                           String objectName,
                           String className,
                           String operationName,
                           String command,
                           String message,
                           Instant timestamp,
                           MStatement statement) {
        this.success = success;
        this.objectName = objectName;
        this.className = className;
        this.operationName = operationName;
        this.command = command;
        this.message = message;
        this.timestamp = timestamp;
        this.statement = statement;
    }

    @Override
    public String toString() {
        return "[" + timestamp + "] " + objectName + "." + operationName + " -> " + (success ? "OK" : "FAIL") +
                (message == null ? "" : (" | " + message));
    }
}