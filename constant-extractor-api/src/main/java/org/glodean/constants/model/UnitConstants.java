package org.glodean.constants.model;

import java.util.Objects;
import java.util.Set;

/**
 * Represents the set of constants discovered for a single unit (class file, config file, jar entry, etc.).
 *
 * This is a more generic replacement/companion to the legacy {@code ClassConstants} and can be used
 * by extractors that operate on different resource types.
 *
 * @param source     descriptor describing where this unit was found
 * @param constants  set of discovered constants and their usage metadata
 */
public record UnitConstants(UnitDescriptor source, Set<UnitConstant> constants) {
    public UnitConstants {
        Objects.requireNonNull(source, "source cannot be null");
        Objects.requireNonNull(constants, "constants set cannot be null");

        // empty set is allowed, but validate or log elsewhere if needed
    }

    /**
     * Convenience: return a human-friendly source name (origin:path).
     */
    public String getSourceName() {
        return source.toString();
    }
}
