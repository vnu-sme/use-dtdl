package org.tzi.use.dtdl.util;

import org.tzi.use.dtdl.DTDLModel.Interface;

public final class Utils {

    public static final String TELEMETRY_ATTR_PREFIX = "__tel_";

    private Utils() {
    }

    public static String sanitize(String id) {
        if (id == null) return "Unnamed";
        String s = id.replaceAll("[^A-Za-z0-9_]", "_");
        if (s.isEmpty()) s = "Unnamed";
        if (Character.isDigit(s.charAt(0))) s = "_" + s;
        return s;
    }

    public static String blankToNull(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    public static String getInterfaceDisplayName(Interface iface) {
        if (iface.getDisplayName() != null && !iface.getDisplayName().isEmpty()) {
            return iface.getDisplayName();
        }

        String id = iface.getId();
        if (id == null) return "Unnamed";

        // Extract last segment of DTMI
        int colon = id.lastIndexOf(':');
        int semi = id.indexOf(';');

        if (colon >= 0 && semi > colon) {
            return id.substring(colon + 1, semi);
        }

        return id;
    }

    public static int envInt(String key, int def) {
        try {
            return Integer.parseInt(System.getenv().getOrDefault(key, String.valueOf(def)));
        } catch (Exception e) {
            return def;
        }
    }

    public static double envDouble(String key, double def) {
        try {
            return Double.parseDouble(System.getenv().getOrDefault(key, String.valueOf(def)));
        } catch (Exception e) {
            return def;
        }
    }

    public static long envLong(String key, long def) {
        try {
            return Long.parseLong(System.getenv().getOrDefault(key, String.valueOf(def)));
        } catch (Exception e) {
            return def;
        }
    }
}