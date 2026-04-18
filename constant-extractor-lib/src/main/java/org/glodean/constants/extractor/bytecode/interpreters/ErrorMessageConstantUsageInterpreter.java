package org.glodean.constants.extractor.bytecode.interpreters;

import org.glodean.constants.interpreter.ConstantUsageInterpreter;
import org.glodean.constants.interpreter.MethodCallContext;
import org.glodean.constants.model.ClassConstant.ConstantUsage;
import org.glodean.constants.model.ClassConstant.CoreSemanticType;
import org.glodean.constants.model.ClassConstant.UsageLocation;
import org.glodean.constants.model.ClassConstant.UsageType;

import java.util.Map;
import java.util.Set;

/**
 * Interprets constants passed to exception constructors or {@code throw} helpers as
 * {@link CoreSemanticType#ERROR_MESSAGE}.
 *
 * <p>Detects patterns such as {@code new IllegalArgumentException("bad input")} or
 * {@code Objects.requireNonNull(x, "must not be null")}.
 */
public class ErrorMessageConstantUsageInterpreter implements ConstantUsageInterpreter {

    private static final Set<String> EXCEPTION_SUFFIXES = Set.of(
            "Exception", "Error", "Throwable"
    );

    private static final Set<String> PRECONDITION_CLASSES = Set.of(
            "java/util/Objects",
            "com/google/common/base/Preconditions"
    );

    private static final Set<String> PRECONDITION_METHODS = Set.of(
            "requireNonNull",
            "checkArgument", "checkState", "checkNotNull"
    );

    @Override
    public ConstantUsage interpret(UsageLocation location, InterpretationContext context) {
        if (!(context instanceof MethodCallContext(String targetClass, String targetMethod, String methodDescriptor, _))) {
            return unknown(location);
        }

        if (isErrorMessage(targetClass, targetMethod)) {
            double confidence = calculateConfidence(targetClass, targetMethod);
            return new ConstantUsage(
                    UsageType.METHOD_INVOCATION_PARAMETER,
                    CoreSemanticType.ERROR_MESSAGE,
                    location,
                    confidence,
                    Map.of(
                            "errorClass", targetClass,
                            "errorMethod", targetMethod,
                            "methodDescriptor", methodDescriptor
                    )
            );
        }

        return unknown(location);
    }

    @Override
    public boolean canInterpret(UsageType type) {
        return type == UsageType.METHOD_INVOCATION_PARAMETER;
    }

    private boolean isErrorMessage(String targetClass, String targetMethod) {
        // Exception/Error constructors
        if ("<init>".equals(targetMethod) && isExceptionClass(targetClass)) {
            return true;
        }
        // Precondition check methods
        return PRECONDITION_CLASSES.contains(targetClass) && PRECONDITION_METHODS.contains(targetMethod);
    }

    private boolean isExceptionClass(String className) {
        String simpleName = className.contains("/") ? className.substring(className.lastIndexOf('/') + 1) : className;
        return EXCEPTION_SUFFIXES.stream().anyMatch(simpleName::endsWith);
    }

    private double calculateConfidence(String targetClass, String targetMethod) {
        if ("<init>".equals(targetMethod) && isExceptionClass(targetClass)) {
            return 0.95;
        }
        if (PRECONDITION_CLASSES.contains(targetClass)) {
            return 0.90;
        }
        return 0.70;
    }

    private static ConstantUsage unknown(UsageLocation location) {
        return new ConstantUsage(UsageType.METHOD_INVOCATION_PARAMETER, CoreSemanticType.UNKNOWN, location, 0.0);
    }
}

