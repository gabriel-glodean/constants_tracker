package org.glodean.constants.extractor.bytecode.types;

import com.google.common.collect.ImmutableSet;
import java.lang.classfile.TypeKind;

/**
 * Numeric constant wrapper supporting propagation operations.
 *
 * <p>Represents a numeric constant value (Integer, Long, Float, Double) discovered in bytecode.
 * This class supports:
 * <ul>
 *   <li><b>Constant propagation:</b> Merging multiple possible numeric values at phi nodes</li>
 *   <li><b>Type conversion:</b> Modeling JVM numeric widening/narrowing (e.g., int → long)</li>
 *   <li><b>Size tracking:</b> Single-cell (int, float) vs double-cell (long, double) values</li>
 * </ul>
 *
 * <p><b>Example:</b> If a local variable can be either {@code 42} or {@code 100} at a merge
 * point, propagation creates a {@link ConstantPropagation} containing both values.
 */
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
          case CHAR -> (int) (char) value().intValue();
          default -> throw new IllegalArgumentException("Unsupported target type: " + targetType);
        };
    return new NumericConstant(convertedValue);
  }
}
