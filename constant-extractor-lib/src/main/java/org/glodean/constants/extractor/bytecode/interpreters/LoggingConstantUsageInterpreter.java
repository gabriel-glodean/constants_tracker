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
 * Interprets constants used in logging method calls detected from bytecode analysis.
 * <p>
 * This interpreter identifies constants passed to logging frameworks (e.g., SLF4J, Log4j,
 * java.util.logging) and classifies them as {@link CoreSemanticType#LOG_MESSAGE}.
 * It recognizes common logging method patterns such as:
 * <ul>
 *   <li>Logger.debug(), Logger.info(), Logger.warn(), Logger.error()</li>
 *   <li>Logger.trace(), Logger.fatal()</li>
 *   <li>System.out.println(), System.err.println()</li>
 * </ul>
 *
 * <p><strong>Usage:</strong></p>
 * <pre>
 * ConstantUsageInterpreter interpreter = new LoggingConstantUsageInterpreter();
 * ConstantUsage usage = interpreter.interpret(location, context);
 * // Returns LOG_MESSAGE semantic type if context indicates logging method
 * </pre>
 */
public class LoggingConstantUsageInterpreter implements ConstantUsageInterpreter {


    private static final Set<String> LOGGER_CLASSES = Set.of(
            "org/slf4j/Logger",
            "org/apache/logging/log4j/Logger",
            "org/apache/log4j/Logger",
            "java/util/logging/Logger",
            "ch/qos/logback/classic/Logger"
    );

    private static final Set<String> LOGGING_METHODS = Set.of(
            "trace", "debug", "info", "warn", "error", "fatal",
            "log", "logp", "logrb", // java.util.logging
            "println", "print" // System.out/err
    );

    private static final Set<String> SYSTEM_OUT_CLASSES = Set.of(
            "java/io/PrintStream",
            "java/io/PrintWriter"
    );

    /**
     * Interprets a constant usage location and determines if it represents a log message.
     * <p>
     * Analyzes the context to identify if the constant is being passed to a logging method.
     * If so, classifies it as {@link CoreSemanticType#LOG_MESSAGE} with high confidence.
     * </p>
     *
     * @param location the location where the constant is used
     * @param context the interpretation context containing method invocation details (expects {@link MethodCallContext})
     * @return a {@link ConstantUsage} with LOG_MESSAGE semantic type if logging is detected,
     *         or UNKNOWN otherwise
     */
    @Override
    public ConstantUsage interpret(UsageLocation location, InterpretationContext context) {
        if (!(context instanceof MethodCallContext(String targetClass, String targetMethod, String methodDescriptor, _))) {
            // Cannot interpret without method call context
            return new ConstantUsage(
                    UsageType.METHOD_INVOCATION_PARAMETER,
                    CoreSemanticType.UNKNOWN,
                    location,
                    0.0
            );
        }

        if ( isLoggingMethod(targetClass, targetMethod)) {
            double confidence = calculateConfidence(targetClass);

            return new ConstantUsage(
                    UsageType.METHOD_INVOCATION_PARAMETER,
                    CoreSemanticType.LOG_MESSAGE,
                    location,
                    confidence,
                    Map.of(
                            "loggerClass", targetClass,
                            "loggerMethod", targetMethod,
                            "methodDescriptor", methodDescriptor
                    )
            );
        }

        // Not a logging call
        return new ConstantUsage(
                UsageType.METHOD_INVOCATION_PARAMETER,
                CoreSemanticType.UNKNOWN,
                location,
                0.0
        );
    }

    /**
     * This interpreter can only handle METHOD_INVOCATION_PARAMETER usage types.
     *
     * @param type the usage type to check
     * @return {@code true} if type is METHOD_INVOCATION_PARAMETER, {@code false} otherwise
     */
    @Override
    public boolean canInterpret(UsageType type) {
        return type == UsageType.METHOD_INVOCATION_PARAMETER;
    }

    /**
     * Determines if the target method is a logging method.
     *
     * @param targetClass the class containing the method
     * @param targetMethod the method name
     * @return {@code true} if this is a logging method, {@code false} otherwise
     */
    private boolean isLoggingMethod(String targetClass, String targetMethod) {
        // Check for known logger classes
        if (LOGGER_CLASSES.contains(targetClass) && LOGGING_METHODS.contains(targetMethod)) {
            return true;
        }

        // Check for System.out.println / System.err.println
        if (SYSTEM_OUT_CLASSES.contains(targetClass) && LOGGING_METHODS.contains(targetMethod)) {
            return true;
        }

        // Check for classes ending with "Logger" (common pattern)
        return targetClass.endsWith("Logger") && LOGGING_METHODS.contains(targetMethod);
    }

    /**
     * Calculates confidence score based on the logging framework.
     *
     * @param targetClass the class containing the method
     * @return confidence score between 0.0 and 1.0
     */
    private double calculateConfidence(String targetClass) {
        // High confidence for well-known logging frameworks
        if (LOGGER_CLASSES.contains(targetClass)) {
            return 0.95;
        }

        // Medium-high confidence for System.out/err
        if (SYSTEM_OUT_CLASSES.contains(targetClass)) {
            return 0.85;
        }

        // Medium confidence for classes ending with Logger
        if (targetClass.endsWith("Logger")) {
            return 0.75;
        }

        // Default medium-low confidence
        return 0.60;
    }
}

