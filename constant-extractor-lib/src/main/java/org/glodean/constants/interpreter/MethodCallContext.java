package org.glodean.constants.interpreter;

import java.util.Objects;

/**
 * Interpretation context for a constant passed as an argument to a method call.
 *
 * <p>Applies to bytecode invocation instructions (e.g., {@code invokevirtual},
 * {@code invokestatic}) and their source-code equivalents. Class name and descriptor
 * notation follows the JVM convention in bytecode (slash-separated,
 * e.g., {@code "org/slf4j/Logger"}) and uses dot-separated names for source-code
 * analysis (e.g., {@code "org.slf4j.Logger"}).
 *
 * @param targetClass      fully qualified name of the class that declares the method
 * @param targetMethod     name of the method being called
 * @param methodDescriptor method signature descriptor
 *                         (JVM descriptor for bytecode, e.g., {@code "(Ljava/lang/String;)V"},
 *                         or a readable format for source-code analysis)
 * @param receiverKind     whether the call targets a static method, {@code this},
 *                         or an external object
 *
 * @see ReceiverKind
 * @see ConstantUsageInterpreter.InterpretationContext
 */
public record MethodCallContext(
        String targetClass,
        String targetMethod,
        String methodDescriptor,
        ReceiverKind receiverKind
) implements ConstantUsageInterpreter.InterpretationContext {

    /**
     * Validates the record state on construction.
     *
     * @throws NullPointerException if any parameter is {@code null}
     */
    public MethodCallContext {
        Objects.requireNonNull(targetClass, "targetClass cannot be null");
        Objects.requireNonNull(targetMethod, "targetMethod cannot be null");
        Objects.requireNonNull(methodDescriptor, "methodDescriptor cannot be null");
        Objects.requireNonNull(receiverKind, "receiverKind cannot be null");
    }

    /**
     * Creates a context for a call on an external object receiver.
     *
     * <p>Convenience factory equivalent to constructing with
     * {@link ReceiverKind#EXTERNAL_OBJECT}.
     *
     * @param targetClass      fully qualified class name
     * @param targetMethod     method name
     * @param methodDescriptor method signature descriptor
     * @return a new {@code MethodCallContext} with {@link ReceiverKind#EXTERNAL_OBJECT}
     */
    public static MethodCallContext callee(
            String targetClass,
            String targetMethod,
            String methodDescriptor) {
        return new MethodCallContext(targetClass, targetMethod, methodDescriptor, ReceiverKind.EXTERNAL_OBJECT);
    }
}
