package org.tzi.use.dtdl.DTDLModel.Relationship;

import org.tzi.use.dtdl.DTDLModel.ContentElement;
import org.tzi.use.dtdl.DTDLModel.Property.Property;
import org.tzi.use.dtdl.DTDLModel.Interface;

import java.util.ArrayList;
import java.util.List;

public class Relationship extends ContentElement {
    // now holds an actual Interface reference (or null)
    private Interface target;
    private Integer minMultiplicity;
    private Integer maxMultiplicity;
    private boolean writable;
    private final List<Property> properties = new ArrayList<>();

    public Relationship(String id) { this.id = id; this.type = "Relationship"; }

    public void setName(String name) { this.name = name; }

    /** Set target interface (object reference). Null allowed if unresolved. */
    public void setTarget(Interface target) { this.target = target; }

    /** Return target interface object, or null if not set / unresolved. */
    public Interface getTarget() { return target; }

    public void setMinMultiplicity(Integer m) {
        this.minMultiplicity = (m == null || m < 0) ? 0 : m;
    }

    public void setMaxMultiplicity(Integer m) {
        if (m == null || m <= 0) {
            this.maxMultiplicity = Integer.MAX_VALUE; // unbounded (DTDL default)
        } else {
            this.maxMultiplicity = m;
        }
    }

    public void setWritable(boolean w) { this.writable = w; }

    public void addProperty(Property p) { properties.add(p); }

    public List<Property> getProperties() { return properties; }

    public Integer getMinMultiplicity() {
        return minMultiplicity;
    }

    public Integer getMaxMultiplicity() {
        return maxMultiplicity;
    }

    public boolean isWritable() {
        return writable;
    }

    @Override
    public void prints() { prints(0); }

    public void prints(int indent) {
        String ind = indent(indent);
        System.out.println(ind + "Relationship:");
        super.prints(indent + 2);
        System.out.println(ind + "  name: " + safe(name));
        System.out.println(ind + "  target: " + (target != null ? safe(target.getId()) : "(null)"));
        System.out.println(ind + "  minMultiplicity: " + (minMultiplicity == null ? "(null)" : minMultiplicity));
        System.out.println(ind + "  maxMultiplicity: " + (maxMultiplicity == null ? "(null)" : maxMultiplicity));
        System.out.println(ind + "  writable: " + writable);

        if (!properties.isEmpty()) {
            System.out.println(ind + "  properties:");
            for (Property p : properties) {
                p.prints(indent + 4);
            }
        }
    }
}
