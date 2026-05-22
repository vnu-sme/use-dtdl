package org.tzi.use.dtdl.mapping;

import org.tzi.use.dtdl.DTDLModel.*;
import org.tzi.use.dtdl.DTDLModel.Command.Command;
import org.tzi.use.dtdl.DTDLModel.Relationship.BidirectionalRelationship;
import org.tzi.use.dtdl.DTDLModel.Schema.*;
import org.tzi.use.dtdl.DTDLModel.Schema.Array.Array;
import org.tzi.use.dtdl.DTDLModel.Schema.Enum.EnumValue;
import org.tzi.use.dtdl.DTDLModel.Schema.Object.Field;
import org.tzi.use.dtdl.DTDLModel.Property.Property;
import org.tzi.use.dtdl.DTDLModel.Relationship.Relationship;
import org.tzi.use.dtdl.DTDLModel.Component.Component;
import org.tzi.use.dtdl.DTDLModel.Telemetry.Telemetry;
import org.tzi.use.dtdl.Main;
import org.tzi.use.dtdl.actions.DTDLPluginState;
import org.tzi.use.dtdl.ast.ASTBidirectionalRelationship;
import org.tzi.use.dtdl.semantic.DTDLModelRegistry;
import org.tzi.use.dtdl.util.Utils;
import org.tzi.use.parser.SemanticException;
import org.tzi.use.parser.Symtable;
import org.tzi.use.parser.ocl.OCLCompiler;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.api.UseApiException;
import org.tzi.use.uml.ocl.type.EnumType;
import org.tzi.use.uml.ocl.expr.VarDecl;
import org.tzi.use.uml.ocl.expr.VarDeclList;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.sql.SQLOutput;
import java.util.*;

import static org.tzi.use.dtdl.mapping.MapperHelper.*;
import static org.tzi.use.dtdl.mapping.MapperHelper.multiplicityToString;

public class DTDLToMModelMapper {
    private final DTDLModel dtdl;
    private final UseModelApi api;
    private final DTDLModelRegistry registry;
    private final Map<String,MClass> ifaceToClass = new LinkedHashMap<>();
    private final Map<Schema,String> schemaToTypeName = new IdentityHashMap<>();
    private final Map<String,EnumType> createdEnums = new LinkedHashMap<>();
    private final Map<String,String> schemaKeyToTypeName = new LinkedHashMap<>();
    private final Deque<String> schemaPath = new ArrayDeque<>();
    private final PrintWriter logWriter;

    public DTDLToMModelMapper(DTDLModel dtdl, UseModelApi api, PrintWriter logWriter, DTDLModelRegistry registry) {
        this.dtdl = dtdl;
        this.api = api;
        this.logWriter = logWriter;
        this.registry = registry;
    }

    public MModel map() throws UseApiException {
//        ensurePrimitiveTypes();
        createClassesForInterfaces();
        createAttributesAndAssociations();
        createGeneralizations();

        return api.getModel();
    }

    private void push(String s) { schemaPath.push(s); }
    private void pop() { schemaPath.pop(); }

    private String currentPath() {
        List<String> l = new ArrayList<>(schemaPath);
        Collections.reverse(l);
        return String.join("_", l);
    }

    /**
     * Returns the enclosing class name from the current schemaPath stack.
     * schemaPath is filled by callers (they push classname then field names), so
     * reversing the deque yields [class, field, ...]; we take the first element.
     */
    private String enclosingClassName() {
        List<String> l = new ArrayList<>(schemaPath);
        Collections.reverse(l);
        if (l.isEmpty()) return "Anon";
        // first element should be the containing class name if callers pushed it
        return sanitize(l.get(0));
    }

    /**
     * Compute a stable key string for a Schema so structurally-equal or named schemas
     * produce the same key even if parsed into different objects.
     */
    private String schemaKey(Schema s) {
        if (s == null) return "prim:String";
        // Named schema -> prefer name (local schema name or id if available)
        if (s.getClass().getSimpleName().equals("NamedSchema")) {
            try {
                NamedSchema ns = (NamedSchema) s;
                String id = ns.getId();
                if (id != null && !id.isEmpty()) return "named:" + id;
                String nm = ns.getName();
                if (nm != null && !nm.isEmpty()) return "named:" + nm;
            } catch (ClassCastException ignored) {}
        }

        // Primitive
        if (s instanceof PrimitiveType) {
            String tn = ((PrimitiveType) s).getTypeName();
            if (tn == null) tn = "string";
            return "prim:" + tn.toLowerCase(Locale.ROOT);
        }

        // Enum
        if (s instanceof org.tzi.use.dtdl.DTDLModel.Schema.Enum.Enum enm) {
            StringBuilder b = new StringBuilder();
            b.append("enum:");
            for (EnumValue v : enm.getValues()) {
                b.append(v == null ? "<null>" : v.getName()).append("|");
            }
            return b.toString();
        }

        // Object: structural key based on field names + recursive element keys
        if (s instanceof org.tzi.use.dtdl.DTDLModel.Schema.Object.Object obj) {
            StringBuilder b = new StringBuilder("object:");
            for (Field f : obj.getFields()) {
                b.append(f.getName()).append(":");
                Schema fs = f.getSchema();
                b.append(schemaKey(fs)).append(";");
            }
            return b.toString();
        }

        // Array
        if (s instanceof Array arr) {
            return "array:" + schemaKey(arr.getElementSchema());
        }

        // Map
        if (s instanceof org.tzi.use.dtdl.DTDLModel.Schema.Map.Map m) {
            String k = m.getMapKey() != null ? schemaKey(m.getMapKey().getSchema()) : "any";
            String v = m.getMapValue() != null ? schemaKey(m.getMapValue().getSchema()) : "any";
            return "map:" + k + "->" + v;
        }

        // Fallback
        return s.getClass().getSimpleName() + "@" + System.identityHashCode(s);
    }

    private void ensureDataTypeIfMissing(String name) throws UseApiException {
        if (api.getModel().getDataType(name) == null) {
            api.createDataType(name, false);
        }
    }

    private void createClassesForInterfaces() throws UseApiException {
        for (Interface iface : dtdl.getInterfaces().values()) {
            String cname = sanitize(Utils.getInterfaceDisplayName(iface));
            if (cname == null || cname.isEmpty()) cname = "Unnamed";

            // 1) Registry mapping (preferred)
            if (registry != null) {
                Optional<String> mapped = registry.getClassNameForDtmi(iface.getId());
                if (mapped.isPresent()) {
                    MClass cls = api.getModel().getClass(mapped.get());
                    if (cls != null) {
                        ifaceToClass.put(iface.getId(), cls);
                        if (logWriter != null) logWriter.println("[DTDL->M] reused class from registry: " + cls.name() + " for " + iface.getId());
                        continue;
                    } else {
                        if (logWriter != null) logWriter.println("[DTDL->M] registry mapping stale for " + iface.getId() + " -> " + mapped.get() + " (class not found); clearing mapping");
                    }
                }
            }

            // 2) If a class with the sanitized displayName exists -> reuse
            MClass cls = api.getModel().getClass(cname);
            if (cls != null) {
                ifaceToClass.put(iface.getId(), cls);
                if (registry != null) registry.registerClassMapping(iface.getId(), cls.name());
                if (logWriter != null) logWriter.println("[DTDL->M] reused existing class: " + cls.name() + " for " + iface.getId());
                continue;
            }

            // 3) Create deterministic fallback name (displayName + hash of dtmi for uniqueness)
//            String fallback = cname + "_" + stableHash(iface.getId(), iface);
//            if (api.getModel().getClass(fallback) == null) {
//                api.createClass(fallback, false);
//            }
            String chosenName = cname;
            if (api.getModel().getClass(chosenName) == null) {
                api.createClass(chosenName, false);
            }

//            MClass chosen = api.getModel().getClass(fallback);
            MClass chosen = api.getModel().getClass(chosenName);
            ifaceToClass.put(iface.getId(), chosen);
            if (registry != null) registry.registerClassMapping(iface.getId(), chosen.name());
            if (logWriter != null) logWriter.println("[DTDL->M] created class: " + chosen.name() + " for " + iface.getId());
        }
    }

    private void createAttributesAndAssociations() throws UseApiException {
        for (Interface iface : dtdl.getInterfaces().values()) {
            MClass cls = ifaceToClass.get(iface.getId());
            if (cls == null) continue;
            for (ContentElement ce : iface.getContents()) {
                if (ce instanceof Telemetry tel) {
                    // create a telemetry attribute on the class, prefix it to mark as telemetry-only
                    String telName = tel.getName();
                    if (telName != null && !telName.isEmpty()) {
                        // className = room, telemetry = temperature ==> push to create name for schema inside telemetry
                        push(cls.name());
                        push(telName);
                        String tName = mapSchemaToTypeName(tel.getSchema());
                        pop();
                        pop();

                        // build safe attribute name with prefix and sanitize underlying name
                        String attrName = Utils.TELEMETRY_ATTR_PREFIX + sanitize(telName);

                        createSingleAttribute(cls, attrName, tName);
                    }
                } else if (ce instanceof Property p) {
                    if (p.getName() == null) continue;

                    // same thing as telemetry above
                    push(cls.name());
                    push(p.getName());
                    String typeName = mapSchemaToTypeName(p.getSchema());
                    pop();
                    pop();

                    createSingleAttribute(cls, p.getName(), typeName);
                } else if (ce instanceof Relationship r && !(ce instanceof BidirectionalRelationship)) {
                    Interface tgt = r.getTarget();
                    MClass targetCls = ifaceToClass.get(tgt.getId());

                    // fallback to registry
                    if (targetCls == null && registry != null) {
                        Optional<String> mapped = registry.getClassNameForDtmi(tgt.getId());
                        if (mapped.isPresent()) {
                            targetCls = api.getModel().getClass(mapped.get());
                            if (targetCls != null) {
                                ifaceToClass.put(tgt.getId(), targetCls);
                                if (logWriter != null) logWriter.println("[DTDL->M] resolved relationship target to registry class: " + targetCls.name() + " for dtmi " + tgt.getId());
                            }
                        }
                    }

                    if (targetCls == null) {
                        System.out.println("ASSOCIATION ERROR: target class not found for " + tgt.getId() + " for " + r.getName());
                        continue;
                    }

                    String relName = sanitize(nonNull(r.getName(), "rel"));

                    String srcName = sanitize(Utils.getInterfaceDisplayName(iface));
                    String tgtName = sanitize(Utils.getInterfaceDisplayName(tgt));

                    String assocName = buildAssociationName(api.getModel(), srcName, tgtName, relName, r, ce);

                    String[] classNames = new String[] {
                            cls.name(),
                            targetCls.name()
                    };

                    String[] roleNames = computeRoleNames(cls, targetCls, srcName, tgtName, relName, r);

                    String leftMult = multiplicityToString(r.getMinMultiplicity(), r.getMaxMultiplicity());
                    String rightMult = "0..*";

                    String[] multiplicities = new String[] { leftMult, rightMult };
                    int[] aggr = new int[] { MAggregationKind.NONE, MAggregationKind.NONE };
                    boolean[] ordered = new boolean[] { false, false };

                    // If no properties, creates normal association, else creates association class
                    boolean hasProperties = r.getProperties() != null && !r.getProperties().isEmpty();

                    if (hasProperties) {
                        if (api.getModel().getAssociationClass(assocName) == null) {
                            api.createAssociationClass(assocName, false, new String[0], classNames, roleNames, multiplicities, aggr, ordered, new String[0][][]);
                            if (logWriter != null) logWriter.println("[DTDL->M] created association class: " + assocName +
                                    " classes=" + Arrays.toString(classNames) + " roles=" + Arrays.toString(roleNames));
                        }

                        MAssociationClass ac = api.getAssociationClass(assocName);
                        if (ac != null && !r.getProperties().isEmpty()) {
                            List<String> propNames = new ArrayList<>();
                            List<String> propTypes = new ArrayList<>();
                            for (Property rp : r.getProperties()) {
                                if (rp.getName() == null) continue;
                                boolean propExists = false;
                                for (MAttribute a : ac.allAttributes()) {
                                    if (a.name().equals(rp.getName())) { propExists = true; break; }
                                }

                                if (!propExists) {
                                    push(cls.name());
                                    push(r.getName());
                                    push(rp.getName());
                                    String ptype = mapSchemaToTypeName(rp.getSchema());
                                    pop();
                                    pop();
                                    pop();
                                    api.createAttributeEx(ac, rp.getName(), api.getType(ptype));
                                }
                            }
                        }
                    } else {
                        if (api.getModel().getAssociation(assocName) == null &&
                                api.getModel().getAssociationClass(assocName) == null) {
                            api.createAssociation(assocName, classNames, roleNames, multiplicities, aggr, ordered, new String[0][][]);
                            if (logWriter != null) logWriter.println("[DTDL->M] created association: " + assocName +
                                    " classes=" + Arrays.toString(classNames) + " roles=" + Arrays.toString(roleNames));
                        }
                    }
                } else if (ce instanceof BidirectionalRelationship r) {
                    Interface tgt = r.getTarget();
                    MClass targetCls = ifaceToClass.get(tgt.getId());

                    // fallback to registry
                    if (targetCls == null && registry != null) {
                        Optional<String> mapped = registry.getClassNameForDtmi(tgt.getId());
                        if (mapped.isPresent()) {
                            targetCls = api.getModel().getClass(mapped.get());
                            if (targetCls != null) {
                                ifaceToClass.put(tgt.getId(), targetCls);
                                if (logWriter != null) logWriter.println("[DTDL->M] resolved relationship target to registry class: " + targetCls.name() + " for dtmi " + tgt.getId());
                            }
                        }
                    }

                    if (targetCls == null) {
                        System.out.println("ASSOCIATION ERROR: target class not found for " + tgt.getId() + " for " + r.getName());
                        continue;
                    }

                    String relName = sanitize(nonNull(r.getName(), "rel"));

                    String srcName = sanitize(Utils.getInterfaceDisplayName(iface));
                    String tgtName = sanitize(Utils.getInterfaceDisplayName(tgt));

                    String assocName = buildAssociationName(api.getModel(), srcName, tgtName, relName, r, ce);

                    String[] classNames = new String[] {
                            cls.name(),
                            targetCls.name()
                    };

                    String[] roleNames =  computeBidirectionalRoleNames(cls, targetCls, r.getName(), r.targetName);

                    String leftMult = multiplicityToString(r.getMinMultiplicity(), r.getMaxMultiplicity());
                    String rightMult = multiplicityToString(r.targetMinMultiplicity, r.targetMaxMultiplicity);

                    String[] multiplicities = new String[] { leftMult, rightMult };
                    int[] aggr = new int[] { MAggregationKind.NONE, MAggregationKind.NONE };
                    boolean[] ordered = new boolean[] { false, false };

                    if (api.getModel().getAssociation(assocName) == null &&
                            api.getModel().getAssociationClass(assocName) == null) {
                        api.createAssociation(assocName, classNames, roleNames, multiplicities, aggr, ordered, new String[0][][]);
                        if (logWriter != null) logWriter.println("[DTDL->M] created association: " + assocName +
                                " classes=" + Arrays.toString(classNames) + " roles=" + Arrays.toString(roleNames));
                    } else {
                        if (logWriter != null) logWriter.println("[DTDL->M] association already exists: " + assocName);
                    }
                } else if (ce instanceof Component c) {
                    if (c.getSchemaInterface() == null) continue;
                    MClass compCls = ifaceToClass.get(c.getSchemaInterface().getId());
                    if (compCls == null) {
                        if (registry != null) {
                            Optional<String> mapped = registry.getClassNameForDtmi(c.getSchemaInterface().getId());
                            if (mapped.isPresent()) compCls = api.getModel().getClass(mapped.get());
                        }
                        if (compCls == null) continue;
                        ifaceToClass.put(c.getSchemaInterface().getId(), compCls);
                    }

                    String srcDisplay = sanitize(Utils.getInterfaceDisplayName(iface));
                    if (srcDisplay == null || srcDisplay.isEmpty()) srcDisplay = sanitize(iface.getDisplayName());
                    String compDisplay = sanitize(nonNull(c.getName(), compCls.nameAsRolename()));

                    String assocName = sanitize(srcDisplay + "_comp_" + compDisplay);
                    if (api.getModel().getAssociation(assocName) != null) assocName = assocName + "_" + stableHash(compDisplay, ce);

                    String[] classNames = new String[] { ifaceToClass.get(iface.getId()).name(), compCls.name() };

                    String leftRole = srcDisplay + "_" + compDisplay;
                    String rightRole = compDisplay + "Of" + srcDisplay;
                    String[] roleNames = new String[] { leftRole, rightRole };

                    String[] multiplicities = new String[] { "1", "0..*" };
                    int[] aggr = new int[] { MAggregationKind.COMPOSITION, MAggregationKind.NONE };
                    boolean[] ordered = new boolean[] { false, false };

                    if (!associationExists(classNames[0], classNames[1], roleNames[0], roleNames[1])) {
                        api.createAssociation(assocName, classNames, roleNames, multiplicities, aggr, ordered, new String[0][][]);
                        if (logWriter != null) logWriter.println("[DTDL->M] created component association: " + assocName + " roles=" + Arrays.toString(roleNames));
                    } else {
                        if (logWriter != null) logWriter.println("[DTDL->M] skipped component association (exists): " + assocName);
                    }
                } else {
                    // skip other content elements
                }
            }
        }

        // commands/operations
        for (Interface iface : dtdl.getInterfaces().values()) {
            MClass cls = ifaceToClass.get(iface.getId());
            if (cls == null) continue;
            for (ContentElement ce : iface.getContents()) {
                if (ce instanceof Command cmd) {
                    VarDeclList varDecls = new VarDeclList(false);
                    List<String> paramNames = new ArrayList<>();

                    if (cmd.getRequest() != null) {
                        Schema s = cmd.getRequest().getSchema();
                        push(cls.name());
                        push(cmd.getName());
                        String tname = mapSchemaToTypeName(s);
                        pop();
                        pop();
                        Type t = api.getType(tname);
                        String pname = cmd.getRequest().getName() == null || cmd.getRequest().getName().isBlank()
                                ? "arg"
                                : cmd.getRequest().getName();

                        varDecls.add(new VarDecl(pname, t));
                        paramNames.add(pname);
                    }
                    boolean opExists = false;
                    for (MOperation mop : cls.operations()) {
                        if (Objects.equals(mop.name(), cmd.getName())) { opExists = true; break; }
                    }
                    if (!opExists) {
                        MOperation op = new MOperation(cmd.getName(), varDecls, null, false);
                        try { cls.addOperation(op); } catch (Exception ex) { throw new UseApiException("Failed to add operation", ex); }
                    }

                    // For rule-based operation executions
                    DTDLPluginState.operationCatalog().register(cls.name(), cmd.getName(), paramNames);
                }
            }
        }
    }

    private void createSingleAttribute(MClass cls, String attrName, String typeName) throws UseApiException {
        // check existed attr that has same name
        boolean exists = false;
        for (MAttribute a : cls.allAttributes()) {
            if (a.name().equals(attrName)) { exists = true; break; }
        }
        if (!exists) {
            api.createAttributeEx(cls, attrName, api.getType(typeName));
            if (logWriter != null) logWriter.println("[DTDL->M] added telemetry attribute " + attrName + " : " + typeName + " to class " + cls.name());
        } else {
            if (logWriter != null) logWriter.println("[DTDL->M] skipped telemetry attribute (exists) " + attrName + " on class " + cls.name());
        }
    }

    private String mapSchemaToTypeName(Schema s) throws UseApiException {
        // compute stable key and consult string-key cache
        String skey = schemaKey(s);
        if (skey != null && schemaKeyToTypeName.containsKey(skey)) {
            String cached = schemaKeyToTypeName.get(skey);
            // keep identity cache in sync for existing callers that use Schema instance keys
            schemaToTypeName.put(s, cached);
            return cached;
        }

        if (s == null) return "String";
        if (schemaToTypeName.containsKey(s)) return schemaToTypeName.get(s);
        if (s instanceof PrimitiveType) {
            String tn = ((PrimitiveType) s).getTypeName();
            if (tn == null) tn = "String";
            tn = tn.toLowerCase(Locale.ROOT);
            if (tn.contains("int") || tn.equals("integer") || tn.equals("long")) {
                schemaToTypeName.put(s,"Integer");
                schemaKeyToTypeName.put(skey, "Integer");
                return "Integer";
            }
            if (tn.contains("float") || tn.contains("double") || tn.equals("number")) {
                schemaToTypeName.put(s,"Real");
                schemaKeyToTypeName.put(skey, "Real");
                return "Real";
            }
            if (tn.contains("bool")) {
                schemaToTypeName.put(s,"Boolean");
                schemaKeyToTypeName.put(skey, "Boolean");
                return "Boolean";
            }
            schemaToTypeName.put(s,"String");
            schemaKeyToTypeName.put(skey, "String");
            return "String";
        }
        if (s instanceof org.tzi.use.dtdl.DTDLModel.Schema.Object.Object obj) {
            // Prefer already-registered named schema mapping if present
            String dtName;
            if (schemaToTypeName.containsKey(s)) {
                dtName = schemaToTypeName.get(s);
            } else {
                String base = enclosingClassName();
                dtName = sanitize(base + "_Object");

                 int shortHash = Math.abs(schemaKey(s).hashCode()) % 10000;
                 dtName = sanitize(base + "_" + shortHash + "_Object");

                // remember mapping so repeated occurrences reuse same type name
                schemaToTypeName.put(s, dtName);
                schemaKeyToTypeName.put(skey, dtName);
            }

            if (api.getModel().getDataType(dtName) == null) api.createDataType(dtName, false);

            MDataType dt = api.getModel().getDataType(dtName);
            if (dt == null) throw new UseApiException("Could not obtain created data type " + dtName);

            // Use the data type name as the local base path while processing fields so nested enums
            // and inner datatypes are named under the named schema instead of under the property path.
            List<Field> fields = obj.getFields();

            // save & restore schemaPath so we don't permanently lose outer context
            Deque<String> savedPath = new ArrayDeque<>(schemaPath);
            schemaPath.clear();
            push(dtName);

            // forcing nested type to be created first and populating caches (schemaToTypeName, schemaKeyToTypeName)
            for (Field f : fields) {
                push(f.getName());
                mapSchemaToTypeName(f.getSchema());
                pop();
            }

            try {
                // create backing attributes for constructor parameters FIRST
                for (Field field : fields) {
                    String fname = field.getName();
                    if (fname == null) continue;
                    String fitName = mapSchemaToTypeName(field.getSchema());
                    Type ftType = api.getType(fitName);
                    boolean attrExists = dt.attributes().stream().anyMatch(a -> a.name().equals(fname));
                    if (!attrExists) {
                        try {
                            addAttributeToDataType(dt, fname, ftType);
                            if (logWriter != null) logWriter.println("[DTDL->M] added backing attribute '" + fname + "' : " + fitName + " to dataType " + dt.name());
                        } catch (UseApiException uae) {
                            if (logWriter != null) logWriter.println("[DTDL->M] failed to add backing attribute '" + fname + "': " + uae.getMessage());
                        }
                    } else {
                        if (logWriter != null) logWriter.println("[DTDL->M] backing attribute '" + fname + "' already exists on dataType " + dt.name());
                    }
                }

                // Now synthesize constructor parameters from dt.allAttributes() so names/order match invariants
                VarDeclList ctorParams = new VarDeclList(false);
                List<MAttribute> allAttrs = dt.allAttributes();
                if (logWriter != null) {
                    logWriter.println(">>> Constructor will use attribute order:");
                    for (MAttribute a : allAttrs) logWriter.println("    - " + a.name());
                }
                for (MAttribute a : allAttrs) {
                    Type t = a.type();
                    ctorParams.add(new VarDecl(a.name(), t));
                }

                MOperation ctor = new MOperation(dt.name(), ctorParams, null, true);
                try {
                    dt.addOperation(ctor);
                    if (logWriter != null) logWriter.println("[DTDL->M] added constructor op " + ctor.name() + "(...) to dataType " + dt.name());
                } catch (MInvalidModelException ex) {
                    if (logWriter != null) logWriter.println("[DTDL->M] constructor already exists or invalid: " + ex.getMessage());
                    // If constructor exists but is invalid, we leave it alone to avoid breaking model invariants.
                }

                // create getters and compile OCL expressions (self.<field>)
                for (Field field : fields) {
                    String fname = field.getName();
                    if (fname == null) continue;

                    String fitName = mapSchemaToTypeName(field.getSchema());
                    Type ftType = api.getType(fitName);

                    String getterName = "get" + Character.toUpperCase(fname.charAt(0)) + fname.substring(1);
                    VarDeclList noParams = new VarDeclList(false);
                    MOperation getter = new MOperation(getterName, noParams, ftType, false);

                    try {
                        dt.addOperation(getter);
                    } catch (MInvalidModelException ex) {
                        for (MOperation mop : dt.operations()) {
                            if (Objects.equals(mop.name(), getterName)) {
                                getter = mop;
                                break;
                            }
                        }
                    }

                    // build operation body
                    try {
                        StringWriter errBuffer = new StringWriter();
                        PrintWriter errorPrinter = new PrintWriter(errBuffer, true);
                        Symtable symTable = new Symtable();
                        try {
                            symTable.add("self", dt, null);
                            if (logWriter != null) logWriter.println("[DEBUG] Added 'self' to symtable with type: " + dt.name());
                        } catch (SemanticException se) {
                            if (logWriter != null)
                                logWriter.println("[DEBUG] Failed adding self to symtable: " + se.getMessage());
                        }

                        String body = "self." + fname;

                        org.tzi.use.uml.ocl.expr.Expression bodyExp = OCLCompiler.compileExpression(api.getModel(), body,
                                "DTDLGetter", errorPrinter, symTable);

                        if (bodyExp == null) {
                            if (logWriter != null) logWriter.println("[DEBUG] Compiler errors: " + errBuffer);
                        } else {
                            if (logWriter != null) logWriter.println("[DEBUG] Expression type: " + bodyExp.type());

                            try {
                                getter.setExpression(bodyExp);

                                // verify expression is attached
                                if (getter.expression() != null) {
                                    logWriter.println("[DEBUG] Getter now has expression attached.");
                                } else {
                                    logWriter.println("[DEBUG] Getter expression STILL NULL after setExpression.");
                                }

                            } catch (Exception e) {
                                if (logWriter != null) {
                                    logWriter.println("[DEBUG] setExpression FAILED: " + e.getMessage());
                                    e.printStackTrace(logWriter);
                                }
                            }
                        }

                    } catch (Throwable ex) {
                        if (logWriter != null) {
                            logWriter.println("[DEBUG] Unexpected error compiling getter expression:");
                            ex.printStackTrace(logWriter);
                        }
                    }

                    // dump operations after each getter
                    if (logWriter != null) {
                        logWriter.println("[DEBUG] Current operations in DT " + dt.name() + ":");
                        for (MOperation mop : dt.operations()) {
                            logWriter.println("   - " + mop.name() + " | hasExpr=" + (mop.expression() != null));
                        }
                    }
                }
            } catch (Exception ex) {
                throw new UseApiException("Failed to create constructor/getters for data type " + dt.name(), ex);
            } finally {
                // restore previous schemaPath
                schemaPath.clear();
                schemaPath.addAll(savedPath);
            }

            return dtName;
        }

        if (s instanceof Array) {
            // for arrays map to an OCL Sequence type expression using the element type
            Schema elem = ((Array) s).getElementSchema();
            String etn = mapSchemaToTypeName(elem); // ensure element type/dataType is created first
            // Use parentheses syntax which USE/Type parser accepts.
            String seqName = "Sequence(" + etn + ")";

            schemaToTypeName.put(s, seqName);
            schemaKeyToTypeName.put(skey, seqName);
            return seqName;
        }

        if (s instanceof org.tzi.use.dtdl.DTDLModel.Schema.Enum.Enum e) {
            String path = currentPath();
            String enumName;
            if (path == null || path.isEmpty()) {
                enumName = sanitize(enclosingClassName() + "_Enum");
            } else {
                enumName = sanitize(path + "_Enum");
            }
            schemaToTypeName.put(s, enumName);
            schemaKeyToTypeName.put(skey, enumName);

            if (api.getModel().getDataType(enumName) == null) {
                List<String> lits = new ArrayList<>();
                for (EnumValue v : e.getValues()) {
                    if (v.getValue() != null) {
                        lits.add(String.valueOf(v.getValue().raw()));
                    } else if (v.getName() != null && !v.getName().isEmpty()) {
                        lits.add(v.getName());
                    }
                }
                EnumType et = api.createEnumeration(enumName, lits);
                createdEnums.put(enumName, et);
            }

            return enumName;
        }
        if (s instanceof NamedSchema ns) {
            String name;
            if (ns.getName() != null && !ns.getName().isEmpty()) {
                name = sanitize(ns.getName());
            } else {
                // fallback to enclosing class name based short name
                name = sanitize(enclosingClassName() + "_Named");
            }
            schemaToTypeName.put(s, name);
            schemaKeyToTypeName.put(skey, name);

            if (api.getModel().getDataType(name) == null) api.createDataType(name, false);
            return name;
        }
        if (s instanceof org.tzi.use.dtdl.DTDLModel.Schema.Map.Map m) {
            Schema k = m.getMapKey() != null ? m.getMapKey().getSchema() : null;
            Schema v = m.getMapValue() != null ? m.getMapValue().getSchema() : null;

            // create an entry datatype for map entries
            String path = currentPath();
            String entryDtName;
            if (path == null || path.isEmpty()) {
                entryDtName = sanitize(enclosingClassName() + "_MapEntry");
            } else {
                entryDtName = sanitize(path + "_MapEntry");
            }
            if (entryDtName == null || entryDtName.isEmpty()) entryDtName = "MapEntry";

            if (api.getModel().getDataType(entryDtName) == null) {
                api.createDataType(entryDtName, false);
            }
            MDataType entryDt = api.getModel().getDataType(entryDtName);
            if (entryDt == null) throw new UseApiException("Could not obtain created data type " + entryDtName);

            if (k != null) {
                String keyTypeName = mapSchemaToTypeName(k);
                Type keyType = api.getType(keyTypeName);
                boolean exists = entryDt.attributes().stream().anyMatch(a -> "key".equals(a.name()));
                if (!exists) addAttributeToDataType(entryDt, "key", keyType);
            }

            if (v != null) {
                String valueTypeName = mapSchemaToTypeName(v);
                Type valueType = api.getType(valueTypeName);
                boolean exists = entryDt.attributes().stream().anyMatch(a -> "value".equals(a.name()));
                if (!exists) addAttributeToDataType(entryDt, "value", valueType);
            }

            // represent map as a sequence of entry datatypes
            String seqName = "Sequence(" + entryDtName + ")";
            schemaToTypeName.put(s, seqName);
            schemaKeyToTypeName.put(skey, seqName);
            return seqName;
        }

        schemaToTypeName.put(s, "String");
        schemaKeyToTypeName.put(skey, "String");
        return "String";
    }

    private String schemaTypeLabel(Schema s) {
        if (s == null) return "String";
        if (s instanceof PrimitiveType) return ((PrimitiveType)s).getTypeName();
        if (s instanceof Array) return "Sequence";
        if (s instanceof org.tzi.use.dtdl.DTDLModel.Schema.Object.Object) return "Object";
        return "String";
    }

    private void createGeneralizations() throws UseApiException {
        for (Interface iface : dtdl.getInterfaces().values()) {
            MClass child = ifaceToClass.get(iface.getId());
            if (child == null) continue;

            for (Interface parentIface : iface.getExtends()) {
                if (parentIface == null) continue;

                MClass parent = ifaceToClass.get(parentIface.getId());

                // Try registry mapping if not created in this import
                if (parent == null && registry != null) {
                    Optional<String> mapped = registry.getClassNameForDtmi(parentIface.getId());
                    if (mapped.isPresent()) {
                        String mappedName = mapped.get();
                        parent = api.getModel().getClass(mappedName);
                        if (parent != null) {
                            ifaceToClass.put(parentIface.getId(), parent);
                        }
                    }
                }

                if (parent == null) {
                    if (logWriter != null)
                        System.out.println("[DTDL->M] Could not resolve parent for extends: " + parentIface.getId());
                    continue;
                }

                api.createGeneralization(child.name(), parent.name());

                if (logWriter != null)
                    logWriter.println("[DTDL->M] created generalization: " + child.name() + " -> " + parent.name());
            }
        }
    }

    private void addAttributeToDataType(MDataType dt, String attrName, Type attrType) throws UseApiException {
        if (dt == null) throw new UseApiException("Target MDataType is null when adding attribute " + attrName);
        if (attrName == null || attrName.isEmpty()) throw new UseApiException("Attribute name required");
        if (attrType == null) {
            try { attrType = api.getType("String"); } catch (UseApiException ignored) { }
        }

        try {
            Constructor<MAttribute> ctor = MAttribute.class.getDeclaredConstructor(String.class, Type.class);
            ctor.setAccessible(true);
            MAttribute attr = ctor.newInstance(attrName, attrType);
            dt.addAttribute(attr);
        } catch (Exception ex) {
            throw new UseApiException("Failed to add attribute '" + attrName + "' to datatype '" + dt.name() + "'", ex);
        }
    }

    private boolean associationExists(String clsA, String clsB, String roleA, String roleB) {
        for (MAssociation a : api.getModel().associations()) {
            List<MAssociationEnd> ends = a.associationEnds();
            if (ends.size() != 2) continue;

            MAssociationEnd e1 = ends.get(0);
            MAssociationEnd e2 = ends.get(1);

            boolean match =
                    Objects.equals(e1.cls().name(), clsA) &&
                            Objects.equals(e2.cls().name(), clsB) &&
                            Objects.equals(e1.name(), roleA) &&
                            Objects.equals(e2.name(), roleB);

            boolean reverse =
                    Objects.equals(e1.cls().name(), clsB) &&
                            Objects.equals(e2.cls().name(), clsA) &&
                            Objects.equals(e1.name(), roleB) &&
                            Objects.equals(e2.name(), roleA);

            if (match || reverse) {
                if (logWriter != null) logWriter.println("[DTDL->M] associationExists matched (match=" + match + ", reverse=" + reverse + "): " + a.name());
                return true;
            }
        }
        return false;
    }
}
