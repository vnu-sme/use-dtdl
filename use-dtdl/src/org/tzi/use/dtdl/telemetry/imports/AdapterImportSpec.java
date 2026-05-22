package org.tzi.use.dtdl.telemetry.imports;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AdapterImportSpec {
    public String id;
    public String url;
    public String method = "GET";
    public long intervalMs = 2000;
    public String deviceId;
    public String objectName;
    public List<BindingImportSpec> bindings = new ArrayList<>();
}