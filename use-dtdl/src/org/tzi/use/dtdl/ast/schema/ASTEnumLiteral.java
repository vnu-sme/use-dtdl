package org.tzi.use.dtdl.ast.schema;

public class ASTEnumLiteral {
    public Integer intValue;
    public String stringValue;

    public ASTEnumLiteral(int value) {
        this.intValue = value;
        this.stringValue = null;
    }

    public ASTEnumLiteral(String value) {
        this.stringValue = value;
        this.intValue = null;
    }

    public boolean isInt() {
        return intValue != null;
    }

    public boolean isString() {
        return stringValue != null;
    }

    public Object raw() {
        return isInt() ? intValue : stringValue;
    }

    public void prints() {
        System.out.println("ASTEnumLiteral.intValue = " + intValue + ", ASTEnumLiteral.stringValue = " + stringValue);
    }
}
