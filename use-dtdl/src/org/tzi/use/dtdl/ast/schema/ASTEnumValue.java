package org.tzi.use.dtdl.ast.schema;

public class ASTEnumValue extends ASTSchema {
    public String name;
    public ASTEnumLiteral value;

    @Override
    public void prints() {
        this.printsGeneralInfo();
        System.out.println("ASTEnumValue.name: " + name);
        value.prints();
    }
}
