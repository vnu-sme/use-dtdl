package org.tzi.use.dtdl.ast;

import java.util.Map;

public abstract class ASTContent extends ASTNode {
    public String name;
    public String semanticType;
    public java.util.Map<String,Object> props = new java.util.LinkedHashMap<>();

    public static ASTContent fromRaw(Object raw) {
        if (raw instanceof ASTContent c) {
            return c;
        }
        if (raw instanceof Map<?, ?> m) {
            ASTContent c = new ASTContent() {};
            c.props.putAll((Map<? extends String, ?>) m);
            if (m.get("name") instanceof String) {
                c.name = (String) m.get("name");
            }
            return c;
        }
        return null;
    }
}