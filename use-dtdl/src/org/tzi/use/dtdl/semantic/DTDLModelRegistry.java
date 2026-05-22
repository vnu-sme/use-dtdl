package org.tzi.use.dtdl.semantic;

import org.tzi.use.dtdl.DTDLModel.DTDLModel;
import org.tzi.use.dtdl.DTDLModel.Interface;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that maintains a single canonical DTDLModel that contains all interfaces
 * registered so far. Also tracks which incoming DTDLModel contributed which interface IDs
 * so we can remove that contribution later.
 *
 * Thread-safe for the main operations.
 */
public final class DTDLModelRegistry {

    // canonical model that contains all imported interfaces
    private final DTDLModel canonical = new DTDLModel();

    // interfaceId -> source model (the model object that contributed this interface)
    private final ConcurrentHashMap<String, DTDLModel> interfaceToSource = new ConcurrentHashMap<>();

    // track contributions: source model -> set of interface IDs it added
    private final Map<DTDLModel, Set<String>> contributions = Collections.synchronizedMap(new LinkedHashMap<>());

    // persistent mapping DTMI -> class name in the Use model
    private final ConcurrentHashMap<String, String> dtmiToClassName = new ConcurrentHashMap<>();

    public static final class RegistrationResult {
        public final boolean success;
        public final List<String> conflicts; // list of conflicting interface ids
        public final boolean overwritten;

        public RegistrationResult(boolean success, List<String> conflicts, boolean overwritten) {
            this.success = success;
            this.conflicts = Collections.unmodifiableList(new ArrayList<>(conflicts));
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
                        // remove from canonical map (we don't know if other references exist elsewhere)
                        // DTDLModel currently stores interfaces in a map; remove the key
                        // We need a removal API: canonical.getInterfaces().remove(c)
                        synchronized (canonical) {
                            canonical.getInterfaces().remove(c);
                        }
                    }
                    // update contribution record
                    synchronized (contributions) {
                        Set<String> set = contributions.get(oldSource);
                        if (set != null) {
                            set.remove(c);
                            if (set.isEmpty()) contributions.remove(oldSource);
                        }
                    }
                }
            }
        }

        // Now add all interfaces from incoming model into canonical and record contributions
        Set<String> added = new HashSet<>();
        for (Map.Entry<String, Interface> e : model.getInterfaces().entrySet()) {
            String ifaceId = e.getKey();
            Interface iface = e.getValue();
            if (ifaceId == null || iface == null) continue;

            // Put into canonical model (replace any existing entry)
            synchronized (canonical) {
                canonical.addInterface(iface);
            }

            // record source mapping
            interfaceToSource.put(ifaceId, model);
            added.add(ifaceId);
        }

        // record contributions
        synchronized (contributions) {
            Set<String> prev = contributions.get(model);
            if (prev == null) prev = Collections.synchronizedSet(new LinkedHashSet<>());
            prev.addAll(added);
            contributions.put(model, prev);
        }

        return new RegistrationResult(true, conflicts, !conflicts.isEmpty());
    }

    /**
     * Remove all interfaces contributed by the given source model.
     * Returns true if any interfaces were removed.
     */
    public boolean unregisterModel(DTDLModel model) {
        if (model == null) return false;
        Set<String> contributed;
        synchronized (contributions) {
            contributed = contributions.remove(model);
        }
        if (contributed == null || contributed.isEmpty()) return false;

        boolean removedAny = false;
        for (String id : contributed) {
            // remove mapping and canonical entry if it still points to this source
            DTDLModel source = interfaceToSource.remove(id);
            if (source != null && source == model) {
                synchronized (canonical) {
                    Interface removed = canonical.getInterface(id);
                    if (removed != null) {
                        canonical.getInterfaces().remove(id);
                        removedAny = true;
                    }
                }
            }
        }
        return removedAny;
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
        synchronized (canonical) {
            i = canonical.getInterface(ifaceId);
        }
        return Optional.ofNullable(i);
    }

    /**
     * Check whether a schema id exists inside any interface in the canonical model.
     */
    public boolean hasModelSchema(String schemaId) {
        if (schemaId == null) return false;
        synchronized (canonical) {
            for (Interface iface : canonical.getInterfaces().values()) {
                if (iface.getSchemas().containsKey(schemaId)) return true;
            }
        }
        return false;
    }

    /**
     * Return the canonical model wrapped in a list for compatibility with existing callers.
     */
    public List<DTDLModel> listRegisteredModels() {
        synchronized (canonical) {
            return Collections.unmodifiableList(List.of(canonical));
        }
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
