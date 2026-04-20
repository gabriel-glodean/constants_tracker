package org.glodean.constants.model;

import java.util.Objects;
import java.util.Set;

/**
 * Represents the set of constants discovered for a single unit (class file, config file, jar entry, etc.).
 *
 * This is a more generic replacement/companion to the existing {@code ClassConstants} and can be used
 * by extractors that operate on different resource types.
 *
 * @param source     descriptor describing where this unit was found
 * @param unitType   the type of the unit (class, config file, ...)
 * @param constants  set of discovered constants and their usage metadata
 */
public record UnitConstants(UnitDescriptor source, UnitType unitType, Set<ClassConstant> constants) {
    public UnitConstants {
        Objects.requireNonNull(source, "source cannot be null");
        Objects.requireNonNull(unitType, "unitType cannot be null");
        Objects.requireNonNull(constants, "constants set cannot be null");

        if (constants.isEmpty()) {
            // empty set is allowed, but log or validate elsewhere if needed
        }
    }

    /**
     * Convenience: return a human-friendly source name (origin:path).
     */
    public String sourceName() {
        return source.toString();
    }
}

