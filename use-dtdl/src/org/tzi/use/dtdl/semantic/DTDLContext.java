package org.tzi.use.dtdl.semantic;

import org.tzi.use.dtdl.DTDLModel.DTDLModel;
import org.tzi.use.dtdl.DTDLModel.Interface;
import org.tzi.use.dtdl.ast.*;
import org.tzi.use.dtdl.ast.schema.ASTSchema;
import org.tzi.use.dtdl.ast.schema.SchemaFactory;

import java.util.*;
import org.tzi.use.dtdl.ast.schema.*;
import java.util.IdentityHashMap;

/**
 * Context used during semantic analysis that also remembers previously
 * registered (converted) DTDLModel instances so later parses can consult them.
 */
public final class DTDLContext {
    // AST-space registries (used during a single analyze run)
    public final Map<String, ASTInterface> interfaces = new LinkedHashMap<>();
    public final List<SemanticError> errors = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();

    private final DTDLModelRegistry registry;

    public DTDLContext(DTDLModelRegistry registry) {
        this.registry = registry == null ? new DTDLModelRegistry() : registry;
    }

    public void report(String msg, String location) {
        errors.add(new SemanticError(msg, location));
    }

    public void report(String msg) {
        errors.add(new SemanticError(msg));
    }

    public void reportWarning(String msg) {
        warnings.add(msg);
    }

    public void clearWarnings() { warnings.clear(); }

    public boolean hasInterface(String id) {
        if (id == null) return false;
        if (interfaces.containsKey(id)) return true;
        return registry.getModelForInterface(id).isPresent();
    }

    public ASTInterface getInterface(String id) {
        return interfaces.get(id);
    }

    public void registerInterface(String id, ASTInterface iface) {
        interfaces.put(id, iface);
    }

    public boolean unregisterModel(DTDLModel model) {
        return registry.unregisterModel(model);
    }

    public DTDLModel getModelForInterface(String ifaceId) {
        return registry.getModelForInterface(ifaceId).orElse(null);
    }

    public Interface getInterfaceFromModels(String ifaceId) {
        return registry.getInterfaceFromModels(ifaceId).orElse(null);
    }

    public List<DTDLModel> listRegisteredModels() {
        return registry.listRegisteredModels();
    }
}