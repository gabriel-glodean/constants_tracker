package org.glodean.constants.model;

import java.util.Objects;

/**
 * Describes the artifact/source from which constants were extracted.
 *
 * Examples:
 * - origin = "file", path = "src/main/resources/config.yml"
 * - origin = "jar",  path = "com/example/Foo.class"
 */
public record UnitDescriptor(String origin, String path) {
    public UnitDescriptor {
        if (origin == null || origin.trim().isEmpty()) {
            throw new IllegalArgumentException("origin cannot be null or empty");
        }
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("path cannot be null or empty");
        }
        Objects.requireNonNull(origin);
        Objects.requireNonNull(path);
    }

    @Override
    public String toString() {
        return origin + ":" + path;
    }
}

