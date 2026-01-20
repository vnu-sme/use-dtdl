package org.tzi.use.dtdl.ast.schema;

public class ASTMap extends ASTSchema {
    public ASTMapKey mapKey;
    public ASTMapValue mapValue;

    @Override
    public void prints() {
        this.printsGeneralInfo();
        if (this.mapKey != null) {
            mapKey.prints();
        }
        if (this.mapValue != null) {
            mapValue.prints();
        }
    }
}
