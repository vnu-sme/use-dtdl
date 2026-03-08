package org.tzi.use.dtdl.mapping;

import org.tzi.use.dtdl.DTDLModel.*;
import org.tzi.use.dtdl.DTDLModel.Schema.*;
import org.tzi.use.dtdl.DTDLModel.Schema.Array.Array;
import org.tzi.use.dtdl.DTDLModel.Schema.Enum.EnumValue;
import org.tzi.use.dtdl.DTDLModel.Schema.Object.Field;
import org.tzi.use.dtdl.DTDLModel.Property.Property;
import org.tzi.use.dtdl.DTDLModel.Relationship.Relationship;
import org.tzi.use.dtdl.DTDLModel.Component.Component;
import org.tzi.use.dtdl.DTDLModel.Telemetry.Telemetry;
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
import java.util.*;

public class DTDLToMModelMapper {
    private final DTDLModel dtdl;
    private final UseModelApi api;
    private final DTDLModelRegistry registry;
    private final Map<String,MClass> ifaceToClass = new LinkedHashMap<>();
    private final Map<Schema,String> schemaToTypeName = new IdentityHashMap<>();
    private final Map<String,EnumType> createdEnums = new LinkedHashMap<>();
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
        createDataTypesForNamedSchemas();
        createAttributesAndAssociations();
        createGeneralizations();
        registerEnumTypes();
        // no reflection-based annotation step needed when using registry mapping
        return api.getModel();
    }

    private void push(String s) { schemaPath.push(s); }
    private void pop() { schemaPath.pop(); }

    private String currentPath() {
        List<String> l = new ArrayList<>(schemaPath);
        Collections.reverse(l);
        return String.join("_", l);
    }

    private void ensurePrimitiveTypes() throws UseApiException {
        ensureDataTypeIfMissing("Integer");
        ensureDataTypeIfMissing("Real");
        ensureDataTypeIfMissing("String");
        ensureDataTypeIfMissing("Boolean");
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
            String fallback = cname + "_" + stableHash(iface.getId());
            if (api.getModel().getClass(fallback) == null) {
                api.createClass(fallback, false);
            }
            MClass chosen = api.getModel().getClass(fallback);
            ifaceToClass.put(iface.getId(), chosen);
            if (registry != null) registry.registerClassMapping(iface.getId(), chosen.name());
            if (logWriter != null) logWriter.println("[DTDL->M] created class: " + chosen.name() + " for " + iface.getId());
        }
    }

    private void createDataTypesForNamedSchemas() throws UseApiException {
        for (Interface iface : dtdl.getInterfaces().values()) {
            String ifaceDisplay = sanitize(Utils.getInterfaceDisplayName(iface));
            if (ifaceDisplay == null || ifaceDisplay.isEmpty()) ifaceDisplay = "NamedIface";
            for (Map.Entry<String, Schema> e : iface.getSchemas().entrySet()) {
                Schema s = e.getValue();
                if (s instanceof org.tzi.use.dtdl.DTDLModel.NamedSchema) {
                    // use interface display name + the local schema name
                    String dtName = sanitize(ifaceDisplay + "_" + e.getKey());
                    if (dtName == null || dtName.isEmpty()) dtName = "Named";
                    if (api.getModel().getDataType(dtName) == null) {
                        api.createDataType(dtName, false);
                    }
                    schemaToTypeName.put(s, dtName);
                    if (logWriter != null) logWriter.println("[DTDL->M] created dataType for named schema: " + dtName + " (iface=" + iface.getId() + ", schemaKey=" + e.getKey() + ")");
                }
            }
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
                        push(cls.name());
                        push(telName);
                        String tName = mapSchemaToTypeName(tel.getSchema());
                        pop();
                        pop();

                        // build safe attribute name with prefix and sanitize underlying name
                        String attrName = Utils.TELEMETRY_ATTR_PREFIX + sanitize(telName);

                        boolean exists = false;
                        for (MAttribute a : cls.allAttributes()) {
                            if (a.name().equals(attrName)) { exists = true; break; }
                        }
                        if (!exists) {
                            api.createAttributeEx(cls, attrName, api.getType(tName));
                            if (logWriter != null) logWriter.println("[DTDL->M] added telemetry attribute " + attrName + " : " + tName + " to class " + cls.name());
                        } else {
                            if (logWriter != null) logWriter.println("[DTDL->M] skipped telemetry attribute (exists) " + attrName + " on class " + cls.name());
                        }
                    }
                    // continue with next content element
                    continue;
                } else if (ce instanceof Property) {
                    Property p = (Property) ce;
                    if (p.getName() == null) continue;
                    push(ifaceToClass.get(iface.getId()).name());
                    push(p.getName());
                    String typeName = mapSchemaToTypeName(p.getSchema());
                    pop();
                    pop();
                    Type t = api.getType(typeName);

                    boolean attrExists = false;
                    for (MAttribute a : cls.allAttributes()) {
                        if (a.name().equals(p.getName())) { attrExists = true; break; }
                    }
                    if (!attrExists) {
                        api.createAttributeEx(cls, p.getName(), t);
                        if (logWriter != null) logWriter.println("[DTDL->M] added attribute " + p.getName() + " : " + typeName + " to class " + cls.name());
                    } else {
                        if (logWriter != null) logWriter.println("[DTDL->M] skipped attribute (exists) " + p.getName() + " on class " + cls.name());
                    }

                } else if (ce instanceof Relationship) {
                    Relationship r = (Relationship) ce;
                    Interface tgt = r.getTarget();
                    if (tgt == null) continue;
                    MClass targetCls = ifaceToClass.get(tgt.getId());

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
                        if (logWriter != null) logWriter.println("[DTDL->M] could not resolve relationship target for dtmi " + tgt.getId() + " — skipping association");
                        continue;
                    }

                    String relName = sanitize(nonNull(r.getName(), "rel"));

                    String srcName = sanitize(Utils.getInterfaceDisplayName(iface));
                    String tgtName = sanitize(Utils.getInterfaceDisplayName(tgt));

                    String assocBase = sanitize(srcName + "_" + relName + "_" + tgtName);
                    String assocName = assocBase;

                    while (api.getModel().getAssociation(assocName) != null ||
                            api.getModel().getAssociationClass(assocName) != null) {
                        assocName = assocBase + "_" + stableHash(nonNull(r.getId(), relName) + assocName);
                    }

                    String[] classNames = new String[] { cls.name(), targetCls.name() };

                    // deterministic role names (prevents collisions and supports self relationships)
                    String leftRole = relName + "From";
                    String rightRole = relName + "To";

                    String[] roleNames = new String[] { leftRole, rightRole };


                    String leftMult = multiplicityToString(r.getMinMultiplicity(), r.getMaxMultiplicity());
                    String rightMult = "0..*";
                    String[] multiplicities = new String[] { leftMult, rightMult };
                    int[] aggr = new int[] { MAggregationKind.NONE, MAggregationKind.NONE };
                    boolean[] ordered = new boolean[] { false, false };

                    if (api.getModel().getAssociationClass(assocName) == null) {
                        api.createAssociationClass(assocName, false, new String[0], classNames, roleNames, multiplicities, aggr, ordered, new String[0][][]);
                        if (logWriter != null) logWriter.println("[DTDL->M] created association class: " + assocName +
                                " classes=" + Arrays.toString(classNames) +
                                " roles=" + Arrays.toString(roleNames));
                    } else {
                        if (logWriter != null) logWriter.println("[DTDL->M] association class already exists: " + assocName);
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
                                if (logWriter != null) logWriter.println("[DTDL->M] added assoc-class property " + rp.getName() + " to " + assocName);
                            } else {
                                if (logWriter != null) logWriter.println("[DTDL->M] skipped assoc-class property (exists) " + rp.getName() + " on " + assocName);
                            }
                        }
                    }
                } else if (ce instanceof Component) {
                    Component c = (Component) ce;
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

                    String assocBase = sanitize(srcDisplay + "_comp_" + compDisplay);
                    String assocName = assocBase;
                    if (api.getModel().getAssociation(assocName) != null) assocName = assocName + "_" + stableHash(compDisplay);

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
                if (ce instanceof org.tzi.use.dtdl.DTDLModel.Command.Command) {
                    org.tzi.use.dtdl.DTDLModel.Command.Command cmd = (org.tzi.use.dtdl.DTDLModel.Command.Command) ce;
                    VarDeclList varDecls = new VarDeclList(false);
                    if (cmd.getRequest() != null) {
                        Schema s = cmd.getRequest().getSchema();
                        push(cls.name());
                        push(cmd.getName());
                        String tname = mapSchemaToTypeName(s);
                        pop();
                        pop();
                        Type t = api.getType(tname);
                        varDecls.add(new VarDecl(cmd.getRequest().getName() == null ? "arg" : cmd.getRequest().getName(), t));
                    }
                    boolean opExists = false;
                    for (MOperation mop : cls.operations()) {
                        if (Objects.equals(mop.name(), cmd.getName())) { opExists = true; break; }
                    }
                    if (!opExists) {
                        MOperation op = new MOperation(cmd.getName(), varDecls, null, false);
                        try { cls.addOperation(op); } catch (Exception ex) { throw new UseApiException("Failed to add operation", ex); }
                    }
                }
            }
        }
    }

    private String mapSchemaToTypeName(Schema s) throws UseApiException {
        if (s == null) return "String";
        if (schemaToTypeName.containsKey(s)) return schemaToTypeName.get(s);
        if (s instanceof PrimitiveType) {
            String tn = ((PrimitiveType) s).getTypeName();
            if (tn == null) tn = "String";
            tn = tn.toLowerCase(Locale.ROOT);
            if (tn.contains("int") || tn.equals("integer") || tn.equals("long")) { schemaToTypeName.put(s,"Integer"); return "Integer"; }
            if (tn.contains("float") || tn.contains("double") || tn.equals("number")) { schemaToTypeName.put(s,"Real"); return "Real"; }
            if (tn.contains("bool")) { schemaToTypeName.put(s,"Boolean"); return "Boolean"; }
            schemaToTypeName.put(s,"String"); return "String";
        }
        if (s instanceof org.tzi.use.dtdl.DTDLModel.Schema.Object.Object obj) {
            String dtName = sanitize(currentPath() + "_Object");

            if (api.getModel().getDataType(dtName) == null) api.createDataType(dtName, false);

            schemaToTypeName.put(s, dtName);

            MDataType dt = api.getModel().getDataType(dtName);
            if (dt == null) throw new UseApiException("Could not obtain created data type " + dtName);

            push("Object");
            List<Field> fields = obj.getFields();

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
                pop();
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
            return seqName;
        }

        if (s instanceof org.tzi.use.dtdl.DTDLModel.Schema.Enum.Enum e) {
            String enumName = sanitize(currentPath() + "_Enum");
            schemaToTypeName.put(s, enumName);

            if (api.getModel().getDataType(enumName) == null) {
                List<String> lits = new ArrayList<>();
                for (EnumValue v : e.getValues()) {
                    if (v.getValue() != null) lits.add(String.valueOf(v.getValue().raw()));
                }
                EnumType et = api.createEnumeration(enumName, lits);
                createdEnums.put(enumName, et);
            }

            return enumName;
        }
        if (s instanceof NamedSchema ns) {
            String name = sanitize(ns.getName() != null ? ns.getName() : sanitize(currentPath() + "_Named"));
            schemaToTypeName.put(s, name);

            if (api.getModel().getDataType(name) == null) api.createDataType(name, false);
            return name;
        }
        if (s instanceof org.tzi.use.dtdl.DTDLModel.Schema.Map.Map m) {
            Schema k = m.getMapKey() != null ? m.getMapKey().getSchema() : null;
            Schema v = m.getMapValue() != null ? m.getMapValue().getSchema() : null;

            // create an entry datatype for map entries
            String entryDtName = sanitize(currentPath() + "_MapEntry");
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
            return seqName;
        }

        schemaToTypeName.put(s, "String");
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
                        logWriter.println("[DTDL->M] Could not resolve parent for extends: " + parentIface.getId());
                    continue;
                }

                api.createGeneralization(child.name(), parent.name());

                if (logWriter != null)
                    logWriter.println("[DTDL->M] created generalization: "
                            + child.name() + " -> " + parent.name());
            }
        }
    }

    private void registerEnumTypes() {
        // enums already registered through UseModelApi.createEnumeration during mapSchemaToTypeName
    }

    private void addAttributeToDataType(MDataType dt, String attrName, Type attrType) throws UseApiException {
        if (dt == null) throw new UseApiException("Target MDataType is null when adding attribute " + attrName);
        if (attrName == null || attrName.isEmpty()) throw new UseApiException("Attribute name required");
        if (attrType == null) {
            try { attrType = api.getType("String"); } catch (UseApiException ignored) { }
        }

        try {
            Class<?> attrCls = Class.forName("org.tzi.use.uml.mm.MAttribute");
            Constructor<?> ctor = attrCls.getDeclaredConstructor(String.class, org.tzi.use.uml.ocl.type.Type.class);
            ctor.setAccessible(true);
            Object attr = ctor.newInstance(attrName, attrType);
            Method addAttr = MDataType.class.getDeclaredMethod("addAttribute", attrCls);
            addAttr.setAccessible(true);
            addAttr.invoke(dt, attr);
        } catch (Exception ex) {
            throw new UseApiException("Failed to add attribute '" + attrName + "' to datatype '" + dt.name() + "'", ex);
        }
    }

    private String sanitize(String id) {
        if (id == null) return null;
        String s = id.replaceAll("[^A-Za-z0-9_]", "_");
        if (s.isEmpty()) s = "Unnamed";
        if (Character.isDigit(s.charAt(0))) s = "_" + s;
        return s;
    }

    private int stableHash(String s) {
        return s == null ? System.identityHashCode(this) : Math.abs(s.hashCode());
    }

    private String multiplicityToString(Integer min, Integer max) {
        int lo = min == null ? 0 : min.intValue();
        int hi = max == null ? Integer.MAX_VALUE : max.intValue();
        if (hi == Integer.MAX_VALUE) {
            if (lo == 0) return "0..*";
            if (lo == 1) return "1..*";
            return lo + "..*";
        } else {
            if (lo == hi) return String.valueOf(lo);
            return lo + ".." + hi;
        }
    }

    private String nonNull(String a, String b) {
        return a != null ? a : b;
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
