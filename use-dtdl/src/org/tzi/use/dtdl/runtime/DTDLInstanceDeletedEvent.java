package org.tzi.use.dtdl.runtime;

public final class DTDLInstanceDeletedEvent {
    public final DTDLInstance instance;

    public DTDLInstanceDeletedEvent(DTDLInstance i){
        this.instance = i;
    }
}
