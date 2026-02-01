package org.tzi.use.dtdl.telemetry;


import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;


/**
 * Minimal in-memory binding registry. Users can register bindings programmatically.
 */
public final class BindingRegistry {
    public static final class Binding {
        public final String dtmi; // optional (null = match any)
        public final String telemetryName; // optional (null = match any)
        public final String adapterId; // adapter identifier
        public final String objectName; // optional explicit object target


        public Binding(String dtmi, String telemetryName, String adapterId, String objectName) {
            this.dtmi = dtmi;
            this.telemetryName = telemetryName;
            this.adapterId = adapterId;
            this.objectName = objectName;
        }


        public boolean matches(String dtmi, String telemetryName, String adapterId) {
            if (this.adapterId != null && adapterId != null && !this.adapterId.equals(adapterId)) return false;
            if (this.dtmi != null && dtmi != null && !this.dtmi.equals(dtmi)) return false;
            if (this.telemetryName != null && telemetryName != null && !this.telemetryName.equals(telemetryName)) return false;
            return true;
        }


        @Override
        public String toString() { return "Binding{" + dtmi + ":" + telemetryName + " -> " + objectName + " via " + adapterId + '}'; }
    }


    private final Map<String, Binding> bindings = new LinkedHashMap<>();


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
    // prefer exact matches, otherwise first matching entry
        for (Binding b : bindings.values()) {
            if (b.matches(dtmi, telemetryName, adapterId)) return Optional.of(b);
        }
        return Optional.empty();
    }
}