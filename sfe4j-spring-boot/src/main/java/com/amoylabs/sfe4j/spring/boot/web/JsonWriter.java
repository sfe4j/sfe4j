package com.amoylabs.sfe4j.spring.boot.web;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal JSON writer for the small payloads SFE4J returns itself.
 *
 * <p>Using the host {@code ObjectMapper} would make the shape of an SFE4J response depend on how the
 * embedding application configures Jackson; these payloads are part of the documented contract.</p>
 */
public final class JsonWriter {

    private JsonWriter() {
    }

    /**
     * @return a JSON object with the given members, in insertion order
     */
    public static String object(Object... keyAndValue) {
        Map<String, String> members = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyAndValue.length; i += 2) {
            members.put(String.valueOf(keyAndValue[i]), string(keyAndValue[i + 1]));
        }
        StringBuilder json = new StringBuilder(64);
        json.append('{');
        boolean first = true;
        for (Map.Entry<String, String> entry : members.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(escape(entry.getKey())).append("\":");
            json.append(entry.getValue());
        }
        json.append('}');
        return json.toString();
    }

    /**
     * @return the JSON representation of a value: a quoted string, a number, a boolean or {@code null}
     */
    public static String string(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Boolean || value instanceof Number) {
            return String.valueOf(value);
        }
        return '"' + escape(String.valueOf(value)) + '"';
    }

    /**
     * Escapes a string so it can never break out of a JSON string, an SSE field or an HTML attribute.
     */
    public static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                case '<' -> escaped.append("\\u003c");
                case '>' -> escaped.append("\\u003e");
                case '&' -> escaped.append("\\u0026");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
