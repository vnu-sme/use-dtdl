package org.tzi.use.dtdl.runtime;

public final class DTDLCommandResponseEvent {
    public final DTDLInstance instance;
    public final String commandName;
    public final Object result;
    public final Throwable error;

    public DTDLCommandResponseEvent(DTDLInstance i, String n, Object r, Throwable e){
        instance=i;
        commandName=n;
        result=r;
        error=e;
    }
}
