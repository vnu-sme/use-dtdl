package org.tzi.use.dtdl.ast;


import org.tzi.use.dtdl.ast.schema.*;
import org.tzi.use.dtdl.ast.schema.ASTSchema;
import org.tzi.use.dtdl.semantic.DTDLContext;
import org.tzi.use.dtdl.semantic.SemanticAnalyzer;
import org.tzi.use.dtdl.semantic.SemanticAnalyzerImpl;

import java.util.*;

import static org.tzi.use.dtdl.ast.schema.SchemaFactory.primitiveSchemaFromName;

public class ASTInterface extends ASTNode {
    public String displayName;
    public String context;
    protected List<ASTRelationship> relationships;
    protected List<ASTBidirectionalRelationship> bidirectionalRelationships;
    protected List<ASTProperty> properties;
    protected List<ASTCommand> commands;
    protected List<ASTComponent> components;
    protected List<ASTTelemetry> telemetries;
    public List<String> extendsInterfaces;
    protected List<ASTSchema> schemas;
    public java.util.Map<String,Object> props = new java.util.LinkedHashMap<>();
    public java.util.Map<String, ASTSchema> schemaIndex = new java.util.LinkedHashMap<>();

    public ASTInterface() {
        relationships = new ArrayList<>();
        properties = new ArrayList<>();
        commands = new ArrayList<>();
        components = new ArrayList<>();
        telemetries = new ArrayList<>();
        schemas = new ArrayList<>();
        bidirectionalRelationships = new ArrayList<>();
    }

    public void addRelationship(ASTRelationship a) {
        relationships.add(a);
    }

    public List<ASTRelationship> getRelationships() {
        return relationships;
    }

    public void addBidirectionalRelationship(ASTBidirectionalRelationship r) {
        bidirectionalRelationships.add(r);
    }

    public List<ASTBidirectionalRelationship> getBidirectionalRelationships() {
        return bidirectionalRelationships;
    }

    public void addProperty(ASTProperty a) {
        properties.add(a);
    }

    public void addCommand(ASTCommand a) {
        commands.add(a);
    }

    public void addComponent(ASTComponent a) {
        components.add(a);
    }

    public void addTelemetry(ASTTelemetry a) {
        telemetries.add(a);
    }

    public void addSchema(ASTSchema a) {
        schemas.add(a);
    }

    public List<String> getExtendsList() {
        if (this.extendsInterfaces != null && !this.extendsInterfaces.isEmpty()) {
            return new ArrayList<>(this.extendsInterfaces);
        }

        Object ext = props.get("extends");
        if (!(ext instanceof List<?>)) return List.of();

        List<String> result = new ArrayList<>();
        for (Object o : (List<?>) ext) {
            if (o instanceof String s) {
                result.add(s);
            }
        }
        return result;
    }

    public void printsAll() {
        System.out.println("=== INTERFACE: " + (displayName == null ? "(no displayName)" : displayName)
                + "  (id=" + id + ") ===");
        System.out.println("context: " + (context == null ? "<none>" : context));
        System.out.println("description: " + (description == null ? "<none>" : description));
        System.out.println("extends: " + (extendsInterfaces == null ? "[]" : extendsInterfaces));
        System.out.println("--- Schemas ---");
        if (schemas == null || schemas.isEmpty()) {
            System.out.println("  <none>");
        } else {
            for (ASTSchema s : schemas) {
                System.out.println("  - " + (s == null ? "<null>" : s.getClass().getSimpleName() + " id=" + s.id));
                if (s != null) s.prints();
            }
        }
        System.out.println("--- Relationships ---");
        if (relationships.isEmpty()) System.out.println("  <none>");
        else for (ASTRelationship r : relationships) r.prints();

        System.out.println("--- Bi-directional Relationships ---");
        if (bidirectionalRelationships.isEmpty()) System.out.println("  <none>");
        else for (ASTBidirectionalRelationship r : bidirectionalRelationships) r.prints();

        System.out.println("--- Properties ---");
        if (properties.isEmpty()) System.out.println("  <none>");
        else for (ASTProperty p : properties) p.prints();

        System.out.println("--- Commands ---");
        if (commands.isEmpty()) System.out.println("  <none>");
        else for (ASTCommand c : commands) c.prints();

        System.out.println("--- Components ---");
        if (components.isEmpty()) System.out.println("  <none>");
        else for (ASTComponent c : components) c.prints();

        System.out.println("--- Telemetries ---");
        if (telemetries.isEmpty()) System.out.println("  <none>");
        else for (ASTTelemetry t : telemetries) t.prints();

        System.out.println("=== /INTERFACE ===");
    }

    public void resolveAll() {
        // 1) Register top-level schemas into schemaIndex (so property/telemetry refs can be resolved)
        Object rawSchemas = this.props.get("schemas");
        if (rawSchemas instanceof List<?>) {
            for (Object s : (List<?>) rawSchemas) {
                ASTSchema as = null;
                if (s instanceof ASTSchema) {
                    as = (ASTSchema) s;
                } else if (s instanceof Map<?, ?> m) {
                    // Build an ASTSchema skeleton from raw map to store in index (deferred resolution)
                    Object t = m.get("@type");
                    if (t instanceof String) {
                        switch ((String) t) {
                            case "Enum" -> as = new ASTEnum();
                            case "Object" -> as = new ASTObject();
                            case "Map" -> as = new ASTMap();
                            default -> as = null;
                        }
                        if (as != null) {
                            // copy raw props (resolver will interpret them later)
                            as.props.putAll((Map<? extends String, ?>) m);
                        }
                    }
                }

                if (as != null) {
                    Object id = as.props.get("@id");
                    if (id instanceof String) {
                        schemaIndex.put((String) id, as);
                    }
                    this.addSchema(as);
                }
            }
        }

        // 2) Resolve the interface contents (properties, telemetries, commands, etc.)
        resolveInterface(this);

        // 3) Resolve the collected/registered schemas (resolve nested fields/values)
        for (ASTSchema s : new ArrayList<>(schemaIndex.values())) {
            // ensure canonicalization and deep-resolve
            resolveSchema(s);
        }
        // Also resolve any schemas added via addSchema that might not have been in schemaIndex
        for (ASTSchema s : this.schemas) {
            if (s != null && (s.props.get("@id") == null || !schemaIndex.containsValue(s))) {
                resolveSchema(s);
            }
        }
    }

    // resolve either primitive name or DTMI reference registered in schemaIndex
    ASTSchema resolveSchemaRef(String ref) {
        if (ref == null) return null;

        // 1. Primitive schema
        if (SchemaFactory.isPrimitiveName(ref)) {
            return SchemaFactory.primitiveSchemaFromName(ref);
        }

        // 2. DTMI schema
        return schemaIndex.get(ref);

    }

    void resolveInterface(ASTInterface iface) {

        iface.id = (String) iface.props.get("@id");
        iface.displayName = (String) iface.props.get("displayName");
        iface.description = (String) iface.props.get("description");

        Object contents = iface.props.get("contents");
        if (contents instanceof java.util.List<?>) {
            for (Object o : (java.util.List<?>) contents) {
                if (o instanceof ASTContent c) {
                    switch (c.semanticType) {
                        case "Property"   -> iface.addProperty((ASTProperty)c);
                        case "Telemetry"  -> iface.addTelemetry((ASTTelemetry)c);
                        case "Command"    -> iface.addCommand((ASTCommand)c);
                        case "Relationship" -> iface.addRelationship((ASTRelationship)c);
                        case "Component"  -> iface.addComponent((ASTComponent)c);
                        default -> { /* ignore */ }
                    }
                }
            }
        }

        // Resolve schemas embedded in properties/telemetries/commands/relationships/components
        iface.properties.forEach(this::resolveProperty);
        iface.telemetries.forEach(this::resolveTelemetry);
        iface.commands.forEach(this::resolveCommand);
        iface.relationships.forEach(this::resolveRelationship);
        iface.components.forEach(this::resolveComponent);
    }


    void resolveProperty(ASTProperty p) {
        p.name = (String) p.props.get("name");
        resolveGeneralInfo(p);

        p.schema = resolveSchemaFromProps(p.props.get("schema"));

        // extract semantic-type metadata (may have been parsed from @type array)
        Object semListObj = p.props.get("semanticTypes");
        if (semListObj instanceof java.util.List<?>) {
            p.semanticTypes = new java.util.ArrayList<>();
            for (Object it : (java.util.List<?>) semListObj) {
                if (it instanceof String s) p.semanticTypes.add(s);
            }
            if (!p.semanticTypes.isEmpty()) {
                p.semanticTypePrimary = p.semanticTypes.get(0);
            }
        } else {
            Object semObj = p.props.get("semanticType");
            if (semObj instanceof String) {
                p.semanticTypes = new java.util.ArrayList<>();
                p.semanticTypes.add((String) semObj);
                p.semanticTypePrimary = (String) semObj;
            }
        }

        // extract unit metadata if present
        Object u = p.props.get("unit");
        if (u instanceof String) p.unit = (String) u;

        Object w = p.props.get("writable");
        if (w instanceof Boolean)
            p.writable = (Boolean) w;
    }

    void resolveTelemetry(ASTTelemetry t) {
        t.name = (String) t.props.get("name");
        resolveGeneralInfo(t);

        t.schema = resolveSchemaFromProps(t.props.get("schema"));

        // extract semantic-type metadata (may have been parsed from @type array)
        Object semListObj = t.props.get("semanticTypes");
        if (semListObj instanceof java.util.List<?>) {
            t.semanticTypes = new java.util.ArrayList<>();
            for (Object it : (java.util.List<?>) semListObj) {
                if (it instanceof String s) t.semanticTypes.add(s);
            }
            if (!t.semanticTypes.isEmpty()) {
                t.semanticTypePrimary = t.semanticTypes.get(0);
            }
        } else {
            Object semObj = t.props.get("semanticType");
            if (semObj instanceof String) {
                t.semanticTypes = new java.util.ArrayList<>();
                t.semanticTypes.add((String) semObj);
                t.semanticTypePrimary = (String) semObj;
            }
        }

        // extract unit metadata if present
        Object u = t.props.get("unit");
        if (u instanceof String) t.unit = (String) u;
    }

    void resolveCommand(ASTCommand c) {
        c.name = (String) c.props.get("name");
        resolveGeneralInfo(c);

        ASTContent req = ASTContent.fromRaw(c.props.get("request"));
        if (req != null) {
            c.request = resolveCommandPayload(req);
        }

        ASTContent res = ASTContent.fromRaw(c.props.get("response"));
        if (res != null) {
            c.response = resolveCommandPayload(res);
        }
    }

    ASTCommandPayload resolveCommandPayload(ASTContent raw) {
        ASTCommandPayload p = new ASTCommandPayload();

        // copy props so validation can read them
        if (raw.props != null) p.props.putAll(raw.props);

        resolveGeneralInfo(p);

        p.name = raw.name;
        p.id = raw.id;
        p.description = raw.description;
        p.displayName = raw.displayName;
        p.type = raw.type;
        p.comment = raw.comment;

        p.schema = resolveSchemaFromProps(raw.props.get("schema"));

        Object nullable = raw.props.get("nullable");
        if (nullable instanceof Boolean) p.nullable = (Boolean) nullable;

        return p;
    }

    void resolveRelationship(ASTRelationship r) {
        r.name = (String) r.props.get("name");
        resolveGeneralInfo(r);
        r.targetInterface = (String) r.props.get("target");

        Object min = r.props.get("minMultiplicity");
        Object max = r.props.get("maxMultiplicity");

        if (min instanceof String) {
            try {
                r.minMultiplicity = Integer.parseInt((String)min);
            } catch(Exception ignore){}
        } else if (min instanceof Number) r.minMultiplicity = ((Number)min).intValue();

        if (max instanceof String) {
            try {
                r.maxMultiplicity = Integer.parseInt((String)max);
            } catch(Exception ignore){}
        } else if (max instanceof Number) r.maxMultiplicity = ((Number)max).intValue();

        Object wr = r.props.get("writable");
        if (wr instanceof Boolean) r.writable = (Boolean) wr;

        Object propsRaw = r.props.get("properties");
        if (propsRaw instanceof List<?>) {
            for (Object o : (List<?>) propsRaw) {
                if (o instanceof ASTProperty ap) {
                    resolveProperty(ap);
                    r.properties.add(ap);
                } else if (o instanceof java.util.Map<?,?> m) {
                    ASTProperty ap2 = new ASTProperty();
                    Object nm = m.get("name");
                    if (nm instanceof String) ap2.name = (String) nm;
                    ap2.props.putAll((Map<? extends String, ?>) m);
                    resolveProperty(ap2);
                    r.properties.add(ap2);
                }
            }
        }
    }

    void resolveComponent(ASTComponent c) {
        c.name = (String) c.props.get("name");
        resolveGeneralInfo(c);
        Object raw = c.props.get("schema");
        if (raw instanceof String) c.schemaInterface = (String) raw;
        else if (raw instanceof ASTSchema) {
            // components typically reference other interfaces by DTMI string; handle defensively
            c.schemaInterface = raw.toString();
        } else if (raw instanceof java.util.Map<?,?> m) {
            Object dtmi = m.get("dtmi");
            if (dtmi instanceof String) c.schemaInterface = (String) dtmi;
            else if (m.get("@id") instanceof String) c.schemaInterface = (String) m.get("@id");
        }
    }

    ASTSchema resolveSchema(ASTSchema s) {
        if (s == null) return null;

        // If schema has an @id and an existing canonical schema is present, return it
        Object idRaw = s.props.get("@id");
        if (idRaw instanceof String id) {
            ASTSchema existing = schemaIndex.get(id);
            if (existing != null && existing != s) {
                // ensure basic info on existing is populated
                resolveGeneralInfo(existing);
                return existing;
            }
            // register this schema into index if not present
            schemaIndex.putIfAbsent(id, s);
        }

        // populate general info for this schema
        resolveGeneralInfo(s);

        if (s instanceof ASTEnum e) {
            Object vs = e.props.get("valueSchema");
            if (vs instanceof String) {
                try {
                    e.valueSchema = (ASTPrimitiveSchema) resolveSchemaRef((String)vs);
                } catch (Exception ex) { /* ignore */ }
            } else if (vs instanceof ASTSchema as) {
                as = resolveSchema(as);
                if (as instanceof ASTPrimitiveSchema) e.valueSchema = (ASTPrimitiveSchema) as;
            }

            Object evs = e.props.get("enumValues");
            List<ASTEnumValue> out = new ArrayList<>();
            if (evs instanceof List<?>) {
                for (Object item : (List<?>) evs) {
                    if (item instanceof ASTEnumValue ev) {
                        out.add(ev);
                    } else if (item instanceof java.util.Map<?,?> m) {
                        ASTEnumValue nev = new ASTEnumValue();
                        Object name = m.get("name");
                        if (name instanceof String) nev.name = (String) name;
                        Object val = m.get("enumValue");
                        if (val == null) val = m.get("value");
                        if (val instanceof String) nev.value = new ASTEnumLiteral((String)val);
                        else if (val instanceof Number) nev.value = new ASTEnumLiteral(((Number)val).intValue());
                        out.add(nev);
                    }
                }
            }
            e.enumValues = out;
        }
        else if (s instanceof ASTObject o) {
            List<ASTField> out = new ArrayList<>();
            Object rawFields = o.props.get("fields");
            if (rawFields instanceof List<?>) {
                for (Object item : (List<?>) rawFields) {
                    if (item instanceof ASTField af) {
                        if (af.schema != null) af.schema = resolveSchema(af.schema);
                        out.add(af);
                    } else if (item instanceof java.util.Map<?,?> m) {
                        ASTField nf = new ASTField();
                        Object nm = m.get("name");
                        if (nm instanceof String) nf.name = (String) nm;

                        nf.schema = resolveSchemaFromProps(m.get("schema"));
                        out.add(nf);
                    }
                }
            }
            o.fields = out;
        }
        else if (s instanceof ASTMap m) {
            Object rawKey = s.props.get("mapKey");
            Object rawValue = s.props.get("mapValue");

            if (rawKey instanceof ASTMapKey mk) {
                if (mk.schema != null) mk.schema = resolveSchema(mk.schema);
                m.mapKey = mk;
            } else if (rawKey instanceof java.util.Map<?,?> mm) {
                ASTMapKey mk = new ASTMapKey();
                Object nm = mm.get("name");
                if (nm instanceof String) mk.name = (String) nm;
                Object sk = mm.get("schema");
                if (sk instanceof String) mk.schema = resolveSchemaRef((String) sk);
                else if (sk instanceof ASTSchema) {
                    mk.schema = (ASTSchema) sk;
                    mk.schema = resolveSchema(mk.schema);
                }
                m.mapKey = mk;
            }

            if (rawValue instanceof ASTMapValue mv) {
                if (mv.schema != null) mv.schema = resolveSchema(mv.schema);
                m.mapValue = mv;
            } else if (rawValue instanceof java.util.Map<?,?> mmv) {
                ASTMapValue mv = new ASTMapValue();
                Object nm = mmv.get("name");
                if (nm instanceof String) mv.name = (String) nm;

                mv.schema = resolveSchemaFromProps(mmv.get("schema"));

                m.mapValue = mv;
            }
        }
        else if (s instanceof ASTArray a) {
            a.elementSchema = resolveSchemaFromProps(s.props.get("elementSchema"));
        }

        return s;
    }

    private ASTSchema resolveSchemaFromProps(Object rawSchema) {
        if (rawSchema instanceof String s) {
            return resolveSchemaRef(s);
        }

        if (rawSchema instanceof ASTSchema as) {
            return resolveSchema(as);
        }

        if (rawSchema instanceof java.util.Map) {
            Map<?,?> m = (Map<?,?>) rawSchema;
            Object t = m.get("@type");
            if (t instanceof String) {
                ASTSchema as2 = switch ((String) t) {
                    case "Enum" -> new ASTEnum();
                    case "Object" -> new ASTObject();
                    case "Map" -> new ASTMap();
                    case "Array" -> new ASTArray();
                    default -> null;
                };
                if (as2 != null) {
                    as2.props.putAll((Map<? extends String, ?>) m);
                    return resolveSchema(as2);
                }
            }
        }

        return null;
    }

    void resolveGeneralInfo(ASTContent n) {
        Object idv = n.props.get("@id");
        n.id = idv instanceof String ? (String) idv : null;

        // @type may be a String or a List<String> (from grammar).
        Object tv = n.props.get("@type");
        if (tv instanceof String) {
            n.type = (String) tv;
        } else if (tv instanceof java.util.List<?>) {
            // pick first string element if available
            String picked = null;
            for (Object it : (java.util.List<?>) tv) {
                if (it instanceof String) { picked = (String) it; break; }
            }
            n.type = picked;
        } else {
            n.type = null;
        }

        Object cv = n.props.get("comment");
        n.comment = cv instanceof String ? (String) cv : null;

        Object dv = n.props.get("description");
        n.description = dv instanceof String ? (String) dv : null;

        Object dn = n.props.get("displayName");
        n.displayName = dn instanceof String ? (String) dn : null;
    }

    void resolveGeneralInfo(ASTSchema n) {
        Object idv = n.props.get("@id");
        n.id = idv instanceof String ? (String) idv : null;

        // Schema @type could also be a list in some inputs — handle safely
        Object tv = n.props.get("@type");
        if (tv instanceof String) {
            n.type = (String) tv;
        } else if (tv instanceof java.util.List<?>) {
            String picked = null;
            for (Object it : (java.util.List<?>) tv) {
                if (it instanceof String) { picked = (String) it; break; }
            }
            n.type = picked;
        } else {
            n.type = null;
        }

        Object cv = n.props.get("comment");
        n.comment = cv instanceof String ? (String) cv : null;

        Object dv = n.props.get("description");
        n.description = dv instanceof String ? (String) dv : null;

        Object dn = n.props.get("displayName");
        n.displayName = dn instanceof String ? (String) dn : null;
    }

    public void validate(SemanticAnalyzer analyzer) {
        DTDLContext ctx = analyzer.getContext();

        // Basic checks — id/type
        Object idObj = this.props.get("@id");
        String id = idObj instanceof String ? (String) idObj : null;
        if (id == null || id.isBlank()) {
            ctx.report("Interface missing @id", null);
            return;
        }
        Object typeObj = this.props.get("@type");
        if (!"Interface".equals(typeObj)) {
            ctx.report("Interface @type must be \"Interface\"", id);
        }

        // validate duplicate content names inside of interface
        validateDuplicateContentNames(ctx, id);

        // ---- validate children (delegates) ----
        for (ASTProperty p : this.properties) p.validate(analyzer);
        for (ASTTelemetry t : this.telemetries) t.validate(analyzer);
        for (ASTCommand c : this.commands) c.validate(analyzer);
        for (ASTRelationship r : this.relationships) r.validate(analyzer);
        for (ASTComponent c : this.components) c.validate(analyzer);
    }

    private void validateDuplicateContentNames(DTDLContext ctx, String id) {
        validateDuplicates(ctx, id, "Property", this.properties, p -> p.name, p -> p.id);
        validateDuplicates(ctx, id, "Telemetry", this.telemetries, t -> t.name, t -> t.id);
        validateDuplicates(ctx, id, "Command", this.commands, c -> c.name, c -> c.id);
        validateDuplicates(ctx, id, "Relationship", this.relationships, r -> r.name, r -> r.id);
        validateDuplicates(ctx, id, "Component", this.components, c -> c.name, c -> c.id);
    }

    private <T> void validateDuplicates(
            DTDLContext ctx,
            String interfaceId,
            String kind,
            List<T> elements,
            java.util.function.Function<T, String> nameFn,
            java.util.function.Function<T, String> idFn
    ) {
        if (elements == null) return;

        java.util.Set<String> names = new java.util.HashSet<>();

        for (T e : elements) {
            if (e == null) continue;

            String name = nameFn.apply(e);
            String eid = idFn.apply(e);

            if (name == null || name.isBlank()) {
                ctx.report(kind + " with missing name in interface " + interfaceId, eid);
            } else if (!names.add(name)) {
                ctx.report("Duplicate " + kind.toLowerCase() + " name '" + name + "' in interface " + interfaceId, eid);
            }
        }
    }
}