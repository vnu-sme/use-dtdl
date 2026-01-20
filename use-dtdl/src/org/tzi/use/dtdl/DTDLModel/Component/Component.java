package org.tzi.use.dtdl.DTDLModel.Component;

import org.tzi.use.dtdl.DTDLModel.ContentElement;
import org.tzi.use.dtdl.DTDLModel.Interface;

public class Component extends ContentElement {
    private Interface schemaInterface;

    public Component(String id) { this.id = id; this.type = "Component"; }

    public void setName(String name) { this.name = name; }

    public void setSchemaInterface(Interface dtmi) { this.schemaInterface = dtmi; }

    public Interface getSchemaInterface() { return schemaInterface; }

    @Override
    public void prints() { prints(0); }

    public void prints(int indent) {
        String ind = indent(indent);
        System.out.println(ind + "Component:");
        super.prints(indent + 2);
        System.out.println(ind + "  name: " + safe(name));
        System.out.println(ind + "  schemaInterface: " + (schemaInterface != null ? schemaInterface.getId() : "(null)"));
    }
}
