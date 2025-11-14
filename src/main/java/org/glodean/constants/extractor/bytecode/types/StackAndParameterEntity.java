package org.glodean.constants.extractor.bytecode.types;

import static java.lang.constant.ConstantDescs.CD_void;

import java.lang.constant.ClassDesc;

// ==== Abstract heap objects and PT sets (allocation-site abstraction) ====
public sealed interface StackAndParameterEntity
    permits Constant,
        ConstantPropagatingEntity,
        ConstantPropagation,
        NullReference,
        ObjectReference {
  static StackAndParameterEntity convert(ClassDesc type, String tag) {
    if (type.isPrimitive()) {
      return new PrimitiveValue(type, tag);
    }
    if (type != CD_void) {
      return new ObjectReference(type, tag);
    }
    throw new IllegalArgumentException("Cannot resolve void types");
  }
}
