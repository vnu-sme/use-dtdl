package org.tzi.use.dtdl.ast;

public abstract class ASTContent extends ASTNode {
    public String name;
    public String semanticType;
    public java.util.Map<String,Object> props = new java.util.LinkedHashMap<>();
}