package org.glodean.constants.extractor.bytecode.types;

import java.lang.classfile.TypeKind;

/**
 * Classifies a JVM value slot as occupying one or two operand-stack cells.
 *
 * <p>Long ({@code J}) and Double ({@code D}) values occupy two stack cells in the JVM
 * specification; all other primitive and reference types occupy one.  This distinction
 * is required to model {@code dup2}, {@code pop2}, and two-word local-variable stores
 * correctly during abstract interpretation.
 */
public enum SizeType {
  /** Value fits in a single operand-stack cell (int, float, reference, etc.). */
  SINGLE_CELL,
  /** Value occupies two operand-stack cells (long or double). */
  DOUBLE_CELL;

  /**
   * Returns the larger of the two sizes, used when merging sizes at phi-nodes.
   *
   * @param other the other size to compare with
   * @return {@link #DOUBLE_CELL} if either operand is double-cell; otherwise {@link #SINGLE_CELL}
   */
  SizeType bigger(SizeType other) {
    if (this == DOUBLE_CELL || other == DOUBLE_CELL) {
      return DOUBLE_CELL;
    }
    return SINGLE_CELL;
  }

  /**
   * Derives the cell size from a {@link TypeKind}.
   *
   * @param typeKind the JVM type kind
   * @return {@link #DOUBLE_CELL} for {@code long}/{@code double}; {@link #SINGLE_CELL} otherwise
   */
  static SizeType fromType(TypeKind typeKind) {
    return typeKind.slotSize() > 1 ? DOUBLE_CELL : SINGLE_CELL;
  }
}
