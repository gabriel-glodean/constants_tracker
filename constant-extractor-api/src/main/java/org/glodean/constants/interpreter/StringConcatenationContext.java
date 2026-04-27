package org.glodean.constants.interpreter;

import java.util.Objects;

public record StringConcatenationContext(
        ConstantSource constantSource
) implements ConstantUsageInterpreter.InterpretationContext {

    public enum ConstantSource {
        LITERAL,
        RESOLVED_CONSTANT
    }

    public StringConcatenationContext {
        Objects.requireNonNull(constantSource, "constantSource cannot be null");
    }
}
