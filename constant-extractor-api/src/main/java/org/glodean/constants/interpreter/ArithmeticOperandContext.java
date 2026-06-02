package org.glodean.constants.interpreter;


import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.SequencedMap;

/**
 * Interpretation context for a constant that appears as an operand in an arithmetic expression.
 */
public record ArithmeticOperandContext(
        String operator
) implements ConstantUsageInterpreter.InterpretationContext {
    public ArithmeticOperandContext {
        Objects.requireNonNull(operator, "operator cannot be null");
    }

    /** Keys: {@code operator}. */
    @Override
    public SequencedMap<String, Object> attributes() {
        LinkedHashMap<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("operator", operator);
        return attrs;
    }
}
