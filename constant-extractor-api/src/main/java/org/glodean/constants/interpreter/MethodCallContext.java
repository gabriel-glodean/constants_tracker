package org.glodean.constants.interpreter;

import java.util.Objects;

/**
 * Interpretation context for a constant passed as an argument to a method call.
 */
public record MethodCallContext(
        String targetClass,
        String targetMethod,
        String methodDescriptor,
        ReceiverKind receiverKind
) implements ConstantUsageInterpreter.InterpretationContext {

    public MethodCallContext {
        Objects.requireNonNull(targetClass, "targetClass cannot be null");
        Objects.requireNonNull(targetMethod, "targetMethod cannot be null");
        Objects.requireNonNull(methodDescriptor, "methodDescriptor cannot be null");
        Objects.requireNonNull(receiverKind, "receiverKind cannot be null");
    }

    public static MethodCallContext callee(
            String targetClass,
            String targetMethod,
            String methodDescriptor) {
        return new MethodCallContext(targetClass, targetMethod, methodDescriptor, ReceiverKind.EXTERNAL_OBJECT);
    }
}
