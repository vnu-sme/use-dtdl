package org.tzi.use.dtdl.ast;


import org.tzi.use.dtdl.semantic.DTDLContext;
import org.tzi.use.dtdl.semantic.SemanticAnalyzer;

public class ASTComponent extends ASTContent {
    public String schemaInterface; // already present

    public void prints() { this.printsGeneralInfo(); System.out.println("components.schemaInterface: " + schemaInterface); }

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
            else if (raw instanceof java.util.Map<?, ?> m) {
                Object dtmi = m.get("dtmi");
                if (dtmi instanceof String) this.schemaInterface = (String) dtmi;
                else if (m.get("@id") instanceof String) this.schemaInterface = (String) m.get("@id");
            }
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
