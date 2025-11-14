package org.glodean.constants.extractor.bytecode.types;

import java.util.Objects;

public abstract sealed class Constant<E> implements StackAndParameterEntity
    permits NumericConstant, ObjectConstant {
  private final E value;

  protected Constant(E value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return Objects.toString(value);
  }

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
