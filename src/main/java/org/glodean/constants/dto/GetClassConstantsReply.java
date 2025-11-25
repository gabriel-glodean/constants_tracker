package org.glodean.constants.dto;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import org.glodean.constants.model.ClassConstant;

/**
 * Reply DTO that contains discovered class constants grouped by their usage types.
 *
 * @param constants a map from constant value to a collection of usage types observed
 */
public record GetClassConstantsReply(Map<Object, Collection<ClassConstant.UsageType>> constants)
    implements Serializable {}
