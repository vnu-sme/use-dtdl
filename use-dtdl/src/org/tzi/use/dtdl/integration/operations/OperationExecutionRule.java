package org.tzi.use.dtdl.integration.operations;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public final class OperationExecutionRule implements Serializable {
    private static final long serialVersionUID = 1L;

    public String id;
    public String objectName;
    public String className;
    public String operationName;
    public String constraintName;
    public boolean triggerWhenConstraintTrue;
    public boolean armed = true;
    public boolean lastConstraintValue;
    public boolean hasLastConstraintValue;
    public int fireCount;
    public String createdAt = Instant.now().toString();
    public String lastCheckedAt;
    public String lastExecutedAt;
    public String lastStatus;
    public String lastMessage;

    public OperationExecutionRule() {
        this.id = UUID.randomUUID().toString();
    }

    public OperationExecutionRule(String objectName,
                                  String className,
                                  String operationName,
                                  String constraintName,
                                  boolean triggerWhenConstraintTrue) {
        this();
        this.objectName = objectName;
        this.className = className;
        this.operationName = operationName;
        this.constraintName = constraintName;
        this.triggerWhenConstraintTrue = triggerWhenConstraintTrue;
    }

    @Override
    public String toString() {
        return id + " | " + objectName + " | " + operationName + " | " + constraintName + " | " +
                (triggerWhenConstraintTrue ? "true" : "false");
    }
}