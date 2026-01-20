package org.tzi.use.dtdl.ast.schema;

import org.tzi.use.dtdl.ast.ASTNode;

public abstract class ASTSchema extends ASTNode {
    public java.util.Map<String,Object> props = new java.util.LinkedHashMap<>();

    public abstract void prints();
}