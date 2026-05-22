package org.glodean.constants.dto;

import java.util.List;

/**
 * Groups extracted units under the descriptor path that produced them.
 */
public record UnitListingResponse(
    String unitPath,
    List<UnitEntry> units) {

  /** Single extracted unit entry with the number of constants found. */
  public record UnitEntry(
      String name,
      long constants) {}
}
