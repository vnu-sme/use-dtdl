package org.tzi.use.dtdl.ast.schema;

import java.util.List;

public class ASTObject extends ASTSchema {
    public List<ASTField> fields;

    @Override
    public void prints() {
        this.printsGeneralInfo();

        for (ASTField field : fields) {
            field.prints();
        }
    }
}