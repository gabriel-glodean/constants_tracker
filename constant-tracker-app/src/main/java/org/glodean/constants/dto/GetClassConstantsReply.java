package org.glodean.constants.dto;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import org.glodean.constants.model.ClassConstant;

/**
 * Reply DTO that contains discovered class constants grouped by their usage types.
 *
 * <p>This DTO represents the result of a constant search query. The map structure allows
 * clients to see:
 * <ul>
 *   <li><b>What constants exist:</b> Map keys are the constant values (String, Number, etc.)</li>
 *   <li><b>How they're used:</b> Values are collections of {@link ClassConstant.UsageType}s</li>
 * </ul>
 *
 * <p><b>Example result:</b>
 * <pre>{@code
 * {
 *   "SELECT * FROM users" -> [METHOD_INVOCATION_PARAMETER],
 *   "https://api.github.com" -> [METHOD_INVOCATION_PARAMETER, STRING_CONCATENATION_MEMBER],
 *   42 -> [ARITHMETIC_OPERAND, FIELD_STORE]
 * }
 * }</pre>
 *
 * @param constants a map from constant value to a collection of usage types observed
 */
public record GetClassConstantsReply(Map<Object, Collection<ClassConstant.UsageType>> constants)
    implements Serializable {}
