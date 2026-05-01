package org.tzi.use.dtdl.util.telemetry;

import org.tzi.use.dtdl.telemetry.TelemetryFact;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RobustWindowGate {

    public static final class Decision {
        public final boolean allow; // can update or not
        public final boolean suspicious; // anomaly detected
        public final boolean confirmed; // anomaly persisted
        public final String message;

        public Decision(boolean allow, boolean suspicious, boolean confirmed, String message) {
            this.allow = allow;
            this.suspicious = suspicious;
            this.confirmed = confirmed;
            this.message = message;
        }
    }

    private static final class Sample {
        final Instant ts;
        final double value;

        Sample(Instant ts, double value) {
            this.ts = ts;
            this.value = value;
        }
    }

    // Sliding window + anomaly streak tracking
    private static final class StreamState {
        final Deque<Sample> window = new ArrayDeque<>();
        int consecutiveSuspicious = 0;
    }

    private static final class FlatValue {
        final String key;
        final double value;

        FlatValue(String key, double value) {
            this.key = key;
            this.value = value;
        }
    }

    private final Map<String, StreamState> states = new HashMap<>();
    private final int maxSamples;
    private final int minSamplesToCheck;
    private final double zThreshold;
    private final int confirmConsecutive;
    private final long maxAgeMillis;

    public RobustWindowGate(int maxSamples, int minSamplesToCheck, double zThreshold, int confirmConsecutive, long maxAgeMillis) {
        this.maxSamples = Math.max(3, maxSamples);
        this.minSamplesToCheck = Math.max(3, minSamplesToCheck);
        this.zThreshold = zThreshold <= 0 ? 3.5 : zThreshold;
        this.confirmConsecutive = Math.max(2, confirmConsecutive);
        this.maxAgeMillis = maxAgeMillis;
    }

    public synchronized Decision inspect(TelemetryFact fact) {
        if (fact == null) {
            return new Decision(true, false, false, null);
        }

        Object normalized = fact.normalizedValue;
        if (normalized == null) {
            return new Decision(true, false, false, null);
        }

        List<FlatValue> values = new ArrayList<>();
        String baseKey = baseKey(fact);
        flatten(values, baseKey, normalized);

        if (values.isEmpty()) {
            return new Decision(true, false, false, null);
        }

        Instant ts = fact.timestamp != null ? fact.timestamp : Instant.now();

        for (FlatValue fv : values) {
            Decision d = inspectValue(fv.key, fv.value, ts);
            if (!d.allow) {
                return d;
            }
        }

        return new Decision(true, false, false, null);
    }

    private Decision inspectValue(String key, double value, Instant ts) {
        StreamState state = states.computeIfAbsent(key, k -> new StreamState());
        prune(state, ts);

        if (state.window.size() < minSamplesToCheck) {
            System.out.println("[GATE][WARMUP] key=" + key + " size=" + state.window.size() + "/" + minSamplesToCheck + " value=" + value);

            state.window.addLast(new Sample(ts, value));
            trim(state);
            state.consecutiveSuspicious = 0;
            return new Decision(true, false, false,
                    "[robust-window] warming up " + key + " (" + state.window.size() + "/" + minSamplesToCheck + ")");
        }

        List<Double> previous = new ArrayList<>(state.window.size());
        for (Sample s : state.window) {
            previous.add(s.value);
        }

        double median = SlidingWindowStats.median(previous);
        double mad = SlidingWindowStats.mad(previous, median);
        double z = SlidingWindowStats.robustZ(value, median, mad);

        System.out.println("[GATE][STATS] key=" + key + " value=" + value + " median=" + median + " mad=" + mad + " z=" + z);

        boolean suspicious = !Double.isNaN(z) && z > zThreshold;

        state.window.addLast(new Sample(ts, value));
        trim(state);

        if (!suspicious) {
            System.out.println("[GATE][NORMAL] key=" + key + " value=" + value);
            state.consecutiveSuspicious = 0;
            return new Decision(true, false, false, null);
        }

        state.consecutiveSuspicious++;

        String msg = "[robust-window] suspicious " + key
                + " value=" + value
                + " median=" + median
                + " mad=" + mad
                + " z=" + z
                + " consecutive=" + state.consecutiveSuspicious + "/" + confirmConsecutive;

        if (state.consecutiveSuspicious < confirmConsecutive) {
            System.out.println("[GATE][SUSPICIOUS] key=" + key + " value=" + value + " consecutive=" + state.consecutiveSuspicious + "/" + confirmConsecutive);
            return new Decision(false, true, false, msg);
        }

        System.out.println("[GATE][CONFIRMED] key=" + key + " value=" + value + " consecutive=" + state.consecutiveSuspicious);
        return new Decision(true, true, true, msg + " [confirmed]");
    }

    private void prune(StreamState state, Instant now) {
        if (state.window.isEmpty()) return;

        if (maxAgeMillis > 0 && now != null) {
            while (!state.window.isEmpty()) {
                Sample first = state.window.peekFirst();
                if (first == null || first.ts == null) break;
                long age = now.toEpochMilli() - first.ts.toEpochMilli();
                if (age <= maxAgeMillis) break;
                state.window.removeFirst();
            }
        }

        trim(state);
    }

    private void trim(StreamState state) {
        while (state.window.size() > maxSamples) {
            state.window.removeFirst();
        }
    }

    private String baseKey(TelemetryFact fact) {
        StringBuilder sb = new StringBuilder();
        sb.append(fact.interfaceId != null ? fact.interfaceId : "<iface>");
        sb.append('|').append(fact.matchedObjectName != null ? fact.matchedObjectName : "<object>");
        sb.append('|').append(fact.telemetryName != null ? fact.telemetryName : "<telemetry>");
        return sb.toString();
    }

    private void flatten(List<FlatValue> out, String prefix, Object value) {
        if (value == null) return;

        Double num = SlidingWindowStats.tryParseNumber(value);
        if (num != null) {
            out.add(new FlatValue(prefix, num));
            return;
        }

        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> en : map.entrySet()) {
                String k = String.valueOf(en.getKey());
                flatten(out, prefix + "." + k, en.getValue());
            }
            return;
        }

        if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                flatten(out, prefix + "[" + i + "]", list.get(i));
            }
        }
    }
}