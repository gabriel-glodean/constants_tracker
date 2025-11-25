package org.glodean.constants.extractor.bytecode.types;

import com.google.common.collect.ImmutableSet;

/** Numeric constant wrapper supporting propagation operations. */
public final class NumericConstant extends Constant<Number> implements ConstantPropagatingEntity {
  public NumericConstant(Number value) {
    super(value);
  }

  @Override
  public ConstantPropagatingEntity propagate(ConstantPropagatingEntity constant) {
    return switch (constant) {
      case NumericConstant numericConstant ->
          new ConstantPropagation(ImmutableSet.of(value(), numericConstant.value()));
      case ConstantPropagation propagation ->
          new ConstantPropagation(
              ImmutableSet.<Number>builder().addAll(propagation.values()).add(value()).build());
      default -> this;
    };
  }
}
