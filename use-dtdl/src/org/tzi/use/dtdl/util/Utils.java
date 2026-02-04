package org.tzi.use.dtdl.util;

public final class Utils {

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
}