package org.tzi.use.dtdl.DTDLModel.Relationship;

public class BidirectionalRelationship extends Relationship {
    public String targetName;
    public Integer targetMinMultiplicity;
    public Integer targetMaxMultiplicity;


    public BidirectionalRelationship(String id, String targetName, Integer minMultiplicity, Integer maxMultiplicity) {
        super(id);
        this.targetName = targetName;
       this.setMinMultiplicity(minMultiplicity);
       this.setMaxMultiplicity(maxMultiplicity);
    }

    public void setTargetMinMultiplicity(Integer m) {
        this.targetMinMultiplicity = (m == null || m < 0) ? 0 : m;
    }

    public void setTargetMaxMultiplicity(Integer m) {
        if (m == null || m <= 0) {
            this.targetMaxMultiplicity = Integer.MAX_VALUE; // unbounded (DTDL default)
        } else {
            this.targetMaxMultiplicity = m;
        }
    }

    @Override
    public void prints(int indent) {
        String ind = indent(indent);
        System.out.println(ind + "Bidrectional Relationship:");
        super.prints(indent + 2);
        System.out.println(ind + "  name: " + safe(name));
        System.out.println(ind + "  targetName: " + (targetName != null ? targetName : "(null)"));
        System.out.println(ind + "  minMultiplicity: " + (this.getMinMultiplicity() == null ? "(null)" : this.getMinMultiplicity()));
        System.out.println(ind + "  maxMultiplicity: " + (this.getMaxMultiplicity() == null ? "(null)" : this.getMaxMultiplicity()));
        System.out.println(ind + "  targetMinMultiplicity: " + (targetMinMultiplicity == null ? "(null)" : targetMinMultiplicity));
        System.out.println(ind + "  targetMaxMultiplicity: " + (targetMaxMultiplicity == null ? "(null)" : targetMaxMultiplicity));
    }
}