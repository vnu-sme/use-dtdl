package org.tzi.use.dtdl.integration.operations;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class OperationCatalog {

    public static final class OperationDescriptor {
        public final String className;
        public final String operationName;
        public final List<String> parameterNames;

        public OperationDescriptor(String className, String operationName, List<String> parameterNames) {
            this.className = className;
            this.operationName = operationName;
            this.parameterNames = parameterNames == null ? List.of() : List.copyOf(parameterNames);
        }

        public String key() {
            return key(className, operationName);
        }

        public static String key(String className, String operationName) {
            return className + "::" + operationName;
        }

        @Override
        public String toString() {
            return className + "::" + operationName + " params=" + parameterNames;
        }
    }

    private final Map<String, OperationDescriptor> operations = new ConcurrentHashMap<>();

    public void register(String className, String operationName, List<String> parameterNames) {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(operationName, "operationName");
        operations.put(OperationDescriptor.key(className, operationName),
                new OperationDescriptor(className, operationName, parameterNames));
    }

    public Optional<OperationDescriptor> get(String className, String operationName) {
        return Optional.ofNullable(operations.get(OperationDescriptor.key(className, operationName)));
    }

    public List<OperationDescriptor> operationsForClass(String className) {
        List<OperationDescriptor> out = new ArrayList<>();
        for (OperationDescriptor d : operations.values()) {
            if (Objects.equals(d.className, className)) {
                out.add(d);
            }
        }

        return out;
    }

    public Map<String, OperationDescriptor> all() {
        return Collections.unmodifiableMap(operations);
    }

    public void clear() {
        operations.clear();
    }
}