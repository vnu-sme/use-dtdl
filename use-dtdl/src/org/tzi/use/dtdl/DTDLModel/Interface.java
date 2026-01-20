package org.tzi.use.dtdl.DTDLModel;

import org.tzi.use.dtdl.DTDLModel.Schema.Schema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Interface extends Element {
    private String context; // should be IRI
    private List<ContentElement> contents;
    private List<Interface> extendsInterfaces;
    private final Map<String, Schema> schemas = new LinkedHashMap<>();

    public Interface(String id) {
        this.id = id;
        this.type = "Interface";
        this.contents = new ArrayList<>();
        this.extendsInterfaces = new ArrayList<>();
    }

    public void setContext(String context) {
        this.context = context;
    }

    public String getContext() {
        return context;
    }

    public void addExtends(Interface dtmi) {
        extendsInterfaces.add(dtmi);
    }

    public List<Interface> getExtends() {
        return extendsInterfaces;
    }

    public void addContent(ContentElement e) {
        contents.add(e);
    }

    public List<ContentElement> getContents() {
        return contents;
    }

    public void addSchema(String id, Schema s) {
        schemas.put(id, s);
    }

    public Map<String, Schema> getSchemas() {
        return schemas;
    }

    @Override
    public void prints() { prints(0); }

    @Override
    public void prints(int indent) {
        String ind = indent(indent);
        System.out.println(ind + "Interface:");
        super.prints(indent + 2);
        System.out.println(ind + "  context: " + safe(context));

        // schemas
        if (schemas.isEmpty()) {
            System.out.println(ind + "  Schemas: (none)");
        } else {
            System.out.println(ind + "  Schemas:");
            for (java.util.Map.Entry<String, Schema> e : schemas.entrySet()) {
                System.out.println(ind + "    id: " + safe(e.getKey()));
                Schema s = e.getValue();
                if (s != null) s.prints(indent + 6);
                else System.out.println(ind + "      (null)");
            }
        }

        // contents
        if (contents.isEmpty()) {
            System.out.println(ind + "  Contents: (none)");
        } else {
            System.out.println(ind + "  Contents:");
            for (ContentElement c : contents) {
                if (c != null) c.prints(indent + 4);
                else System.out.println(ind + "    (null content)");
            }
        }
    }
}
