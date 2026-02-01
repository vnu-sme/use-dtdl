package org.tzi.use.dtdl.telemetry;


import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;


/**
 * Simple in-process EventBus with single consumer thread.
 */
public final class EventBus {
    private final BlockingQueue<TelemetryEvent> q = new LinkedBlockingQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Thread worker;


    public EventBus(Consumer<TelemetryEvent> processor) {
        this.worker = new Thread(() -> {
            // q got a new event
            while (running.get() || !q.isEmpty()) {
                try {
                    TelemetryEvent ev = q.take();
                    System.err.println("[BUS] Dispatching event to processor: " + ev);

                    try {
                        processor.accept(ev);
                    } catch (Throwable t) {
                        System.err.println("[EventBus] processor error: " + t.getMessage());
                        t.printStackTrace(System.err);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "dtdl-telemetry-eventbus");
        this.worker.setDaemon(true);
    }


    public void start() {
        if (running.compareAndSet(false, true)) worker.start();
    }


    public void stop() {
        running.set(false);
        worker.interrupt();
    }


    public void post(TelemetryEvent e) {
        System.err.println("[BUS] Event enqueued: " + e);

        if (!running.get()) throw new IllegalStateException("EventBus not started");
        // push to queue
        q.offer(e);
    }
}