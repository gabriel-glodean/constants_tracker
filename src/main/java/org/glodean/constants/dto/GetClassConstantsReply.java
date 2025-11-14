package org.glodean.constants.dto;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import org.glodean.constants.model.ClassConstant;

public record GetClassConstantsReply(Map<Object, Collection<ClassConstant.UsageType>> constants)
    implements Serializable {}
