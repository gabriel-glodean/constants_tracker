package org.glodean.constants.extractor.bytecode;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimap;
import org.glodean.constants.interpreter.ConstantUsageInterpreter;
import org.glodean.constants.model.ClassConstant;

import java.util.*;

/**
 * Registry that maps {@link ClassConstant.UsageType} values to their corresponding
 * {@link ConstantUsageInterpreter} implementations.
 *
 * <p>A single usage type may be handled by multiple interpreters. Instances are
 * constructed exclusively through the {@link Builder}, which enforces immutability
 * once the registry is built.
 *
 * <p>Example usage:
 * <pre>{@code
 * ConstantUsageInterpreterRegistry registry = ConstantUsageInterpreterRegistry.builder()
 *     .register(UsageType.METHOD_INVOCATION_PARAMETER, myInterpreter)
 *     .register(UsageType.STATIC_FIELD_STORE, anotherInterpreter)
 *     .build();
 *
 * Collection<ConstantUsageInterpreter> interpreters =
 *     registry.getInterpreters(UsageType.METHOD_INVOCATION_PARAMETER);
 * }</pre>
 */
public class ConstantUsageInterpreterRegistry {

    private final Multimap<ClassConstant.UsageType, ConstantUsageInterpreter> interpreters;

    private ConstantUsageInterpreterRegistry(Multimap<ClassConstant.UsageType, ConstantUsageInterpreter> interpreters) {
        this.interpreters = interpreters;
    }

    /**
     * Returns all interpreters registered for the given usage type.
     *
     * @param usageType the usage type to look up; must not be {@code null}
     * @return an unmodifiable collection of interpreters for the given type;
     * empty if none are registered
     * @throws NullPointerException if {@code usageType} is {@code null}
     */
    public Collection<ConstantUsageInterpreter> getInterpreters(ClassConstant.UsageType usageType) {
        Objects.requireNonNull(usageType, "Usage type cannot be null");
        return interpreters.get(usageType);
    }

    /**
     * Creates a new {@link Builder} for constructing a {@code ConstantUsageInterpreterRegistry}.
     *
     * @return a fresh, empty builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link ConstantUsageInterpreterRegistry}.
     *
     * <p>Allows registering one or more {@link ConstantUsageInterpreter} instances per
     * {@link ClassConstant.UsageType}. Multiple interpreters may be registered for the
     * same usage type and will all be available for dispatch during analysis.
     *
     * <p>The resulting registry is immutable; further calls to {@link #register} after
     * {@link #build()} do not affect the already-built instance.
     */
    public static final class Builder {
        private final Set<ClassConstant.UsageType> missing = EnumSet.allOf(ClassConstant.UsageType.class);
        private final ImmutableListMultimap.Builder<ClassConstant.UsageType, ConstantUsageInterpreter> multimapBuilder;

        private Builder() {
            this.multimapBuilder = ImmutableListMultimap.builder();
        }

        /**
         * Registers a {@link ConstantUsageInterpreter} for the specified
         * {@link ClassConstant.UsageType}.
         *
         * <p>Multiple interpreters can be registered for the same usage type; they will
         * all be returned by {@link ConstantUsageInterpreterRegistry#getInterpreters} in
         * registration order.
         *
         * @param usageType   the usage type this interpreter handles; must not be {@code null}
         * @param interpreter the interpreter to register; must not be {@code null}
         * @return this builder instance for method chaining
         * @throws NullPointerException if either argument is {@code null}
         */
        public Builder register(ClassConstant.UsageType usageType, ConstantUsageInterpreter interpreter) {
            Objects.requireNonNull(usageType, "Usage type cannot be null");
            Objects.requireNonNull(interpreter, "Interpreter cannot be null");
            multimapBuilder.put(usageType, interpreter);
            missing.remove(usageType);
            return this;
        }

        /**
         * Constructs an immutable {@link ConstantUsageInterpreterRegistry} from all
         * previously registered interpreters.
         *
         * @return a new, immutable {@code ConstantUsageInterpreterRegistry}
         */
        public ConstantUsageInterpreterRegistry build() {
            for (ClassConstant.UsageType usageType : missing) {
                // Register a default no-op interpreter for any usage types that were not explicitly handled
                multimapBuilder.put(usageType, NoOpConstantUsageInterpreter.forType(usageType));
            }
            return new ConstantUsageInterpreterRegistry(multimapBuilder.build());
        }
    }

    /**
     * Default no-operation implementation of {@link ConstantUsageInterpreter}.
     * <p>
     * This interpreter performs minimal processing and is useful as a fallback or placeholder.
     * It creates basic usage records with UNKNOWN semantic type and cannot interpret any
     * specific usage types by default.
     *
     * <p>
     * Use this implementation when:
     * <ul>
     *   <li>You need a null-safe default interpreter</li>
     *   <li>You want to disable interpretation temporarily</li>
     *   <li>You're testing infrastructure without semantic analysis</li>
     * </ul>
     *
     * <p><strong>Example usage:</strong></p>
     * <pre>
     * ConstantUsageInterpreter interpreter = new NoOpConstantUsageInterpreter();
     * ConstantUsage usage = interpreter.interpret(location, context);
     * // Returns a basic usage with UNKNOWN semantic type
     * </pre>
     */
     enum NoOpConstantUsageInterpreter implements ConstantUsageInterpreter {
        ARITHMETIC_OPERAND_INTERPRETER(ClassConstant.UsageType.ARITHMETIC_OPERAND),

        STRING_CONCATENATION_MEMBER_INTERPRETER(ClassConstant.UsageType.STRING_CONCATENATION_MEMBER),

        STATIC_FIELD_STORE_INTERPRETER(ClassConstant.UsageType.STATIC_FIELD_STORE),

        FIELD_STORE_INTERPRETER(ClassConstant.UsageType.FIELD_STORE),

        METHOD_INVOCATION_TARGET_INTERPRETER(ClassConstant.UsageType.METHOD_INVOCATION_TARGET),

         METHOD_INVOCATION_PARAMETER_INTERPRETER(ClassConstant.UsageType.METHOD_INVOCATION_PARAMETER),

         ANNOTATION_VALUE_INTERPRETER(ClassConstant.UsageType.ANNOTATION_VALUE);

        private final ClassConstant.UsageType usageType;

        private final static Map<ClassConstant.UsageType, NoOpConstantUsageInterpreter> TYPE_TO_INTERPRETER = Map.of(
                ClassConstant.UsageType.ARITHMETIC_OPERAND, ARITHMETIC_OPERAND_INTERPRETER,
                ClassConstant.UsageType.STRING_CONCATENATION_MEMBER, STRING_CONCATENATION_MEMBER_INTERPRETER,
                ClassConstant.UsageType.STATIC_FIELD_STORE, STATIC_FIELD_STORE_INTERPRETER,
                ClassConstant.UsageType.FIELD_STORE, FIELD_STORE_INTERPRETER,
                ClassConstant.UsageType.METHOD_INVOCATION_TARGET, METHOD_INVOCATION_TARGET_INTERPRETER,
                ClassConstant.UsageType.METHOD_INVOCATION_PARAMETER, METHOD_INVOCATION_PARAMETER_INTERPRETER,
                ClassConstant.UsageType.ANNOTATION_VALUE, ANNOTATION_VALUE_INTERPRETER
        );

        static NoOpConstantUsageInterpreter forType(ClassConstant.UsageType usageType) {
            return TYPE_TO_INTERPRETER.get(usageType);
        }

        NoOpConstantUsageInterpreter(ClassConstant.UsageType usageType) {
            this.usageType = usageType;
        }

        /**
         * Creates a minimal constant usage record without performing semantic analysis.
         * <p>
         * This method always returns a {@link ClassConstant.ConstantUsage} with:
         * <ul>
         *   <li>Structural type: {@link ClassConstant.UsageType#METHOD_INVOCATION_PARAMETER} (default)</li>
         *   <li>Semantic type: {@link ClassConstant.CoreSemanticType#UNKNOWN}</li>
         *   <li>Confidence: 0.0 (no confidence in classification)</li>
         *   <li>Metadata: empty map</li>
         * </ul>
         *
         * @param location the location where the constant is used
         * @param context  additional context information (ignored)
         * @return a minimal {@link ClassConstant.ConstantUsage} record with UNKNOWN semantic type
         */
        @Override
        public ClassConstant.ConstantUsage interpret(ClassConstant.UsageLocation location, InterpretationContext context) {
            // Return a minimal usage with no semantic interpretation
            return new ClassConstant.ConstantUsage(
                    this.usageType,
                    ClassConstant.CoreSemanticType.UNKNOWN,               // No semantic classification
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
        public boolean canInterpret(ClassConstant.UsageType type) {
            return type == this.usageType;
        }
    }
}
