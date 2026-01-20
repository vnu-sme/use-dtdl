package org.tzi.use.dtdl.runtime.telemetry.adapters;

import org.tzi.use.dtdl.runtime.telemetry.TelemetryAdapter;
import org.tzi.use.dtdl.runtime.telemetry.TelemetryHub;
import org.tzi.use.dtdl.runtime.telemetry.TelemetryMessage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;


// Example: a simple polling adapter that fetches a JSON endpoint periodically and pushes telemetry messages.
// This is a skeleton — adapt parsing/field extraction to chosen API/library.
public class PollingHttpAdapter implements TelemetryAdapter {
    private final String name;
    private final URI endpoint;
    private final ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "poll-"+(int)(Math.random()*1000)));
    private TelemetryHub hub;
    private final HttpClient http = HttpClient.newHttpClient();
    private final long periodSeconds;

    public PollingHttpAdapter(String name, URI endpoint, long periodSeconds) {
        this.name = name;
        this.endpoint = endpoint;
        this.periodSeconds = Math.max(1L, periodSeconds);
    }

    @Override
    public void setHub(TelemetryHub hub) { this.hub = hub; }

    @Override
    public String getName() { return name; }

    @Override
    public void start() {
        sched.scheduleWithFixedDelay(this::pollOnce, 0, periodSeconds, TimeUnit.SECONDS);
    }

    @Override
    public void stop() {
        sched.shutdownNow();
    }

    private void pollOnce() {
        try {
            HttpRequest req = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                String body = resp.body();
                // TODO: parse JSON and map fields to TelemetryMessage instances
                // Example pseudo:
                // List<TelemetryMessage> msgs = parse(body);
                // for (TelemetryMessage m : msgs) hub.ingest(m);

                // For quick testing you can emit a synthetic message:
                // hub.ingest(new TelemetryMessage(null, "dtmi:com:example:Room;1", "environmentState", Map.of("temperature", 22.3), Map.of("source", name)));
            } else {
                System.err.println("PollingHttpAdapter " + name + " non-2xx: " + resp.statusCode());
            }
        } catch (Throwable ex) {
            System.err.println("PollingHttpAdapter " + name + " poll error: " + ex.getMessage());
        }
    }
}