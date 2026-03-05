package org.glodean.constants.extractor.bytecode;

import org.glodean.constants.extractor.ConstantUsageInterpreter;
import org.glodean.constants.model.ClassConstant.ConstantUsage;
import org.glodean.constants.model.ClassConstant.CoreSemanticType;
import org.glodean.constants.model.ClassConstant.UsageLocation;
import org.glodean.constants.model.ClassConstant.UsageType;

/**
 * Default no-operation implementation of {@link ConstantUsageInterpreter}.
 * <p>
 * This interpreter performs minimal processing and is useful as a fallback or placeholder.
 * It creates basic usage records with UNKNOWN semantic type and cannot interpret any
 * specific usage types by default.
 * </p>
 *
 * <p>
 * Use this implementation when:
 * <ul>
 *   <li>You need a null-safe default interpreter</li>
 *   <li>You want to disable interpretation temporarily</li>
 *   <li>You're testing infrastructure without semantic analysis</li>
 * </ul>
 * </p>
 *
 * <p><strong>Example usage:</strong></p>
 * <pre>
 * ConstantUsageInterpreter interpreter = new NoOpConstantUsageInterpreter();
 * ConstantUsage usage = interpreter.interpret(location, context);
 * // Returns a basic usage with UNKNOWN semantic type
 * </pre>
 */
public class NoOpConstantUsageInterpreter implements ConstantUsageInterpreter {

    /**
     * Creates a new no-operation interpreter instance.
     */
    public NoOpConstantUsageInterpreter() {
        // No initialization needed
    }

    /**
     * Creates a minimal constant usage record without performing semantic analysis.
     * <p>
     * This method always returns a {@link ConstantUsage} with:
     * <ul>
     *   <li>Structural type: {@link UsageType#METHOD_INVOCATION_PARAMETER} (default)</li>
     *   <li>Semantic type: {@link CoreSemanticType#UNKNOWN}</li>
     *   <li>Confidence: 0.0 (no confidence in classification)</li>
     *   <li>Metadata: empty map</li>
     * </ul>
     * </p>
     *
     * @param location the location where the constant is used
     * @param context additional context information (ignored)
     * @return a minimal {@link ConstantUsage} record with UNKNOWN semantic type
     */
    @Override
    public ConstantUsage interpret(UsageLocation location, InterpretationContext context) {
        // Return a minimal usage with no semantic interpretation
        return new ConstantUsage(
                UsageType.METHOD_INVOCATION_PARAMETER, // Default structural type
                CoreSemanticType.UNKNOWN,               // No semantic classification
                location,
                0.0                                     // Zero confidence
        );
    }

    /**
     * Indicates whether this interpreter can handle the specified usage type.
     * <p>
     * Since this is a no-operation interpreter, it returns {@code false} for all types,
     * indicating it doesn't provide meaningful interpretation for any usage pattern.
     * </p>
     *
     * @param type the usage type to check
     * @return always {@code false}
     */
    @Override
    public boolean canInterpret(UsageType type) {
        // No-op interpreter doesn't claim to interpret any types meaningfully
        return false;
    }
}

