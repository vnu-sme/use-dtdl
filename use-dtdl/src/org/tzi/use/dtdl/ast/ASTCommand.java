package org.tzi.use.dtdl.ast;


import org.tzi.use.dtdl.ast.schema.ASTSchema;
import org.tzi.use.dtdl.semantic.DTDLContext;
import org.tzi.use.dtdl.semantic.SemanticAnalyzer;

import static org.tzi.use.dtdl.ast.schema.SchemaFactory.primitiveSchemaFromName;

public class ASTCommand extends ASTContent {
    public ASTCommandPayload request;
    public ASTCommandPayload response;

    public ASTCommand() {
        this.request = null;
        this.response = null;
    }

    public void prints() {
        this.printsGeneralInfo();
        System.out.println("ASTCommand.name: " + name);
        if (this.request != null) {
            this.request.prints();
        }
        if (this.response != null) {
            this.response.prints();
        }
    }

    public void validate(SemanticAnalyzer analyzer) {
        DTDLContext ctx = analyzer.getContext();

        // name is required and must be a non-empty identifier
        if (this.name == null || this.name.isBlank()) {
            ctx.report("Command missing name", this.id);
        }

        // validate request and response payloads
        if (request != null) request.validate(analyzer);
        if (response != null) response.validate(analyzer);
//
//        // Disallow request+response with same payload name? not required, but warn if both null
//        if (request == null && response == null) {
//            ctx.report("Command '" + this.name + "' should have at least request or response payload", this.id);
//        }
    }
}
