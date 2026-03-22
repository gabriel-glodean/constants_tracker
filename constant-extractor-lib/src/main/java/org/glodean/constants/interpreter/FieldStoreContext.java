package org.glodean.constants.interpreter;

import java.util.Objects;

/**
 * Interpretation context for a constant that is stored to an instance field.
 *
 * <p>Covers the {@code putfield} bytecode instruction and its source-code equivalent.
 * Class name and descriptor notation follows the JVM convention in bytecode
 * (slash-separated, e.g., {@code "com/example/Foo"}) and uses dot-separated names
 * for source-code analysis (e.g., {@code "com.example.Foo"}).
 *
 * @param ownerClass      fully qualified name of the class that declares the field
 * @param fieldName       name of the field being assigned
 * @param fieldDescriptor JVM type descriptor of the field
 *                        (e.g., {@code "Ljava/lang/String;"} for bytecode,
 *                        or a readable type name for source-code analysis)
 * @param receiverKind    whether the assignment targets a static field, {@code this},
 *                        or an external object
 *
 * @see ReceiverKind
 * @see ConstantUsageInterpreter.InterpretationContext
 */
public record FieldStoreContext(
        String ownerClass,
        String fieldName,
        String fieldDescriptor,
        ReceiverKind receiverKind
) implements ConstantUsageInterpreter.InterpretationContext {

    /**
     * Validates the record state on construction.
     *
     * @throws NullPointerException if any parameter is {@code null}
     */
    public FieldStoreContext {
        Objects.requireNonNull(ownerClass, "ownerClass cannot be null");
        Objects.requireNonNull(fieldName, "fieldName cannot be null");
        Objects.requireNonNull(fieldDescriptor, "fieldDescriptor cannot be null");
        Objects.requireNonNull(receiverKind, "receiverKind cannot be null");
    }
}
