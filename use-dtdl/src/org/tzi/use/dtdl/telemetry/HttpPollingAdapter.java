package org.tzi.use.dtdl.telemetry;

import org.tzi.use.dtdl.util.JacksonPath;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Polls an HTTP endpoint periodically and emits TelemetryEvent for each response.
 *
 * Configuration:
 *  - url: HTTP GET URL
 *  - intervalMs: polling interval in milliseconds (>= 100)
 *  - dtmi, telemetryName, deviceId, objectName: metadata used when building TelemetryEvent
 *  - valuePath: dot-separated path into JSON response to extract value (if response is JSON). If null, try to treat entire body as scalar.
 *
 */
public final class HttpPollingAdapter implements TelemetryAdapter {

    private final String id;
    private final String url;
    private final long intervalMs;
    private final String dtmi;
    private final String telemetryName;
    private final String deviceId;
    private final String objectName;
    private final String valuePath;

    private final HttpClient client;
    private final ScheduledExecutorService scheduler;
    private final AtomicReference<Consumer<TelemetryEvent>> handler = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> running = new AtomicReference<>();

    public HttpPollingAdapter(String id, String url, long intervalMs, String dtmi, String telemetryName,
                              String deviceId, String objectName, String valuePath) {
        this.id = Objects.requireNonNull(id, "id");
        this.url = Objects.requireNonNull(url, "url");
        if (intervalMs < 100) throw new IllegalArgumentException("intervalMs too small");

        System.err.println(
                "[HttpPollingAdapter:init] id=" + id + " url=" + url + " dtmi=" + dtmi + " telemetry=" + telemetryName +
                        " deviceId=" + deviceId + " objectName=" + objectName + " rawValuePath='" + valuePath + "'");

        this.intervalMs = intervalMs;
        this.dtmi = dtmi;
        this.telemetryName = telemetryName;
        this.deviceId = deviceId;
        this.objectName = objectName;
        this.valuePath = valuePath;

        this.client = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(5))
                .build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "http-poll-" + id);
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void start(Consumer<TelemetryEvent> handler) {
        this.handler.set(Objects.requireNonNull(handler));
        // schedule polling task
        ScheduledFuture<?> prev = running.get();
        if (prev != null && !prev.isDone()) return;
        ScheduledFuture<?> fut = scheduler.scheduleAtFixedRate(this::pollOnce, 0, intervalMs, TimeUnit.MILLISECONDS);
        running.set(fut);
    }

    private void pollOnce() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .GET()
                    .uri(URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .build();

            System.err.println("[HTTP] GET " + url);

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            System.err.println("[HTTP] Response " + resp.statusCode() + " from " + url);

            String body = resp.body();
            Object extracted = null;
            if (body != null) {
                String trimmed = body.trim();
                if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                    // JSON-ish -> try dot-path extraction
                    if (valuePath != null && !valuePath.isBlank()) {
                        extracted = JacksonPath.extract(trimmed, valuePath);
                        System.err.println("[ADAPTER] About to extract valuePath='" + valuePath +
                                "' from JSON body length=" + trimmed.length());
                    } else {
                        // fallback: the whole body
                        extracted = trimmed;
                    }
                } else {
                    // plain scalar
                    extracted = trimmed;
                }
            }
            System.err.println("[ADAPTER] Extracted value=" + extracted +
                    " telemetry=" + telemetryName +
                    " dtmi=" + dtmi);

            TelemetryEvent ev = new TelemetryEvent(dtmi, deviceId, objectName, telemetryName, extracted, Instant.now(), id, Map.of("httpStatus", resp.statusCode(), "url", url));
            Consumer<TelemetryEvent> h = handler.get();
            if (h != null) h.accept(ev);
        } catch (IOException | InterruptedException ex) {
            // network errors: still emit a diagnostic TelemetryEvent with meta containing exception message (optional)
            TelemetryEvent ev = new TelemetryEvent(dtmi, deviceId, objectName, telemetryName, null, Instant.now(), id, Map.of("error", ex.getMessage(), "url", url));
            Consumer<TelemetryEvent> h = handler.get();
            if (h != null) h.accept(ev);
            // restore interrupt state
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
        } catch (Throwable t) {
            System.err.println("[HttpPollingAdapter:" + id + "] poll error: " + t.getMessage());
            t.printStackTrace(System.err);
        }
    }

    @Override
    public void close() {
        ScheduledFuture<?> f = running.getAndSet(null);
        if (f != null) f.cancel(true);
        try {
            scheduler.shutdownNow();
        } catch (Throwable ignored) {}
        handler.set(null);
    }

    @Override
    public String id() {
        return id;
    }

    public String url() {
        return url;
    }

    public long intervalMs() {
        return intervalMs;
    }

    public String dtmi() {
        return dtmi;
    }

    public String telemetryName() {
        return telemetryName;
    }

    public String deviceId() {
        return deviceId;
    }

    public String objectName() {
        return objectName;
    }

    public String valuePath() {
        return valuePath;
    }
}
