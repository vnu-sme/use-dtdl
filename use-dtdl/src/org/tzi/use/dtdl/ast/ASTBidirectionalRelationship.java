package org.tzi.use.dtdl.ast;

public class ASTBidirectionalRelationship extends ASTRelationship {
    public String targetRoleName;
    public Integer targetMinMultiplicity;
    public Integer targetMaxMultiplicity;

    public ASTBidirectionalRelationship(String id, String name, String comment, String description, String displayName, String type,
                                        String targetRoleName)  {
        this.id = id;
        this.name = name;
        this.comment = comment;
        this.description = description;
        this.displayName = displayName;
        this.type = type;
        this.targetRoleName = targetRoleName;
    }

    @Override
    public void prints() {
        System.out.println("=== BIDRECTIONAL RELATIONSHIP: " + (name == null ? "<anon>" : name) + "  (id=" + id + ") ===");
        this.printsGeneralInfo();
        System.out.println("  targetInterface: " + (targetInterface == null ? "<null>" : targetInterface));
        System.out.println("  targetRoleName: " + (targetRoleName == null ? "<null>" : targetRoleName));
        System.out.println("  multiplicity: " + minMultiplicity + " .. " + maxMultiplicity + "  writable=" + writable);
    }
}
