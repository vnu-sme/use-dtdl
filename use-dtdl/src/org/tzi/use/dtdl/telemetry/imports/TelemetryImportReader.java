package org.tzi.use.dtdl.telemetry.imports;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public final class TelemetryImportReader {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TelemetryImportReader() {}

    public static TelemetryImportSpec read(File file) throws IOException {
        return read(file.toPath());
    }

    public static TelemetryImportSpec read(Path path) throws IOException {
        TelemetryImportSpec spec = MAPPER.readValue(path.toFile(), TelemetryImportSpec.class);
        normalize(spec);
        return spec;
    }

    private static void normalize(TelemetryImportSpec spec) {
        if (spec == null || spec.adapters == null) return;

        for (AdapterImportSpec a : spec.adapters) {
            if (a == null) continue;

            if (a.method == null || a.method.isBlank()) {
                a.method = "GET";
            } else {
                a.method = a.method.trim().toUpperCase();
            }

            if (a.intervalMs < 100) {
                a.intervalMs = 2000; // avoid calling window too tight
            }

            if (a.bindings == null) {
                a.bindings = new java.util.ArrayList<>();
            }

            for (BindingImportSpec b : a.bindings) {
                if (b == null) continue;
                if (b.fieldPaths == null) {
                    b.fieldPaths = new java.util.LinkedHashMap<>();
                }
            }
        }
    }
}