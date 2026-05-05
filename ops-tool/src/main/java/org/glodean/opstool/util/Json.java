package org.glodean.opstool.util;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/** Minimal JSON helpers -- no external library, no reflection. */
public final class Json {
    private Json() {}
    // -- building --
    /** Escapes a value for safe embedding inside a JSON string literal. */
    public static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
    // -- parsing --
    /**
     * Returns the value of the first "key": "value" occurrence in json, or null if not found.
     */
    public static String extractString(String json, String key) {
        Matcher m =
                Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"")
                        .matcher(json);
        return m.find() ? m.group(1) : null;
    }
    /**
     * Returns the value of the first "key": integer occurrence in json, or defaultValue if not found.
     */
    public static int extractInt(String json, String key, int defaultValue) {
        Matcher m =
                Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)").matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : defaultValue;
    }
}