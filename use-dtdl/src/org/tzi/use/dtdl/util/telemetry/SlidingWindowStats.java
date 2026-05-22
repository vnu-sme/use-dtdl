package org.tzi.use.dtdl.util.telemetry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SlidingWindowStats {
    private SlidingWindowStats() {}

    public static double median(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return Double.NaN;
        }

        List<Double> copy = new ArrayList<>(values);
        Collections.sort(copy);

        int n = copy.size();

        // odd returns middle
        if ((n & 1) == 1) {
            return copy.get(n / 2);
        }

        // even returns average of the two
        return (copy.get(n / 2 - 1) + copy.get(n / 2)) / 2.0;
    }

    public static double mad(List<Double> values, double median) {
        if (values == null || values.isEmpty() || Double.isNaN(median)) {
            return Double.NaN;
        }

        List<Double> dev = new ArrayList<>(values.size());
        for (Double v : values) {
            if (v == null || Double.isNaN(v) || Double.isInfinite(v)) {
                continue;
            }
            dev.add(Math.abs(v - median));
        }

        if (dev.isEmpty()) {
            return Double.NaN;
        }

        Collections.sort(dev);

        int n = dev.size();
        if ((n & 1) == 1) {
            return dev.get(n / 2);
        }

        return (dev.get(n / 2 - 1) + dev.get(n / 2)) / 2.0;
    }

    public static double robustZ(double value, double median, double mad) {
        if (Double.isNaN(value) || Double.isNaN(median) || Double.isNaN(mad)) {
            return Double.NaN;
        }
        if (mad <= 1e-12) {
            return Math.abs(value - median) <= 1e-12 ? 0.0 : Double.POSITIVE_INFINITY;
        }
        return Math.abs(value - median) / (1.4826 * mad);
    }

    public static Double tryParseNumber(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number n) return n.doubleValue();

        if (raw instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (Exception ignored) {
                return null;
            }
        }

        return null;
    }
}