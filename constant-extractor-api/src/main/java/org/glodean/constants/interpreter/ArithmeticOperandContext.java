package org.glodean.constants.interpreter;

import java.util.Objects;

/**
 * Interpretation context for a constant that appears as an operand in an arithmetic expression.
 */
public record ArithmeticOperandContext(
        String operator
) implements ConstantUsageInterpreter.InterpretationContext {
    public ArithmeticOperandContext {
        Objects.requireNonNull(operator, "operator cannot be null");
    }
}

