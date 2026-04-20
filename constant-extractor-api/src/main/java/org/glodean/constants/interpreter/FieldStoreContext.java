package org.glodean.constants.interpreter;

import java.util.Objects;

/**
 * Interpretation context for a constant that is stored to an instance field.
 */
public record FieldStoreContext(
        String ownerClass,
        String fieldName,
        String fieldDescriptor,
        ReceiverKind receiverKind
) implements ConstantUsageInterpreter.InterpretationContext {

    public FieldStoreContext {
        Objects.requireNonNull(ownerClass, "ownerClass cannot be null");
        Objects.requireNonNull(fieldName, "fieldName cannot be null");
        Objects.requireNonNull(fieldDescriptor, "fieldDescriptor cannot be null");
        Objects.requireNonNull(receiverKind, "receiverKind cannot be null");
    }
}

