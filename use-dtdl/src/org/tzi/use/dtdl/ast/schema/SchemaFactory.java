package org.tzi.use.dtdl.ast.schema;


public final class SchemaFactory {

    public static ASTSchema primitiveSchemaFromName(String name) {
        if (name == null)
            return null;

        return switch (name.toLowerCase()) {
            case "boolean" -> new ASTPrimitiveSchema(ASTPrimitiveSchema.Kind.BOOLEAN);
            case "integer" -> new ASTPrimitiveSchema(ASTPrimitiveSchema.Kind.INTEGER);
            case "long" -> new ASTPrimitiveSchema(ASTPrimitiveSchema.Kind.LONG);
            case "float" -> new ASTPrimitiveSchema(ASTPrimitiveSchema.Kind.FLOAT);
            case "double" -> new ASTPrimitiveSchema(ASTPrimitiveSchema.Kind.DOUBLE);
            case "string" -> new ASTPrimitiveSchema(ASTPrimitiveSchema.Kind.STRING);
            case "date" -> new ASTPrimitiveSchema(ASTPrimitiveSchema.Kind.DATE);
            case "datetime" -> new ASTPrimitiveSchema(ASTPrimitiveSchema.Kind.DATETIME);
            case "duration" -> new ASTPrimitiveSchema(ASTPrimitiveSchema.Kind.DURATION);
            case "bytes" -> new ASTPrimitiveSchema(ASTPrimitiveSchema.Kind.BYTES);
            default -> throw new IllegalArgumentException(
                    "Unknown DTDL primitive schema: " + name
            );
        };
    }

    public static boolean isPrimitiveName(String name) {
        if (name == null) return false;
        return switch (name.toLowerCase()) {
            case "boolean", "integer", "long",
                 "float", "double",
                 "string",
                 "date", "datetime",
                 "duration",
                 "bytes" -> true;
            default -> false;
        };
    }

    private SchemaFactory() {}
}
