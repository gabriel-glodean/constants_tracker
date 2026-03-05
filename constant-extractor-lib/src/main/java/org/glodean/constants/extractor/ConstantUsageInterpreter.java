package org.glodean.constants.extractor;

import org.glodean.constants.model.ClassConstant.UsageLocation;
import org.glodean.constants.model.ClassConstant.ConstantUsage;
import org.glodean.constants.model.ClassConstant.UsageType;

/**
 * Interprets constant usage locations and converts them into meaningful usage information.
 * <p>
 * This interface defines a contract for analyzing where and how constants are used
 * within Java code, supporting both bytecode and source code analysis. It provides
 * context-aware interpretation of constant usage patterns across different extraction methods.
 * </p>
 */
public interface ConstantUsageInterpreter {
    /**
     * Context information used during the interpretation of constant usage.
     * Implementations can extend this interface to provide specific contextual data
     * needed for their interpretation logic.
     */
    interface InterpretationContext {}

    /**
     * Interprets a constant usage location and produces a detailed usage description.
     *
     * @param location the location where the constant is used
     * @param context additional context information for interpreting the usage
     * @return a {@link ConstantUsage} object describing how the constant is used
     */
    ConstantUsage interpret(UsageLocation location, InterpretationContext context);

    /**
     * Determines whether this interpreter can handle the specified usage type.
     *
     * @param type the usage type to check
     * @return {@code true} if this interpreter can interpret the given type, {@code false} otherwise
     */
    boolean canInterpret(UsageType type);
}
