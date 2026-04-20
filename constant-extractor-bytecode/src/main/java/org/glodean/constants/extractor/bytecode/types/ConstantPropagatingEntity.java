package org.glodean.constants.extractor.bytecode.types;

/** Entities that support constant propagation operations. */
public sealed interface ConstantPropagatingEntity extends StackAndParameterEntity
    permits ConstantPropagation, NumericConstant, PrimitiveValue {

  /**
   * Merges this entity with the given {@code constant}, returning a new entity that
   * over-approximates both possible values.
   *
   * <p>If both entities are known numeric constants, the result is a
   * {@link ConstantPropagation} containing both values. If either entity is non-constant
   * (e.g., a {@link PrimitiveValue}), the result is typically the non-constant entity,
   * representing a loss of precision.
   *
   * @param constant the other constant entity to merge with
   * @return a new {@code ConstantPropagatingEntity} representing the join of both values
   */
  ConstantPropagatingEntity propagate(ConstantPropagatingEntity constant);
}
