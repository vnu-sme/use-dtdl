package org.tzi.use.dtdl.runtime;

public final class DTDLComponentSetEvent { public final DTDLInstance instance; public final String compName; public final DTDLInstance component; public DTDLComponentSetEvent(DTDLInstance i, String n, DTDLInstance c){ instance=i; compName=n; component=c; } }
