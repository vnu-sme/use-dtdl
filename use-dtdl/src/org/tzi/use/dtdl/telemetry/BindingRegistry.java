package org.tzi.use.dtdl.telemetry;


import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Minimal in-memory binding registry. Users can register bindings programmatically.
 */
public final class BindingRegistry {
    public static final class Binding {
        public final String dtmi; // optional (null = match any)
        public final String telemetryName; // optional (null = match any)
        public final String adapterId; // adapter identifier
        public final String objectName; // optional explicit object target

        // For primitive telemetry mapping: map entire telemetry to a single JSON path (e.g. "current.temperature_2m")
        public final String valuePath;

        // For object telemetry mapping: map fieldName -> JSON path (e.g. "temperature" -> "current.temperature_2m")
        public final Map<String, String> fieldPaths;

        public Binding(String dtmi, String telemetryName, String adapterId, String objectName) {
            this(dtmi, telemetryName, adapterId, objectName, null, null);
        }

        public Binding(String dtmi, String telemetryName, String adapterId, String objectName, String valuePath) {
            this(dtmi, telemetryName, adapterId, objectName, valuePath, null);
        }

        public Binding(String dtmi, String telemetryName, String adapterId, String objectName, Map<String, String> fieldPaths) {
            this(dtmi, telemetryName, adapterId, objectName, null, fieldPaths);
        }

        public Binding(String dtmi, String telemetryName, String adapterId, String objectName, String valuePath, Map<String,String> fieldPaths) {
            this.dtmi = dtmi;
            this.telemetryName = telemetryName;
            this.adapterId = adapterId;
            this.objectName = objectName;
            this.valuePath = valuePath;
            this.fieldPaths = fieldPaths == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(fieldPaths));
        }

        public boolean matches(String dtmi, String telemetryName, String adapterId) {
            if (this.adapterId != null && adapterId != null && !this.adapterId.equals(adapterId)) return false;
            if (this.dtmi != null && dtmi != null && !this.dtmi.equals(dtmi)) return false;
            if (this.telemetryName != null && telemetryName != null && !this.telemetryName.equals(telemetryName)) return false;
            return true;
        }

        @Override
        public String toString() {
            return "Binding{" + dtmi + ":" + telemetryName +
                    " -> " + objectName +
                    " via " + adapterId +
                    (valuePath != null ? (" valuePath=" + valuePath) : "") +
                    (fieldPaths != null ? (" fieldPaths=" + fieldPaths) : "") +
                    '}';
        }
    }


    private final Map<String, Binding> bindings = new ConcurrentHashMap<>();


    public BindingRegistry() {}


    public void register(String id, Binding b) {
        bindings.put(id, b);
    }


    public void unregister(String id) {
        bindings.remove(id);
    }


    public Map<String,Binding> all() {
        return Collections.unmodifiableMap(bindings);
    }


    public Optional<Binding> find(String dtmi, String telemetryName, String adapterId) {
        // prefer strongest exact match: adapterId + telemetryName + dtmi
        for (Binding b : bindings.values()) {
            if (Objects.equals(b.adapterId, adapterId)
                    && Objects.equals(b.telemetryName, telemetryName)
                    && Objects.equals(b.dtmi, dtmi)) {
                return Optional.of(b);
            }
        }
        // fallback: any binding that matches per matches() rules
        for (Binding b : bindings.values()) {
            if (b.matches(dtmi, telemetryName, adapterId)) return Optional.of(b);
        }
        return Optional.empty();
    }


    /**
     * Return only bindings that either explicitly target the given adapterId or are global (adapterId == null)
     */
    public Collection<Binding> bindingsForAdapter(String adapterId) {
        List<Binding> out = new ArrayList<>();
        for (Binding b : bindings.values()) {
            if (b == null) continue;
            if (b.adapterId == null) { // global binding -> always candidate
                out.add(b);
                continue;
            }
            if (adapterId != null && adapterId.equals(b.adapterId)) {
                out.add(b);
            }
        }
        return out;
    }
}