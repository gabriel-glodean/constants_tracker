package org.glodean.constants.interpreter;

import java.util.Objects;

/**
 * Interpretation context for a constant that appears as an operand in an arithmetic expression.
 *
 * <p>The {@code operator} string identifies the specific operation. In bytecode analysis it
 * holds a JVM arithmetic opcode mnemonic (e.g., {@code "iadd"}, {@code "dmul"}). In
 * source-code analysis it holds the symbolic operator (e.g., {@code "+"}, {@code "*"}).
 *
 * @param operator the arithmetic operator applied to the constant
 *
 * @see ConstantUsageInterpreter.InterpretationContext
 */
public record ArithmeticOperandContext(
        String operator
) implements ConstantUsageInterpreter.InterpretationContext {
    /**
     * Validates the record state on construction.
     *
     * @throws NullPointerException if {@code operator} is {@code null}
     */
    public ArithmeticOperandContext {
        Objects.requireNonNull(operator, "operator cannot be null");
    }
}
