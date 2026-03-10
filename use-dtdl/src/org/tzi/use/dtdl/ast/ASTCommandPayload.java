package org.tzi.use.dtdl.ast;

import org.tzi.use.dtdl.ast.schema.ASTSchema;
import org.tzi.use.dtdl.semantic.DTDLContext;
import org.tzi.use.dtdl.semantic.SemanticAnalyzer;

import static org.tzi.use.dtdl.ast.schema.SchemaFactory.primitiveSchemaFromName;

public class ASTCommandPayload extends ASTContent {
    public boolean nullable;
    public ASTSchema schema;

    public void prints() {
        System.out.println("START OF ASTCommandPayload: ");
        this.printsGeneralInfo();
        System.out.println("ASTCommandPayload.name: " + name);
        System.out.println("ASTCommandPayload.nullable = " + this.nullable);
        System.out.println("ASTCommandPayload.schema = " + this.schema);
        if (schema != null) {
            schema.prints();
        }
    }

    public void validate(SemanticAnalyzer analyzer) {
        DTDLContext ctx = analyzer.getContext();

        // name optional for payloads, but id should exist
        if (this.name != null && this.name.isBlank()) {
            ctx.report("Command payload has empty name", this.id);
        }

        // resolve schema similar to properties
        Object raw = this.props.get("schema");
        if (raw instanceof String ref) {
            ASTSchema resolved = ctx.resolveSchemaRefGlobal(ref);
            if (resolved == null) {
                if (!ctx.hasModelSchema(ref)) {
                    ctx.report("Command payload '" + this.name + "' unresolved schema: " + ref, this.id);
                }
            } else {
                this.schema = resolved;
            }
        } else if (raw instanceof ASTSchema) {
            this.schema = (ASTSchema) raw;
        } else if (this.schema == null) {
            // payload without explicit schema is allowed (maybe free-form) — warn
            ctx.report("Command payload '" + this.name + "' missing schema", this.id);
        }

        Object n = this.props.get("nullable");
        if (n != null && !(n instanceof Boolean)) {
            ctx.report("Command payload '" + this.name + "' nullable must be boolean", this.id);
        } else if (n != null) {
            this.nullable = (Boolean) n;
        }
    }
}
