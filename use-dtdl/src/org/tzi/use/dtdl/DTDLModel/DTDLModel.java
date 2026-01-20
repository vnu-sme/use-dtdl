package org.tzi.use.dtdl.DTDLModel;

import java.util.LinkedHashMap;
import java.util.Map;

public class DTDLModel {
    private final Map<String, Interface> interfaces = new LinkedHashMap<>();

    public void addInterface(Interface iface) {
        interfaces.put(iface.getId(), iface);
    }

    public Interface getInterface(String id) {
        return interfaces.get(id);
    }

    public Map<String, Interface> getInterfaces() {
        return interfaces;
    }

    public Interface findInterfaceById(String id) {
        if (id == null) return null;
        return interfaces.get(id);
    }

    public void prints() {
        if (interfaces.isEmpty()) {
            System.out.println("DTDLModel: (no interfaces)");
            return;
        }
        System.out.println("DTDLModel:");
        for (Interface iface : interfaces.values()) {
            System.out.println();
            iface.prints(2);
        }
    }
}