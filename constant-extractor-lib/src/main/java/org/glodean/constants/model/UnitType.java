package org.glodean.constants.model;

/**
 * Extensible marker interface representing the type of unit being analyzed.
 *
 * Implementations may be enums (for core types) or custom classes for plugin types.
 */
public interface UnitType {
    /**
     * Canonical name of the unit type. For enums this can simply be name().
     */
    String name();
}

