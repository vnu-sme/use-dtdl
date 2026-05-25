package org.tzi.use.dtdl.semantic;

import org.tzi.use.dtdl.DTDLModel.DTDLModel;
import org.tzi.use.dtdl.DTDLModel.Interface;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that maintains a single canonical DTDLModel that contains all interfaces
 * registered so far. Also tracks which incoming DTDLModel contributed which interface IDs
 * so we can remove that contribution later.
 */
public final class DTDLModelRegistry {

    // canonical model that contains all imported interfaces
    private final DTDLModel canonical = new DTDLModel();

    // interfaceId -> source model (the model object that contributed this interface)
    private final Map<String, DTDLModel> interfaceToSource = new LinkedHashMap<>();

    // persistent mapping DTMI -> class name in the Use model
    private final Map<String, String> dtmiToClassName = new LinkedHashMap<>();

    public static final class RegistrationResult {
        public final boolean success;
        public final List<String> conflicts; // list of conflicting interface ids
        public final boolean overwritten;

        public RegistrationResult(boolean success, List<String> conflicts, boolean overwritten) {
            this.success = success;
            this.conflicts = List.copyOf(conflicts);
            this.overwritten = overwritten;
        }
    }

    public DTDLModelRegistry() {}

    /**
     * Register model. If overwrite==false and any interface id already exists in registry,
     * registration fails and conflicting ids are returned in result.conflicts.
     *
     * If overwrite==true, existing mappings for conflicting interface ids will be removed
     * from the canonical model and replaced by the incoming model's interfaces.
     */
    public RegistrationResult registerModel(DTDLModel model, boolean overwrite) {
        if (model == null) return new RegistrationResult(false, List.of(), false);

        List<String> conflicts = new ArrayList<>();
        for (String ifaceId : model.getInterfaces().keySet()) {
            if (interfaceToSource.containsKey(ifaceId)) {
                conflicts.add(ifaceId);
            }
        }

        if (!conflicts.isEmpty() && !overwrite) {
            // don't modify state
            return new RegistrationResult(false, conflicts, false);
        }

        // If overwriting, remove previous mappings for the conflicting interface ids
        if (!conflicts.isEmpty() && overwrite) {
            for (String c : conflicts) {
                DTDLModel oldSource = interfaceToSource.remove(c);
                // remove interface from canonical model if present and if it still belongs to oldSource
                if (oldSource != null) {
                    Interface removed = canonical.getInterface(c);
                    if (removed != null) {
                        canonical.getInterfaces().remove(c);
                    }
                }
            }
        }

        // Now add all interfaces from incoming model into canonical
        Set<String> added = new HashSet<>();
        for (Map.Entry<String, Interface> e : model.getInterfaces().entrySet()) {
            String ifaceId = e.getKey();
            Interface iface = e.getValue();
            if (ifaceId == null || iface == null) continue;

            // Put into canonical model (replace any existing entry)
            canonical.addInterface(iface);

            // record source mapping
            interfaceToSource.put(ifaceId, model);
            added.add(ifaceId);
        }

        return new RegistrationResult(true, conflicts, !conflicts.isEmpty());
    }

    /**
     * Returns the canonical model if the interface exists, otherwise empty.
     */
    public Optional<DTDLModel> getModelForInterface(String ifaceId) {
        if (ifaceId == null) return Optional.empty();
        if (interfaceToSource.containsKey(ifaceId)) {
            return Optional.of(canonical);
        }
        return Optional.empty();
    }

    /**
     * Return the Interface object from the canonical model for the given id.
     */
    public Optional<Interface> getInterfaceFromModels(String ifaceId) {
        if (ifaceId == null) return Optional.empty();
        Interface i;
        i = canonical.getInterface(ifaceId);
        return Optional.ofNullable(i);
    }

    /**
     * Check whether a schema id exists inside any interface in the canonical model.
     */
    public boolean hasModelSchema(String schemaId) {
        if (schemaId == null) return false;
        for (Interface iface : canonical.getInterfaces().values()) {
            if (iface.getSchemas().containsKey(schemaId)) return true;
        }
        return false;
    }

    /**
     * Return the canonical model (direct).
     */
    public DTDLModel getCanonicalModel() {
        return canonical;
    }

    public void registerClassMapping(String dtmi, String className) {
        if (dtmi == null || className == null) return;
        dtmiToClassName.put(dtmi, className);
    }

    public Optional<String> getClassNameForDtmi(String dtmi) {
        if (dtmi == null) return Optional.empty();
        String v = dtmiToClassName.get(dtmi);
        return v == null ? Optional.empty() : Optional.of(v);
    }

    public boolean hasClassForDtmi(String dtmi) {
        if (dtmi == null) return false;
        return dtmiToClassName.containsKey(dtmi);
    }

    public String findDTMIByClass(String className) {
        for (Map.Entry<String, String> entry : dtmiToClassName.entrySet()) {
            if (entry.getValue().equals(className)) {
                return entry.getKey();
            }
        }

        return null;
    }
}
