package org.glodean.constants.extractor.bytecode.types;

import java.util.Objects;

/**
 * Base class for immutable constant values tracked by the analysis (numeric or object).
 *
 * <p>In the abstract interpretation, constants represent compile-time known values that
 * propagate through the program. This sealed hierarchy ensures type safety:
 * <ul>
 *   <li>{@link NumericConstant}: Integer, Long, Float, Double values</li>
 *   <li>{@link ObjectConstant}: String literals, Class references, MethodType/MethodHandle constants</li>
 * </ul>
 *
 * <p>Constants are distinguished from {@link ObjectReference}s: constants have known values
 * at analysis time, while object references represent runtime-allocated objects with unknown
 * identity (tracked by allocation site).
 *
 * @param <E> the underlying value type (Number for numeric constants, Object for object constants)
 */
public abstract sealed class Constant<E> implements StackAndParameterEntity
    permits NumericConstant, ObjectConstant {
  private final E value;

  /**
   * Constructs a {@code Constant} wrapping the given value.
   *
   * @param value the compile-time constant value; must not be {@code null}
   */
  protected Constant(E value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return Objects.toString(value);
  }

  /**
   * Returns the wrapped compile-time constant value.
   *
   * @return the constant value; never {@code null}
   */
  public final E value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    Constant<?> constant = (Constant<?>) o;
    return Objects.equals(value, constant.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }
}
