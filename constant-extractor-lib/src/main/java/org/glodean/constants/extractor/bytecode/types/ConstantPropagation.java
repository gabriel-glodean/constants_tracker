package org.glodean.constants.extractor.bytecode.types;

import com.google.common.collect.ImmutableSet;
import java.lang.classfile.TypeKind;
import java.util.Objects;
import java.util.Set;

/** Represents a propagated set of numeric constants (merge of multiple numeric constants). */
public record ConstantPropagation(Set<Number> values, SizeType size)
    implements StackAndParameterEntity, ConstantPropagatingEntity, ConvertibleEntity {

  /**
   * Creates a single-cell {@code ConstantPropagation} from the given value set.
   *
   * @param values the set of possible numeric values
   */
  public ConstantPropagation(Set<Number> values) {
    this(values, SizeType.SINGLE_CELL);
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    ConstantPropagation that = (ConstantPropagation) o;
    return Objects.equals(values, that.values);
  }

  @Override
  public SizeType size() {
    return size;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(values);
  }

  @Override
  public String toString() {
    return values.toString();
  }

  /**
   * Returns a copy of this {@code ConstantPropagation} with the given {@link SizeType},
   * or {@code this} if the size is already correct.
   *
   * @param size the desired cell size
   * @return a {@code ConstantPropagation} with the requested size
   */
  public ConstantPropagation toSize(SizeType size) {
    if (this.size == size) {
      return this;
    }
    return new ConstantPropagation(values, size);
  }

  @Override
  public ConstantPropagatingEntity propagate(ConstantPropagatingEntity constant) {
    return switch (constant) {
      case NumericConstant numericConstant ->
          new ConstantPropagation(
              ImmutableSet.<Number>builder().add(numericConstant.value()).addAll(values()).build(),
              size.bigger(numericConstant.size()));
      case ConstantPropagation propagation ->
          new ConstantPropagation(
              ImmutableSet.<Number>builder().addAll(propagation.values()).addAll(values()).build(),
              size.bigger(propagation.size()));
      default -> this;
    };
  }

  @Override
  public ConvertibleEntity convertTo(TypeKind targetType, String site) {
    return new ConstantPropagation(values, SizeType.fromType(targetType));
  }
}
