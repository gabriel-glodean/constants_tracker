package org.glodean.constants.extractor.bytecode.types;

import java.lang.classfile.TypeKind;

public enum SizeType {
  SINGLE_CELL,
  DOUBLE_CELL;

  SizeType bigger(SizeType other) {
    if (this == DOUBLE_CELL || other == DOUBLE_CELL) {
      return DOUBLE_CELL;
    }
    return SINGLE_CELL;
  }

  static SizeType fromType(TypeKind typeKind) {
    return typeKind.slotSize() > 1 ? DOUBLE_CELL : SINGLE_CELL;
  }
}
