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
    public final Map<String, ASTSchema> schemas = new LinkedHashMap<>();
    public final List<SemanticError> errors = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();

//    // model-space registry (persistent across loads)
//    // Keep set of registered model objects and a fast map interfaceId -> model
//    private final List<DTDLModel> registeredModels = new ArrayList<>();
//    private final Map<String, DTDLModel> interfaceToModel = new LinkedHashMap<>();

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

    public void registerSchema(String id, ASTSchema s) {
        if (id == null || s == null) return;

        // If this schema id already registered in this AST-run, report duplicate.
        if (schemas.containsKey(id)) {
            report("Schema id '" + id + "' declared multiple times", id);
            return;
        }

        // If some already-registered model contains that schema id, report collision.
        if (registry != null && registry.hasModelSchema(id)) {
            report("Schema id '" + id + "' collides with schema declared in previously registered model", id);
            return;
        }

        // Otherwise register
        schemas.put(id, s);
    }

    public ASTSchema resolveSchemaRefGlobal(String ref) {
        if (ref == null) return null;

        // primitive
        if (SchemaFactory.isPrimitiveName(ref)) {
            return SchemaFactory.primitiveSchemaFromName(ref);
        }

        // interface-local schemas
        return schemas.get(ref);
    }


    public boolean hasModelSchema(String schemaId) {
        return registry.hasModelSchema(schemaId);
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