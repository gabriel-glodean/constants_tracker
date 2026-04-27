package org.glodean.constants.model;

import java.util.Objects;

/**
 * Describes the artifact/source from which constants were extracted.
 *
 * <p>The {@link #sourceKind()} field provides a typed classification of the resource
 * (class file, JAR, directory, etc.) so that version-to-version diffs can reliably
 * detect added, changed, or <b>removed</b> files.</p>
 *
 * <p>The {@link #sizeBytes()} records the size of the resource in bytes, useful for
 * quick sanity checks and change-detection heuristics.</p>
 *
 * <p>The optional {@link #contentHash()} (e.g.&nbsp;SHA-256 hex) enables cheap
 * change detection without re-extracting: if two versions share the same
 * {@code sourceKind + path + sizeBytes + contentHash}, the file is unchanged.</p>
 *
 * @param sourceKind  typed classification of the resource
 * @param path        path (relative or absolute) to the resource
 * @param sizeBytes   size of the resource in bytes ({@code -1} when unknown)
 * @param contentHash optional content hash for change detection (may be {@code null})
 */
public record UnitDescriptor(SourceKind sourceKind, String path, long sizeBytes, String contentHash) {
    public UnitDescriptor {
        Objects.requireNonNull(sourceKind, "sourceKind cannot be null");
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("path cannot be null or empty");
        }
        // sizeBytes may be -1 when unknown; contentHash is nullable
    }

    /**
     * Convenience constructor without size or content hash.
     */
    public UnitDescriptor(SourceKind sourceKind, String path) {
        this(sourceKind, path, -1L, null);
    }

    /**
     * Convenience constructor with size but no content hash.
     */
    public UnitDescriptor(SourceKind sourceKind, String path, long sizeBytes) {
        this(sourceKind, path, sizeBytes, null);
    }

    @Override
    public String toString() {
        return sourceKind.name() + ":" + path;
    }
}
