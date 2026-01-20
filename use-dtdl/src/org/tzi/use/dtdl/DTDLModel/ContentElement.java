package org.tzi.use.dtdl.DTDLModel;

public class ContentElement extends Element {
    protected String name;

    public String getName() {
        return name;
    }

    @Override
    public void prints() {
        prints(0);
    }

    public void prints(int indent) {
        String ind = indent(indent);
        System.out.println(ind + this.getClass().getSimpleName() + ":");
        super.prints(indent + 2);
        System.out.println(ind + "  name: " + safe(name));
    }
}
