package org.glodean.constants.extractor.bytecode.types;

import java.lang.classfile.TypeKind;

/**
 * Entities that can be type-converted via JVM primitive-conversion instructions
 * (e.g., {@code i2l}, {@code f2d}, {@code d2i}).
 *
 * <p>Implementors ({@link ConstantPropagation}, {@link NumericConstant},
 * {@link PrimitiveValue}) model the effect of narrowing and widening conversions
 * on tracked constant values.
 */
public sealed interface ConvertibleEntity extends StackAndParameterEntity
    permits ConstantPropagation, NumericConstant, PrimitiveValue {

  /**
   * Returns a new entity that represents this value after conversion to {@code targetType}.
   *
   * @param targetType the JVM target type kind (e.g., {@link TypeKind#LONG})
   * @param site       allocation-site tag passed through to any freshly created entities
   * @return a new {@link ConvertibleEntity} of the requested type
   */
  ConvertibleEntity convertTo(TypeKind targetType, String site);
}
