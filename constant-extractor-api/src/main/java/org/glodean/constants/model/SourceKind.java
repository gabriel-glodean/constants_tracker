package org.glodean.constants.model;

/**
 * Extensible marker interface representing the kind of resource from which constants
 * were extracted (e.g., class file, JAR, YAML config).
 *
 * <p>Each extractor module provides its own implementation (typically an enum).
 * This keeps the API module free of extractor-specific knowledge while still
 * giving {@link UnitDescriptor} a typed, comparable origin field.</p>
 *
 * <p>Implementations should be enums or records with a meaningful {@link #name()}.
 */
public interface SourceKind {
    /**
     * Canonical name of the source kind. For enums this is typically {@code name()}.
     */
    String name();
}
