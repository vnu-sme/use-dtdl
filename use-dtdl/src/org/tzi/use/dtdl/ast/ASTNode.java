package org.tzi.use.dtdl.ast;

public abstract class ASTNode {
    public String id;
    protected String type;
    protected String comment;
    public String description;
    protected String displayName;

    protected void printsGeneralInfo() {
        System.out.println("ASTNode.displayName: " + displayName);
        System.out.println("ASTNode.id: " + id);
        System.out.println("ASTNode.description: " + description);
        System.out.println("ASTNode.comment: " + comment);
        System.out.println("ASTNode.type: " + type);
    }
}