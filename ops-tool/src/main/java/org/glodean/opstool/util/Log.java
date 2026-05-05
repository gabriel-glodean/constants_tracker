package org.glodean.opstool.util;

import java.time.Instant;

/**
 * Zero-dependency, zero-reflection console logger for the ops-tool native binary.
 * Replaces Log4j2 to avoid GraalVM reflection issues.
 * Supports SLF4J-style {} placeholders.
 */
public final class Log {

    private final String name;

    private Log(String name) {
        this.name = name;
    }

    public static Log getLogger(Class<?> clazz) {
        return new Log(clazz.getSimpleName());
    }

    public void info(String msg, Object... args) {
        print("INFO ", msg, args);
    }

    public void debug(String msg, Object... args) {
        print("DEBUG", msg, args);
    }

    public void warn(String msg, Object... args) {
        print("WARN ", msg, args);
    }

    public void error(String msg, Object... args) {
        System.err.println(format("ERROR", msg, args));
    }

    private void print(String level, String msg, Object[] args) {
        System.out.println(format(level, msg, args));
    }

    private String format(String level, String msg, Object[] args) {
        String rendered = render(msg, args);
        return Instant.now() + " " + level + " [" + name + "] " + rendered;
    }

    /** Replaces {} placeholders left-to-right with args.toString(). */
    private static String render(String msg, Object[] args) {
        if (args == null || args.length == 0) return msg;
        StringBuilder sb = new StringBuilder();
        int argIdx = 0;
        int i = 0;
        while (i < msg.length()) {
            if (i < msg.length() - 1 && msg.charAt(i) == '{' && msg.charAt(i + 1) == '}') {
                if (argIdx < args.length) {
                    Object arg = args[argIdx++];
                    sb.append(arg == null ? "null" : arg);
                } else {
                    sb.append("{}");
                }
                i += 2;
            } else {
                sb.append(msg.charAt(i));
                i++;
            }
        }
        return sb.toString();
    }
}

