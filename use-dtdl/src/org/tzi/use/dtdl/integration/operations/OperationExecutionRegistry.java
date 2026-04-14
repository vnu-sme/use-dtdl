package org.tzi.use.dtdl.integration.operations;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class OperationExecutionRegistry {
    private final Map<String, OperationExecutionRule> rules = new LinkedHashMap<>();
    private final Path storageFile;

    public OperationExecutionRegistry(Path storageFile) {
        this.storageFile = Objects.requireNonNull(storageFile, "storageFile");
    }

    public static OperationExecutionRegistry loadDefault() {
        Path p = Path.of(System.getProperty("user.home"), ".use-dtdl-operation-rules.json");
        try {
            Files.deleteIfExists(p);
        } catch (Exception ignored) {
        }
        return new OperationExecutionRegistry(p);
    }

    public synchronized void register(OperationExecutionRule rule) {
        rules.put(rule.id, rule);
        save();
    }

    public synchronized void remove(String ruleId) {
        rules.remove(ruleId);
        save();
    }

    public synchronized Map<String, OperationExecutionRule> all() {
        return Map.copyOf(rules);
    }

    public synchronized OperationExecutionRule get(String id) {
        return rules.get(id);
    }

    public synchronized void clear() {
        rules.clear();
        save();
    }

    public synchronized void save() {
        // intentionally in-memory only
    }

    public synchronized void load() {
        rules.clear();
    }

    public Path storageFile() {
        return storageFile;
    }

}