package org.glodean.constants.interpreter;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.SequencedMap;

/**
 * Interpretation context for a constant passed as an argument to a method call.
 */
public record MethodCallContext(
        String targetClass,
        String targetMethod,
        String methodDescriptor,
        ReceiverKind receiverKind
) implements ConstantUsageInterpreter.InterpretationContext {

    public MethodCallContext {
        Objects.requireNonNull(targetClass, "targetClass cannot be null");
        Objects.requireNonNull(targetMethod, "targetMethod cannot be null");
        Objects.requireNonNull(methodDescriptor, "methodDescriptor cannot be null");
        Objects.requireNonNull(receiverKind, "receiverKind cannot be null");
    }

    /**
     * Returns the callee identity in a stable, fixed key order.
     * Used as the {@code metadata} payload stored in {@code constant_usages}.
     * Keys: {@code calleeOwner}, {@code calleeName}, {@code calleeDescriptor}, {@code receiverKind}.
     */
    @Override
    public SequencedMap<String, Object> attributes() {
        LinkedHashMap<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("calleeOwner", targetClass);
        attrs.put("calleeName", targetMethod);
        attrs.put("calleeDescriptor", methodDescriptor);
        attrs.put("receiverKind", receiverKind.name());
        return attrs;
    }
}
