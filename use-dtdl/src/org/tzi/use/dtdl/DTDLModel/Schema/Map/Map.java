package org.tzi.use.dtdl.DTDLModel.Schema.Map;

import org.tzi.use.dtdl.DTDLModel.Schema.ComplexSchema;

public class Map extends ComplexSchema {
    private MapKey mapKey;
    private MapValue mapValue;

    public void setMapKey(MapKey k) { this.mapKey = k; }
    public void setMapValue(MapValue v) { this.mapValue = v; }
    public MapKey getMapKey() { return mapKey; }
    public MapValue getMapValue() { return mapValue; }

    // Map
    @Override
    public void prints(int indent) {
        String ind = indent(indent);
        System.out.println(ind + "Map:");
        if (getMapKey() != null) {
            System.out.println(ind + "  mapKey: " + safe(getMapKey().getName()));
            getMapKey().prints(indent + 4);
        } else {
            System.out.println(ind + "  mapKey: (null)");
        }
        if (getMapValue() != null) {
            System.out.println(ind + "  mapValue:");
            getMapValue().prints(indent + 4);
        } else {
            System.out.println(ind + "  mapValue: (null)");
        }
    }
}

