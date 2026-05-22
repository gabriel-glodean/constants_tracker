package org.glodean.constants.store.postgres.repository.projection;

/**
 * Flat projection row used to aggregate constant counts per unit snapshot.
 */
public interface UnitConstantsCountRow {

  String getUnitPath();

  String getName();

  Long getConstants();
}

