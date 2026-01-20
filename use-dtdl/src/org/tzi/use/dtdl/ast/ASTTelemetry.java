package org.tzi.use.dtdl.ast;

import org.tzi.use.dtdl.semantic.DTDLContext;
import org.tzi.use.dtdl.semantic.SemanticAnalyzer;
import org.tzi.use.dtdl.ast.schema.ASTSchema;

public class ASTTelemetry extends ASTContent {
    public ASTSchema schema;

    public ASTTelemetry() {
        this.schema = null;
    }

    public void prints() {
        this.printsGeneralInfo();
        System.out.println("ASTTelemetry.name: " + name);
        System.out.println("telemetry.schema: " + schema);
    }

    public void validate(SemanticAnalyzer analyzer) {
        DTDLContext ctx = analyzer.getContext();

        if (this.name == null || this.name.isBlank()) {
            ctx.report("Telemetry missing name", this.id);
        }

        Object raw = this.props.get("schema");
        ASTSchema resolved = null;

        if (raw instanceof String ref) {
            resolved = ctx.resolveSchemaRefGlobal(ref);
            if (resolved == null) {
                if (!ctx.hasModelSchema(ref)) {
                    ctx.report("Telemetry '" + this.name + "' unresolved schema: " + ref, this.id);
                }
            } else {
                this.schema = resolved;
            }
        } else if (raw instanceof ASTSchema) {
            this.schema = (ASTSchema) raw;
        } else if (this.schema == null) {
            ctx.report("Telemetry '" + this.name + "' missing schema", this.id);
        }

        // telemetry should not use reserved names
        if (this.name != null && (this.name.equalsIgnoreCase("@id") || this.name.equalsIgnoreCase("@type"))) {
            ctx.report("Telemetry '" + this.name + "' uses reserved name", this.id);
        }
    }
}