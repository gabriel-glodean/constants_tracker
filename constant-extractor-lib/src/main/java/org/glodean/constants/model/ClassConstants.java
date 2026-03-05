package org.glodean.constants.model;

import java.util.Objects;
import java.util.Set;

/**
 * Represents the set of constants discovered for a single class.
 *
 * @param name      internal class name (slash-separated)
 * @param constants set of discovered constants and their usage metadata
 */
public record ClassConstants(String name, Set<ClassConstant> constants) {
    public ClassConstants {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Class name cannot be null or empty");
        }
        Objects.requireNonNull(constants, "Constants set cannot be null");
    }
}
