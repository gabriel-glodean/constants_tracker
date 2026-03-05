package org.glodean.constants.extractor;

/**
 * Context information for constant usage within method invocations.
 * <p>
 * Provides details about the target method being invoked, which can be used
 * by {@link ConstantUsageInterpreter} implementations to determine the semantic
 * meaning of constants passed as parameters.
 * </p>
 * <p>
 * This context is applicable to both bytecode and source code analysis,
 * supporting various constant extraction methods.
 * </p>
 *
 * @param targetClass the fully qualified class name containing the target method
 *                    (slash-separated for bytecode, e.g., "org/slf4j/Logger",
 *                    or dot-separated for source code, e.g., "org.slf4j.Logger")
 * @param targetMethod the name of the target method being invoked
 * @param methodDescriptor the method signature descriptor
 *                         (JVM descriptor format for bytecode, e.g., "(Ljava/lang/String;)V",
 *                         or a readable format for source code)
 */
public record MethodCallContext(
        String targetClass,
        String targetMethod,
        String methodDescriptor
) implements ConstantUsageInterpreter.InterpretationContext {

    /**
     * Creates a method call context with the specified details.
     *
     * @param targetClass the class containing the target method
     * @param targetMethod the target method name
     * @param methodDescriptor the method signature descriptor
     * @throws NullPointerException if any parameter is null
     */
    public MethodCallContext {
        if (targetClass == null) {
            throw new NullPointerException("targetClass cannot be null");
        }
        if (targetMethod == null) {
            throw new NullPointerException("targetMethod cannot be null");
        }
        if (methodDescriptor == null) {
            throw new NullPointerException("methodDescriptor cannot be null");
        }
    }
}

