package org.tzi.use.dtdl.ast;

import org.tzi.use.dtdl.semantic.DTDLContext;
import org.tzi.use.dtdl.semantic.SemanticAnalyzer;
import org.tzi.use.dtdl.ast.schema.ASTSchema;

public class ASTProperty extends ASTContent {
    public ASTSchema schema;
    public boolean writable;

    public java.util.List<String> semanticTypes;
    public String semanticTypePrimary;
    public String unit;

    public ASTProperty() {
        this.schema = null;
        this.writable = false;
    }

    public ASTProperty(ASTSchema schema, boolean writable) {
        this.schema = schema;
        this.writable = writable;
    }

    public void prints() {
        System.out.println("  Property: " + (name == null ? "<anon>" : name) + "  (id=" + id + ")");
        this.printsGeneralInfo();
        System.out.println("    writable: " + writable);
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

        // name must be present
        if (this.name == null || this.name.isBlank()) {
            ctx.report("Property missing name", this.id);
        }

        // property name should not collide with reserved words
        if (this.name != null) {
            String n = this.name.trim();
            if (n.equalsIgnoreCase("@id") || n.equalsIgnoreCase("@type")) {
                ctx.report("Property '" + this.name + "' uses reserved name", this.id);
            }
        }
    }
}