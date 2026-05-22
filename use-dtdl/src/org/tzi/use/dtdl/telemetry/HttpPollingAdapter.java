package org.tzi.use.dtdl.telemetry;

import org.tzi.use.dtdl.util.JacksonPath;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
    private final String deviceId;
    private final String objectName;
    private final String method;

    private final HttpClient client;
    private final ScheduledExecutorService scheduler;
    private final AtomicReference<Consumer<TelemetryEvent>> handler = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> running = new AtomicReference<>();

    public HttpPollingAdapter(String id, String url, String method, long intervalMs, String deviceId, String objectName) {
        this.method = method == null ? "GET" : method.toUpperCase();
        this.id = Objects.requireNonNull(id, "id");
        this.url = Objects.requireNonNull(url, "url");
        if (intervalMs < 100) throw new IllegalArgumentException("intervalMs too small");

        System.err.println(
                "[HttpPollingAdapter:init] id=" + id + " url=" + url +
                        " deviceId=" + deviceId + " objectName=" + objectName);

        this.intervalMs = intervalMs;
        this.deviceId = deviceId;
        this.objectName = objectName;

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
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10));

            if ("POST".equalsIgnoreCase(method)) {
                builder.POST(HttpRequest.BodyPublishers.noBody());
            } else {
                builder.GET();
            }

            HttpRequest req = builder.build();

            System.err.println("[HTTP] " + url);

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            System.err.println("[HTTP] Response " + resp.statusCode() + " from " + url);

            String body = resp.body();
            Object extracted = null;
            if (body != null) {
                String trimmed = body.trim();
                if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                    extracted = trimmed;
                }
            }
            System.err.println("[ADAPTER] Extracted value=" + (extracted != null ? "<json/scalar>" : extracted) +
                    " dtmi=<adapter-none> source=" + id);

            TelemetryEvent ev = new TelemetryEvent(null, deviceId, objectName, extracted, Instant.now(), id, Map.of("httpStatus", resp.statusCode(), "url", url));
            Consumer<TelemetryEvent> h = handler.get();
            if (h != null) h.accept(ev);
        } catch (IOException | InterruptedException ex) {
            // network errors: still emit a diagnostic TelemetryEvent with meta containing exception message (optional)
            TelemetryEvent ev = new TelemetryEvent(null, deviceId, objectName, null, Instant.now(), id, Map.of("error", ex.getMessage(), "url", url));
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

    public String deviceId() {
        return deviceId;
    }

    public String objectName() {
        return objectName;
    }
}
