package org.tzi.use.dtdl.ast;


import org.tzi.use.dtdl.semantic.DTDLContext;
import org.tzi.use.dtdl.semantic.SemanticAnalyzer;

public class ASTComponent extends ASTContent {
    public String schemaInterface; // already present

    public void prints() {
        System.out.println("=== COMPONENT: " + (name == null ? "<unnamed>" : name) + "  (id=" + id + ") ===");
        this.printsGeneralInfo();
        System.out.println("  schemaInterface: " + (schemaInterface == null ? "<null>" : schemaInterface));
    }

    public void validate(SemanticAnalyzer analyzer) {
        DTDLContext ctx = analyzer.getContext();

        // name check
        if (this.name == null || this.name.isBlank()) {
            ctx.report("Component missing name", this.id);
        }

        // resolve schema reference (string or map)
        if (this.schemaInterface == null) {
            Object raw = this.props.get("schema");
            if (raw instanceof String) this.schemaInterface = (String) raw;
        }

        if (this.schemaInterface == null || this.schemaInterface.isBlank()) {
            ctx.report("Component '" + this.name + "' missing schema reference", this.id);
        } else {
            // referenced interface may be in current AST or in registered models
            if (!ctx.hasInterface(this.schemaInterface)) {
                ctx.report("Component '" + this.name + "' references unknown interface: " + this.schemaInterface, this.id);
            }
        }
    }
}
