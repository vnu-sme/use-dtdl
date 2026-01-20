package org.tzi.use.dtdl.ast.schema;

import java.util.List;

public class ASTEnum extends ASTSchema {
    public ASTPrimitiveSchema valueSchema;
    public List<ASTEnumValue> enumValues;

    @Override
    public void prints() {
        this.printsGeneralInfo();
        System.out.println("ASTEnum.ASTPrimitiveSchema: " + valueSchema);
        for (ASTEnumValue enumValue : enumValues) {
            enumValue.prints();
        }
    }
}
