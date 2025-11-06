package org.glodean.constants.extractor.bytecode.types;

import com.google.common.collect.ImmutableSet;

import java.util.Objects;
import java.util.Set;

public record ConstantPropagation(Set<Number> values) implements StackAndParameterEntity, ConstantPropagatingEntity {
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ConstantPropagation that = (ConstantPropagation) o;
        return Objects.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(values);
    }

    @Override
    public String toString() {
        return values.toString();
    }

    @Override
    public ConstantPropagatingEntity propagate(ConstantPropagatingEntity constant) {
        return switch (constant) {
            case NumericConstant numericConstant ->
                    new ConstantPropagation(ImmutableSet.<Number>builder().add(numericConstant.value()).addAll(values()).build());
            case ConstantPropagation propagation ->
                    new ConstantPropagation(ImmutableSet.<Number>builder().addAll(propagation.values()).addAll(values()).build());
            default -> this;
        };
    }
}
