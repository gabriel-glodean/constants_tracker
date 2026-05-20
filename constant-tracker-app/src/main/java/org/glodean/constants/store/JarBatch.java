package org.glodean.constants.store;

import java.util.List;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.model.UnitDescriptor;

/**
 * Carries one chunk of extracted class/config files together with the descriptor of
 * their containing JAR (or the outer fat JAR treated as a container).
 *
 * <p>The storage layer uses {@code firstBatch} to decide whether to purge old
 * snapshots for this container before inserting fresh ones. Every JAR produces at
 * least one batch; large JARs produce multiple consecutive batches that all share
 * the same {@code containerDescriptor}.
 *
 * @param containerDescriptor the JAR-level descriptor (path = JAR filename, kind = JAR)
 * @param units               class/config files extracted from this chunk of the JAR
 * @param firstBatch          {@code true} only for the first chunk emitted for a given container
 */
public record JarBatch(
    UnitDescriptor containerDescriptor,
    List<UnitConstants> units,
    boolean firstBatch) {}

