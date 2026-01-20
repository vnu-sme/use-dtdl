package org.tzi.use.dtdl.ast;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.tzi.use.dtdl.DTDLModel.DTDLModel;
import org.tzi.use.dtdl.DTDLModel.Interface;
import org.tzi.use.dtdl.DTDLModel.Property.Property;
import org.tzi.use.dtdl.DTDLModel.Command.Command;
import org.tzi.use.dtdl.DTDLModel.Command.CommandPayload;
import org.tzi.use.dtdl.DTDLModel.Telemetry.Telemetry;
import org.tzi.use.dtdl.DTDLModel.Component.Component;
import org.tzi.use.dtdl.DTDLModel.Relationship.Relationship;

import org.tzi.use.dtdl.ast.schema.*;
import org.tzi.use.dtdl.semantic.DTDLContext;

/**
 * Converter: AST -> DTDLModel
 */
public final class Converter {
    private final DTDLContext ctx;
    private final DTDLModel model = new DTDLModel();
    private final Map<String, Interface> ifaceIndex = new HashMap<>();

    public Converter(DTDLContext ctx) {
        this.ctx = ctx;
    }

    public DTDLModel buildModel(List<ASTInterface> astIfaces) {
        if (astIfaces == null) return model;

        // Phase 1: create Interface shells
        for (ASTInterface a : astIfaces) {
            Object idObj = a.props.get("@id");
            if (!(idObj instanceof String id)) continue;

            Interface di = new Interface(id);

            Object ctxObj = a.props.get("@context");
            if (ctxObj instanceof String) di.setContext((String) ctxObj);

            di.setDescription(a.description);
            di.setDisplayName(a.displayName);

            Object comment = a.props.get("comment");
            if (comment instanceof String) di.setComment((String) comment);

            model.addInterface(di);
            ifaceIndex.put(id, di);
        }

        // Phase 2: per-interface schemas
        for (ASTInterface a : astIfaces) {
            Object idObj = a.props.get("@id");
            if (!(idObj instanceof String id)) continue;
            Interface di = model.getInterface(id);
            if (di == null) continue;

            if (a.schemas != null) {
                for (ASTSchema astSchema : a.schemas) {
                    if (astSchema == null) continue;
                    Object sid = astSchema.props.get("@id");
                    if (!(sid instanceof String sidStr)) continue;
                    org.tzi.use.dtdl.DTDLModel.Schema.Schema conv = convertSchema(astSchema);
                    if (conv != null) di.addSchema(sidStr, conv);
                }
            }
        }

        // Phase 3: contents (properties/telemetries/commands/relationships/components)
        for (ASTInterface a : astIfaces) {
            Object idObj = a.props.get("@id");
            if (!(idObj instanceof String ifaceId)) continue;
            Interface di = model.getInterface(ifaceId);
            if (di == null) continue;

            // properties
            if (a.properties != null) {
                for (ASTProperty p : a.properties) {
                    if (p == null) continue;
                    String name = p.name;
                    String id = ifaceId + ":property:" + (name != null ? name : "anon");
                    if (p.id != null) id = p.id;
                    Property prop = new Property(id);
                    prop.setGeneralInfo(p.id, p.type, p.comment, p.description, p.displayName);
                    prop.setName(name);
                    if (p.schema != null) prop.setSchema(convertSchema(p.schema));
                    prop.setWritable(p.writable);
                    di.addContent(prop);
                }
            }

            // telemetries
            if (a.telemetries != null) {
                for (ASTTelemetry t : a.telemetries) {
                    if (t == null) continue;
                    String name = t.name;
                    String id = ifaceId + ":telemetry:" + (name != null ? name : "anon");
                    if (t.id != null) id = t.id;
                    Telemetry tel = new Telemetry(id);
                    tel.setGeneralInfo(t.id, t.type, t.comment, t.description, t.displayName);
                    tel.setName(name);
                    if (t.schema != null) tel.setSchema(convertSchema(t.schema));
                    di.addContent(tel);
                }
            }

            // commands
            if (a.commands != null) {
                for (ASTCommand c : a.commands) {
                    if (c == null) continue;
                    String name = c.name;
                    String id = ifaceId + ":command:" + (name != null ? name : "anon");
                    if (c.id != null) id = c.id;
                    Command cmd = new Command(id);
                    cmd.setName(name);
                    cmd.setGeneralInfo(c.id, c.type, c.comment, c.description, c.displayName);

                    if (c.request != null) {
                        CommandPayload req = new CommandPayload();
                        req.setGeneralInfo(c.request.id, c.request.type, c.request.comment, c.request.description, c.request.displayName);
                        req.setName(c.request.name);
                        if (c.request.schema != null) req.setSchema(convertSchema(c.request.schema));
                        req.setNullable(c.request.nullable);
                        cmd.setRequest(req);
                    }
                    if (c.response != null) {
                        CommandPayload rsp = new CommandPayload();
                        rsp.setGeneralInfo(c.response.id, c.response.type, c.response.comment, c.response.description, c.response.displayName);
                        rsp.setName(c.response.name);
                        if (c.response.schema != null) rsp.setSchema(convertSchema(c.response.schema));
                        rsp.setNullable(c.response.nullable);
                        cmd.setResponse(rsp);
                    }
                    di.addContent(cmd);
                }
            }

            // relationships
            if (a.relationships != null) {
                for (ASTRelationship r : a.relationships) {
                    if (r == null) continue;
                    String name = r.name;
                    String id = ifaceId + ":relationship:" + (name != null ? name : "anon");
                    Relationship rel = new Relationship(id);
                    rel.setGeneralInfo(r.id, r.type, r.comment, r.description, r.displayName);
                    rel.setName(name);

                    // resolve target interface string -> actual Interface object in model (if available)
                    if (r.targetInterface != null) {
                        // 1) look in the model being built (same import)
                        Interface targetIface = model.getInterface(r.targetInterface);

                        // 2) if not found, consult the registry via context (canonical model)
                        if (targetIface == null) {
                            targetIface = ctx.getInterfaceFromModels(r.targetInterface);
                        }

                        rel.setTarget(targetIface);
                    }

                    rel.setMinMultiplicity(r.minMultiplicity);
                    rel.setMaxMultiplicity(r.maxMultiplicity);
                    rel.setWritable(r.writable);

                    if (r.properties != null) {
                        for (ASTProperty rp : r.properties) {
                            if (rp == null) continue;
                            String base = rel.getTarget() != null
                                            ? rel.getTarget().getId()
                                            : ifaceId;

                            String rpId = base + ":prop:" + (rp.name != null ? rp.name : "anon");

                            Property rpModel = new Property(rpId);
                            rpModel.setGeneralInfo(rp.name, rp.type, rp.comment, rp.description, rp.displayName);
                            rpModel.setName(rp.name);
                            if (rp.schema != null) rpModel.setSchema(convertSchema(rp.schema));
                            rpModel.setWritable(rp.writable);
                            rel.addProperty(rpModel);
                        }
                    }
                    di.addContent(rel);
                }
            }

            // components
            if (a.components != null) {
                for (ASTComponent c : a.components) {
                    if (c == null) continue;
                    String name = c.name;
                    String id = ifaceId + ":component:" + (name != null ? name : "anon");
                    if (c.id != null) id = c.id;
                    Component comp = new Component(id);
                    comp.setGeneralInfo(c.id, c.type, c.comment, c.description, c.displayName);
                    comp.setName(name);
                    String target = c.schemaInterface;
                    if (target != null) {
                        Interface targetIface = model.getInterface(target);
                        if (targetIface == null) {
                            targetIface = ctx.getInterfaceFromModels(target);
                        }
                        if (targetIface != null) comp.setSchemaInterface(targetIface);
                    }
                    di.addContent(comp);
                }
            }
        }

        return model;
    }

    /**
     * Convert ASTSchema -> model Schema (base type).
     * Uses fully-qualified model class names for schema kinds that clash with Java types.
     */
    private org.tzi.use.dtdl.DTDLModel.Schema.Schema convertSchema(ASTSchema s) {
        if (s == null) return null;

        // primitive
        if (s instanceof ASTPrimitiveSchema) {
            String lower = getLower((ASTPrimitiveSchema) s);
            return new org.tzi.use.dtdl.DTDLModel.Schema.PrimitiveType(lower);
        }

        // enum
        if (s instanceof ASTEnum e) {
            org.tzi.use.dtdl.DTDLModel.Schema.Enum.Enum out = new org.tzi.use.dtdl.DTDLModel.Schema.Enum.Enum();

            if (e.valueSchema != null) {
                org.tzi.use.dtdl.DTDLModel.Schema.PrimitiveType vt = (org.tzi.use.dtdl.DTDLModel.Schema.PrimitiveType) convertSchema(e.valueSchema);
                out.setValueSchema(vt);
            }

            if (e.enumValues != null) {
                for (ASTEnumValue v : e.enumValues) {
                    if (v == null) continue;
                    org.tzi.use.dtdl.DTDLModel.Schema.Enum.EnumValue ev =
                            new org.tzi.use.dtdl.DTDLModel.Schema.Enum.EnumValue();
                    ev.setName(v.name);
                    if (v.value != null) {
                        Object raw = v.value.raw();
                        if (raw instanceof Integer) {
                            ev.setValue(new org.tzi.use.dtdl.DTDLModel.Schema.Enum.EnumLiteral((Integer) raw));
                        } else if (raw instanceof String) {
                            ev.setValue(new org.tzi.use.dtdl.DTDLModel.Schema.Enum.EnumLiteral((String) raw));
                        }
                    }
                    out.addEnumValue(ev);
                }
            }

            return out;
        }

        // object
        if (s instanceof ASTObject o) {
            org.tzi.use.dtdl.DTDLModel.Schema.Object.Object out =
                    new org.tzi.use.dtdl.DTDLModel.Schema.Object.Object();
            if (o.fields != null) {
                for (ASTField f : o.fields) {
                    if (f == null) continue;
                    org.tzi.use.dtdl.DTDLModel.Schema.Schema fieldSchema = convertSchema(f.schema);
                    org.tzi.use.dtdl.DTDLModel.Schema.Object.Field mf =
                            new org.tzi.use.dtdl.DTDLModel.Schema.Object.Field(f.name, fieldSchema);
                    out.addField(mf);
                }
            }
            return out;
        }

        // map
        if (s instanceof ASTMap m) {
            org.tzi.use.dtdl.DTDLModel.Schema.Map.Map out =
                    new org.tzi.use.dtdl.DTDLModel.Schema.Map.Map();
            if (m.mapKey != null) {
                org.tzi.use.dtdl.DTDLModel.Schema.Schema kSchema = convertSchema(m.mapKey.schema);
                org.tzi.use.dtdl.DTDLModel.Schema.Map.MapKey mk =
                        new org.tzi.use.dtdl.DTDLModel.Schema.Map.MapKey(m.mapKey.name, kSchema);
                out.setMapKey(mk);
            }
            if (m.mapValue != null) {
                org.tzi.use.dtdl.DTDLModel.Schema.Schema vSchema = convertSchema(m.mapValue.schema);
                org.tzi.use.dtdl.DTDLModel.Schema.Map.MapValue mv =
                        new org.tzi.use.dtdl.DTDLModel.Schema.Map.MapValue(vSchema);
                out.setMapValue(mv);
            }
            return out;
        }

        // array
        if (s instanceof ASTArray a) {
            org.tzi.use.dtdl.DTDLModel.Schema.Array.Array out =
                    new org.tzi.use.dtdl.DTDLModel.Schema.Array.Array();
            if (a.elementSchema != null) out.setElementSchema(convertSchema(a.elementSchema));
            return out;
        }

        // unknown
        return null;
    }

    private static String getLower(ASTPrimitiveSchema ps) {
        String lower;
        switch (ps.kind) {
            case BOOLEAN -> lower = "boolean";
            case INTEGER -> lower = "integer";
            case LONG -> lower = "long";
            case FLOAT -> lower = "float";
            case DOUBLE -> lower = "double";
            case STRING -> lower = "string";
            case DATE -> lower = "date";
            case DATETIME -> lower = "datetime";
            case DURATION -> lower = "duration";
            case BYTES -> lower = "bytes";
            default -> lower = "string";
        }
        return lower;
    }
}
