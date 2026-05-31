package org.glodean.constants.interpreter;

import org.glodean.constants.model.UnitConstant.ConstantUsage;
import org.glodean.constants.model.UnitConstant.UsageLocation;
import org.glodean.constants.model.UnitConstant.UsageType;

import java.util.SequencedMap;

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
 */
public interface ConstantUsageInterpreter {

    /**
     * Marker interface for the additional data required by a specific interpreter.
     * Each {@link ConstantUsageInterpreter} implementation defines and expects
     * its own concrete subtype.
     */
    interface InterpretationContext {
        SequencedMap<String, Object> attributes();
    }

    /**
     * Converts a usage location into a structured constant usage description, or
     * {@code null} if this interpreter does not recognize the context.
     *
     * @param location the location where the constant was found
     * @param context  additional data required for interpretation; must match the
     *                 concrete type expected by this interpreter
     * @return a {@link ConstantUsage}, or {@code null} if this interpreter cannot interpret the usage
     */
    ConstantUsage interpret(UsageLocation location, InterpretationContext context);

    /**
     * Returns whether this interpreter can try to handle the given usage type.
     *
     * @param type the usage type to test
     * @return {@code true} if this interpreter can process {@code type}
     */
    boolean canInterpret(UsageType type);
}
