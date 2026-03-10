package org.tzi.use.dtdl.ast;

import org.tzi.use.dtdl.semantic.DTDLContext;
import org.tzi.use.dtdl.semantic.SemanticAnalyzer;
import org.tzi.use.dtdl.ast.schema.ASTSchema;

public class ASTProperty extends ASTContent {
    public ASTSchema schema;
    public boolean writable;

    public ASTProperty() {
        this.schema = null;
        this.writable = false;
    }

    public ASTProperty(ASTSchema schema, boolean writable) {
        this.schema = schema;
        this.writable = writable;
    }

    public void prints() {
        this.printsGeneralInfo();
        System.out.println("properties.name: " + name);
        System.out.println("properties.schema: " + schema);
        if (schema != null) {
            schema.prints();
        }
        System.out.println("properties.writable: " + writable);
    }

    public void validate(SemanticAnalyzer analyzer) {
        DTDLContext ctx = analyzer.getContext();

        // name must be present
        if (this.name == null || this.name.isBlank()) {
            ctx.report("Property missing name", this.id);
        }

        // schema resolution: allow primitive, AST-local schema, or model-registered schema
        Object raw = this.props.get("schema");
        ASTSchema resolved = null;

        if (raw instanceof String ref) {
            resolved = ctx.resolveSchemaRefGlobal(ref);
            if (resolved == null) {
                // maybe resides in registered models
                if (!ctx.hasModelSchema(ref)) {
                    ctx.report("Property '" + this.name + "' has unresolved schema reference: " + ref, this.id);
                }
            } else {
                this.schema = resolved;
            }
        } else if (raw instanceof ASTSchema) {
            this.schema = (ASTSchema) raw;
            // deeper schema validation delegated to schema resolver earlier
        } else if (this.schema == null) {
            // no schema found
            ctx.report("Property '" + this.name + "' missing schema", this.id);
        }

        // writable must be boolean (if provided)
        Object w = this.props.get("writable");
        if (w != null && !(w instanceof Boolean)) {
            ctx.report("Property '" + this.name + "' writable must be boolean", this.id);
        } else if (w != null) {
            this.writable = (Boolean) w;
        }

        // additional semantic rule: property name should not collide with reserved words
        if (this.name != null) {
            String n = this.name.trim();
            if (n.equalsIgnoreCase("@id") || n.equalsIgnoreCase("@type")) {
                ctx.report("Property '" + this.name + "' uses reserved name", this.id);
            }
        }
    }
}