package org.tzi.use.dtdl.ast.schema;

public class ASTArray extends ASTSchema {
    public ASTSchema elementSchema;

    @Override
    public void prints() {
        this.printsGeneralInfo();
        elementSchema.prints();
    }
}