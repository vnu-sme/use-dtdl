package org.tzi.use.dtdl.ast;

import org.tzi.use.dtdl.semantic.DTDLContext;
import org.tzi.use.dtdl.semantic.SemanticAnalyzer;
import java.util.ArrayList;
import java.util.List;

public class ASTRelationship extends ASTContent {
    public String targetInterface;
    public int minMultiplicity;
    public int maxMultiplicity;
    public boolean writable;
    public List<ASTProperty> properties;

    public ASTRelationship() {
        targetInterface = null;
        writable = false;
        properties = new ArrayList<ASTProperty>();
    }

    public void prints() {
        this.printsGeneralInfo();
        System.out.println("ASTRelationship.name: " + name);
        System.out.println("relationships.TargetInterface: " + targetInterface);
        System.out.println("relationships.minMultiplicity: " + minMultiplicity);
        System.out.println("relationships.maxMultiplicity: " + maxMultiplicity);
        System.out.println("relationships.writable: " + writable);
        System.out.println("relationships.properties: " + properties);
        for (ASTProperty prop : properties) {
            prop.prints();
        }
    }

    public void validate(SemanticAnalyzer analyzer) {
        DTDLContext ctx = analyzer.getContext();

        if (this.name == null || this.name.isBlank()) {
            ctx.report("Relationship missing name", this.id);
        }

        // target interface resolution
        if (this.targetInterface == null || this.targetInterface.isBlank()) {
            Object t = this.props.get("target");
            if (t instanceof String) this.targetInterface = (String) t;
        }

        if (this.targetInterface == null || this.targetInterface.isBlank()) {
            ctx.report("Relationship '" + this.name + "' missing target", this.id);
        } else {
            // check existence either in AST context or registered models
            if (!ctx.hasInterface(this.targetInterface)) {
                ctx.report("Relationship '" + this.name + "' target interface not found: " + this.targetInterface, this.id);
            }
        }

        // multiplicities: sanity checks
        // if props contain minMultiplicity/maxMultiplicity, validate type and ranges
        Object minObj = this.props.get("minMultiplicity");
        Object maxObj = this.props.get("maxMultiplicity");

        int min = this.minMultiplicity;
        int max = this.maxMultiplicity;

        if (minObj instanceof String) {
            try { min = Integer.parseInt((String)minObj); } catch (Exception ex) { ctx.report("Relationship '" + this.name + "' minMultiplicity is not a valid integer", this.id); }
        } else if (minObj instanceof Number) {
            min = ((Number)minObj).intValue();
        }

        if (maxObj instanceof String) {
            try { max = Integer.parseInt((String)maxObj); } catch (Exception ex) { ctx.report("Relationship '" + this.name + "' maxMultiplicity is not a valid integer", this.id); }
        } else if (maxObj instanceof Number) {
            max = ((Number)maxObj).intValue();
        }

        // update parsed fields (so Converter sees the parsed multiplicities)
        this.minMultiplicity = min;
        this.maxMultiplicity = max;

        if (min < 0) {
            ctx.report("Relationship '" + this.name + "' minMultiplicity must be >= 0", this.id);
        }
        // if max is negative, assume unbounded (-1 allowed) else ensure max >= min
        if (max >= 0 && max < min) {
            ctx.report("Relationship '" + this.name + "' maxMultiplicity must be >= minMultiplicity", this.id);
        }
        // limit check to avoid insane multiplicities
        final int MAX_MULT = 1024;
        if (min > MAX_MULT || max > MAX_MULT) {
            ctx.report("Relationship '" + this.name + "' multiplicities exceed maximum allowed (" + MAX_MULT + ")", this.id);
        }

        // writable must be boolean (if provided)
        Object wr = this.props.get("writable");
        if (wr != null && !(wr instanceof Boolean)) {
            ctx.report("Relationship '" + this.name + "' writable must be boolean", this.id);
        } else if (wr != null) this.writable = (Boolean) wr;

        // validate nested property definitions
        for (ASTProperty p : properties) {
            if (p != null) p.validate(analyzer);
        }
    }
}