package org.tzi.use.dtdl.ast.schema;

public class ASTMap extends ASTSchema {
    public ASTMapKey mapKey;
    public ASTMapValue mapValue;

    @Override
    public void prints() {
        System.out.println("  Map (id=" + id + ")");
        if (mapKey != null) {
            System.out.println("    key:");
            mapKey.prints();
        } else {
            System.out.println("    key: <none>");
        }
        if (mapValue != null) {
            System.out.println("    value:");
            mapValue.prints();
        } else {
            System.out.println("    value: <none>");
        }
    }
}
