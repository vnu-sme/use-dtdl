package org.tzi.use.dtdl.runtime.telemetry;

import java.io.*;
import java.time.Instant;
import java.util.Objects;

/** Very small file-backed dead-letter writer. Appends line-per-message in a readable format. */
public final class DeadLetterSink {
    private final File file;

    public DeadLetterSink(String path) {
        Objects.requireNonNull(path);
        this.file = new File(path);
    }

    public synchronized void write(TelemetryMessage m) throws IOException {
        try (FileWriter fw = new FileWriter(file, true);
             BufferedWriter bw = new BufferedWriter(fw)) {
            bw.write(Instant.now().toString());
            bw.write(" | seq=");
            bw.write(String.valueOf(m.sequence));
            bw.write(" | dtmi=");
            bw.write(String.valueOf(m.dtmi));
            bw.write(" | instanceId=");
            bw.write(String.valueOf(m.instanceId));
            bw.write(" | telemetry=");
            bw.write(m.telemetryName);
            bw.write(" | value=");
            bw.write(String.valueOf(m.value));
            bw.write(" | meta=");
            bw.write(String.valueOf(m.meta));
            bw.newLine();
        }
    }
}