package org.glodean.constants.interpreter;

import java.util.LinkedHashMap;
import java.util.SequencedMap;

public enum StringConcatenationContext implements ConstantUsageInterpreter.InterpretationContext{
        LITERAL,
        RESOLVED_CONSTANT;

    /** Keys: {@code constantSource}. */
    @Override
    public SequencedMap<String, Object> attributes() {
        LinkedHashMap<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("constantSource", this.name());
        return attrs;
    }
}
