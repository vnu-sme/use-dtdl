package org.tzi.use.dtdl.ast;

import org.tzi.use.dtdl.ast.schema.ASTSchema;
import org.tzi.use.dtdl.semantic.DTDLContext;
import org.tzi.use.dtdl.semantic.SemanticAnalyzer;

import static org.tzi.use.dtdl.ast.schema.SchemaFactory.primitiveSchemaFromName;

public class ASTCommandPayload extends ASTContent {
    public boolean nullable;
    public ASTSchema schema;

    public void prints() {
        System.out.println("  CommandPayload: " + (name == null ? "<anon>" : name) + "  (id=" + id + ") nullable=" + nullable);
        this.printsGeneralInfo();
        if (schema != null) {
            System.out.println("    schema:");
            schema.prints();
        } else {
            System.out.println("    schema: <none>");
        }
    }

    public void validate(SemanticAnalyzer analyzer) {
        DTDLContext ctx = analyzer.getContext();

        // name optional for payloads, but id should exist
        if (this.name != null && this.name.isBlank()) {
            ctx.report("Command payload has empty name", this.id);
        }
    }
}
