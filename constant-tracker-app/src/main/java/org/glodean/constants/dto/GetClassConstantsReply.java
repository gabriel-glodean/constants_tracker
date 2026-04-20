package org.glodean.constants.dto;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import org.glodean.constants.model.UnitConstant;

/**
 * Deprecated compatibility DTO retained during the migration from "class" -> "unit" terminology.
 *
 * <p>Prefer {@link org.glodean.constants.dto.GetUnitConstantsReply} in new code.
 *
 * @deprecated use {@link org.glodean.constants.dto.GetUnitConstantsReply}
 */
@Deprecated
public record GetClassConstantsReply(Map<Object, Collection<UnitConstant.UsageType>> constants)
    implements Serializable {}
