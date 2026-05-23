package org.tzi.use.dtdl.ast;

import org.tzi.use.dtdl.semantic.DTDLContext;
import org.tzi.use.dtdl.semantic.SemanticAnalyzer;
import org.tzi.use.dtdl.ast.schema.ASTSchema;

public class ASTTelemetry extends ASTContent {
    public ASTSchema schema;

    public java.util.List<String> semanticTypes;
    public String semanticTypePrimary;
    public String unit;

    public ASTTelemetry() {
        this.schema = null;
        this.semanticTypes = null;
        this.semanticTypePrimary = null;
        this.unit = null;
    }

    public void prints() {
        System.out.println("  Telemetry: " + (name == null ? "<anon>" : name) + "  (id=" + id + ")");
        this.printsGeneralInfo();
        if (schema != null) {
            System.out.println("    schema:");
            schema.prints();
        } else {
            System.out.println("    schema: <none>");
        }
        System.out.println("    unit: " + unit);
        if (semanticTypePrimary != null) {
            System.out.println("    semanticTypePrimary: " + semanticTypePrimary);
        }
        if (semanticTypes != null && !semanticTypes.isEmpty()) {
            System.out.println("    semanticTypes: " + semanticTypes);
        }
    }

    public void validate(SemanticAnalyzer analyzer) {
        DTDLContext ctx = analyzer.getContext();

        if (this.name == null || this.name.isBlank()) {
            ctx.report("Telemetry missing name", this.id);
        }

        // telemetry should not use reserved names
        if (this.name != null && (this.name.equalsIgnoreCase("@id") || this.name.equalsIgnoreCase("@type"))) {
            ctx.report("Telemetry '" + this.name + "' uses reserved name", this.id);
        }
    }
}