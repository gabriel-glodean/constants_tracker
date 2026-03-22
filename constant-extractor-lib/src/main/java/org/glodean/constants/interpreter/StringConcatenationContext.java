package org.glodean.constants.interpreter;

import java.util.Objects;

/**
 * Interpretation context for a constant that participates in a string concatenation.
 *
 * <p>The {@code constantSource} field indicates whether the constant is a literal
 * segment baked directly into the expression or a reference that was resolved to a
 * constant value at the concatenation site.
 *
 * <p>This context applies to both bytecode analysis ({@code invokedynamic} with
 * {@code StringConcatFactory}) and source-code analysis of {@code +} concatenation
 * expressions.
 *
 * @param constantSource how the constant came to be part of the concatenation
 *
 * @see ConstantSource
 * @see ConstantUsageInterpreter.InterpretationContext
 */
public record StringConcatenationContext(
        ConstantSource constantSource
) implements ConstantUsageInterpreter.InterpretationContext {

    /**
     * Describes how a constant was introduced into a concatenation expression.
     */
    public enum ConstantSource {
        /**
         * The constant is a literal segment written directly in the expression.
         *
         * <p>In bytecode this corresponds to a text segment baked into the
         * {@code invokedynamic} recipe. In source-code analysis it corresponds
         * to a string literal inside the concatenation expression.
         */
        LITERAL,

        /**
         * The constant is a reference that the analysis resolved to a constant
         * value at the concatenation site.
         *
         * <p>In bytecode this is a stack argument that resolved to a constant.
         * In source-code analysis it is a non-literal reference (field access,
         * local variable, or method call) whose value could be statically determined.
         */
        RESOLVED_CONSTANT
    }

    /**
     * Validates the record state on construction.
     *
     * @throws NullPointerException if {@code constantSource} is {@code null}
     */
    public StringConcatenationContext {
        Objects.requireNonNull(constantSource, "constantSource cannot be null");
    }
}
