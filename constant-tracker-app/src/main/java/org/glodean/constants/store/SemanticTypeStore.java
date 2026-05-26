package org.glodean.constants.store;

import org.glodean.constants.model.UnitConstant;

import java.util.Set;

public interface SemanticTypeStore {
    Set<UnitConstant.SemanticType> getSupportedSemanticTypes();

    UnitConstant.SemanticType register(UnitConstant.SemanticType semanticType);
}
