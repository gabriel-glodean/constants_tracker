package org.glodean.constants.dto;

import org.glodean.constants.model.ClassConstant;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

public record GetClassConstantsReply(
        Map<Object, Collection<ClassConstant.UsageType>> constants) implements Serializable {
}
