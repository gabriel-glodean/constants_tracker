package org.glodean.constants.extractor.bytecode.types;

public sealed interface ConstantPropagatingEntity extends StackAndParameterEntity
    permits ConstantPropagation, NumericConstant, PrimitiveValue {
  ConstantPropagatingEntity propagate(ConstantPropagatingEntity constant);
}
