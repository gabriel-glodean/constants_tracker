package org.glodean.constants.dto;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import org.glodean.constants.model.UnitConstant;

/**
 * Reply DTO that contains discovered unit (formerly "class") constants grouped by their usage types.
 *
 * <p>This DTO represents the result of a constant search query. The map structure allows
 * clients to see:
 * <ul>
 *   <li><b>What constants exist:</b> Map keys are the constant values (String, Number, etc.)</li>
 *   <li><b>How they're used:</b> Values are collections of {@link UnitConstant.UsageType}s</li>
 * </ul>
 *
 * @param constants a map from constant value to a collection of usage types observed
 */
public record GetUnitConstantsReply(Map<Object, Collection<UnitConstant.UsageType>> constants)
    implements Serializable {}

