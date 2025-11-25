package org.glodean.constants.model;

import java.util.Set;

/**
 * Represents the set of constants discovered for a single class.
 *
 * @param name internal class name (slash-separated)
 * @param constants set of discovered constants and their usage metadata
 */
public record ClassConstants(String name, Set<ClassConstant> constants) {}
