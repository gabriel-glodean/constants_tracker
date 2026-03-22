package org.glodean.constants.interpreter;

import org.glodean.constants.model.ClassConstant.UsageLocation;
import org.glodean.constants.model.ClassConstant.ConstantUsage;
import org.glodean.constants.model.ClassConstant.UsageType;

/**
 * Strategy for converting a raw constant usage location into a classified
 * {@link ConstantUsage}.
 *
 * <p>Each implementation handles exactly one {@link UsageType}. Callers should invoke
 * {@link #canInterpret(UsageType)} to select the appropriate implementation, then call
 * {@link #interpret(UsageLocation, InterpretationContext)} with the matching
 * {@link InterpretationContext} subtype.
 *
 * <p>Implementations are expected to be stateless and thread-safe.
 *
 * @see MethodCallContext
 * @see FieldStoreContext
 * @see ArithmeticOperandContext
 * @see StringConcatenationContext
 */
public interface ConstantUsageInterpreter {

    /**
     * Marker interface for the additional data required by a specific interpreter.
     * Each {@link ConstantUsageInterpreter} implementation defines and expects
     * its own concrete subtype.
     */
    interface InterpretationContext {}

    /**
     * Converts a usage location into a structured constant usage description.
     *
     * @param location the location where the constant was found
     * @param context  additional data required for interpretation; must match the
     *                 concrete type expected by this interpreter
     * @return a {@link ConstantUsage} describing how the constant is used
     */
    ConstantUsage interpret(UsageLocation location, InterpretationContext context);

    /**
     * Returns whether this interpreter handles the given usage type.
     *
     * @param type the usage type to test
     * @return {@code true} if this interpreter can process {@code type}
     */
    boolean canInterpret(UsageType type);
}
