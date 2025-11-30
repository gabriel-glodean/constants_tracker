package org.glodean.constants.extractor.bytecode.types;

import com.google.common.collect.ImmutableSet;
import java.lang.classfile.TypeKind;

/** Numeric constant wrapper supporting propagation operations. */
public final class NumericConstant extends Constant<Number>
    implements ConstantPropagatingEntity, ConvertibleEntity {
  public NumericConstant(Number value) {
    super(value);
  }

  @Override
  public SizeType size() {
    if (value() instanceof Double || value() instanceof Long) {
      return SizeType.DOUBLE_CELL;
    }
    return SizeType.SINGLE_CELL;
  }

  @Override
  public ConstantPropagatingEntity propagate(ConstantPropagatingEntity constant) {
    return switch (constant) {
      case NumericConstant numericConstant ->
          new ConstantPropagation(
              ImmutableSet.of(value(), numericConstant.value()),
              size().bigger(numericConstant.size()));
      case ConstantPropagation propagation ->
          new ConstantPropagation(
              ImmutableSet.<Number>builder().addAll(propagation.values()).add(value()).build(),
              size().bigger(propagation.size()));
      default -> this;
    };
  }

  @Override
  public ConvertibleEntity convertTo(TypeKind targetType, String site) {
    Number convertedValue =
        switch (targetType) {
          case BYTE -> value().byteValue();
          case SHORT -> value().shortValue();
          case INT -> value().intValue();
          case LONG -> value().longValue();
          case FLOAT -> value().floatValue();
          case DOUBLE -> value().doubleValue();
          default -> throw new IllegalArgumentException("Unsupported target type: " + targetType);
        };
    return new NumericConstant(convertedValue);
  }
}
