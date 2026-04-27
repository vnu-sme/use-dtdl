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
            if (p.isEmpty()) continue;

            while (p.contains("[")) {
                int fieldEnd = p.indexOf('[');
                String field = p.substring(0, fieldEnd);

                if (!field.isEmpty()) {
                    sb.append('/').append(field);
                }

                int start = p.indexOf('[');
                int end = p.indexOf(']');

                String index = p.substring(start + 1, end);
                sb.append('/').append(index);

                p = p.substring(end + 1);
            }

            if (!p.isEmpty()) {
                sb.append('/').append(p);
            }
        }

        return sb.toString();
    }
}