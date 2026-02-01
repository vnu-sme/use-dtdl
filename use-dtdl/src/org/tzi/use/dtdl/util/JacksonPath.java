package org.tzi.use.dtdl.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JacksonPath {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JacksonPath() {}

    public static Object extract(String json, String dotPath) {
        try {
            JsonNode root = MAPPER.readTree(json);

            if (dotPath == null || dotPath.isBlank()) {
                return root.toString();
            }

            String pointer = toJsonPointer(dotPath);
            JsonNode node = root.at(pointer);

            if (node.isMissingNode() || node.isNull()) {
                return null;
            }
            if (node.isNumber()) {
                return node.numberValue();
            }
            if (node.isBoolean()) {
                return node.booleanValue();
            }
            if (node.isTextual()) {
                return node.textValue();
            }
            return node.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String toJsonPointer(String dotPath) {
        StringBuilder sb = new StringBuilder();
        String[] parts = dotPath.split("\\.");
        for (String p : parts) {
            int br = p.indexOf('[');
            if (br < 0) {
                sb.append('/').append(p);
            } else {
                sb.append('/').append(p, 0, br);
                int br2 = p.indexOf(']', br);
                if (br2 > br) {
                    sb.append('/').append(p, br + 1, br2);
                }
            }
        }
        return sb.toString();
    }
}