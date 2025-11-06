package org.glodean.constants.model;

public record ClassConstant(Object value, Iterable<UsageType> constantData) {
        public enum UsageType{
            PROPAGATION_IN_ARITHMETIC_OPERATIONS,
            STATIC_FIELD_STORE,
            FIELD_STORE,
            METHOD_INVOCATION_TARGET,
            METHOD_INVOCATION_PARAMETER
        }
}
