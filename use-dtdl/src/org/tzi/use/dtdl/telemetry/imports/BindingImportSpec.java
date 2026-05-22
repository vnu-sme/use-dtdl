package org.tzi.use.dtdl.telemetry.imports;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BindingImportSpec {
    public String dtmi;
    public String telemetryName;
    public String objectName;
    public String valuePath;
    public Map<String, String> fieldPaths = new LinkedHashMap<>();
}