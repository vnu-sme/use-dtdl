package org.tzi.use.dtdl.ast.schema;

public class ASTPrimitiveSchema extends ASTSchema {
    @Override
    public void prints() {
        System.out.println("this is primitive schema, i dont know how to print");
    }

    public enum Kind {
        BOOLEAN,
        INTEGER,
        LONG,
        FLOAT,
        DOUBLE,
        STRING,
        DATE,
        DATETIME,
        DURATION,
        BYTES
    }

    public final Kind kind;

    public ASTPrimitiveSchema(Kind kind) {
        this.kind = kind;
    }

    @Override
    public String toString() {
        return "Primitive(" + kind + ")";
    }
}