package org.glodean.constants.extractor.bytecode.types;

import java.lang.classfile.TypeKind;

public sealed interface ConvertibleEntity extends StackAndParameterEntity
    permits ConstantPropagation, NumericConstant, PrimitiveValue {
  ConvertibleEntity convertTo(TypeKind targetType, String site);
}
