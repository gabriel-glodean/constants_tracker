package org.glodean.constants.interpreter;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.SequencedMap;

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

    /**
     * Keys: {@code fieldOwner}, {@code fieldName}, {@code fieldDescriptor}, {@code receiverKind}.
     */
    @Override
    public SequencedMap<String, Object> attributes() {
        LinkedHashMap<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("fieldOwner", ownerClass);
        attrs.put("fieldName", fieldName);
        attrs.put("fieldDescriptor", fieldDescriptor);
        attrs.put("receiverKind", receiverKind.name());
        return attrs;
    }
}
