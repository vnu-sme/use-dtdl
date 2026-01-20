package org.tzi.use.dtdl.DTDLModel;

public abstract class Element {
    protected String id;
    protected String type;
    protected String comment;
    protected String description;
    protected String displayName;

    public String getId() { return id; }
    public String getType() { return type; }
    public String getComment() { return comment; }

    public void setComment(String comment) { this.comment = comment; }

    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void prints() {
        prints(0);
    }

    public void setGeneralInfo(String id, String type, String comment, String description, String displayName) {
        this.id = id;
        this.type = type;
        this.comment = comment;
        this.description = description;
        this.displayName = displayName;
    }

    public void prints(int indent) {
        String ind = indent(indent);
        System.out.println(ind + "id: " + safe(id));
        System.out.println(ind + "type: " + safe(type));
        System.out.println(ind + "displayName: " + safe(displayName));
        System.out.println(ind + "description: " + safe(description));
        System.out.println(ind + "comment: " + safe(comment));
    }

    /* helpers */
    protected static String indent(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(' ');
        return sb.toString();
    }

    protected static String safe(String s) {
        return s == null ? "(null)" : s;
    }
}
