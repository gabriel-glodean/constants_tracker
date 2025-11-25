package org.glodean.constants.model;

/**
 * Represents a discovered constant value in a class along with its observed usage types.
 *
 * @param value the constant value (String, Number, etc.)
 * @param constantData iterable of usage types where this constant was observed
 */
public record ClassConstant(Object value, Iterable<UsageType> constantData) {
  public enum UsageType {
    ARITHMETIC_OPERAND,
    STRING_CONCATENATION_MEMBER,
    STATIC_FIELD_STORE,
    FIELD_STORE,
    METHOD_INVOCATION_TARGET,
    METHOD_INVOCATION_PARAMETER
  }
}
