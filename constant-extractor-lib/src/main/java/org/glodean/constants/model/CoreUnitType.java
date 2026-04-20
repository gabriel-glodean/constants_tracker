package org.glodean.constants.model;

/**
 * Built-in unit types. Implement {@link UnitType} so callers can switch on known values
 * while allowing custom UnitType implementations.
 */
public enum CoreUnitType implements UnitType {
    CLASS,
    CONFIG_FILE,
    JAR,
    DIRECTORY,
    UNKNOWN;

    @Override
    public String name() {
        return toString();
    }
}

