package org.tzi.use.dtdl.ast;

public abstract class ASTNode {
    public String id;
    protected String type;
    protected String comment;
    public String description;
    protected String displayName;

    protected void printsGeneralInfo() {
        System.out.println("  id=" + (id == null ? "<null>" : id)
                + ", type=" + (type == null ? "<null>" : type)
                + ", displayName=" + (displayName == null ? "<null>" : displayName));
        if (description != null && !description.isBlank()) {
            System.out.println("    description: " + description);
        }
        if (comment != null && !comment.isBlank()) {
            System.out.println("    comment: " + comment);
        }
    }
}