package org.tzi.use.dtdl.runtime;

import java.util.Map;

public final class DTDLCommandInvokedEvent {
    public final DTDLInstance instance;
    public final String commandName;
    public final Map<String,Object> args;

    public DTDLCommandInvokedEvent(DTDLInstance i, String n, Map<String,Object> a){
        instance=i;
        commandName=n;
        args=a;
    }
}
