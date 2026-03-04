package org.glodean.constants.extractor.bytecode.types;

/** Entities that support constant propagation operations. */
public sealed interface ConstantPropagatingEntity extends StackAndParameterEntity
    permits ConstantPropagation, NumericConstant, PrimitiveValue {
  ConstantPropagatingEntity propagate(ConstantPropagatingEntity constant);
}
