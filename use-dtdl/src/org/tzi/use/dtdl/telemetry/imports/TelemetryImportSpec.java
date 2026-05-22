package org.tzi.use.dtdl.telemetry.imports;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TelemetryImportSpec {
    public List<AdapterImportSpec> adapters = new ArrayList<>();
}