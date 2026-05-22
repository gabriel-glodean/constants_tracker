package org.glodean.constants.store.postgres.repository.projection;

/**
 * Flat projection row used to aggregate constant counts per unit snapshot.
 */
public record UnitConstantsCountRow(
    String path,
    String name,
    Long constants) {}
