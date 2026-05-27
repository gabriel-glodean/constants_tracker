package org.glodean.constants.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Reply DTO for a constant lookup query ({@code GET /class}).
 *
 * <p>Each {@link ConstantEntry} in {@link #constants()} represents a unique constant value found
 * in the requested unit. It carries:
 * <ul>
 *   <li><b>value</b>     — the constant's storage form (string literal, number, structured form
 *                          like {@code handle:…} or {@code condy:…})</li>
 *   <li><b>valueType</b> — stable type token, e.g. {@code "String"}, {@code "Integer"},
 *                          {@code "MethodHandle"}, {@code "DynamicConstant"}</li>
 *   <li><b>usages</b>    — every observed usage, each paired with its structural type and the
 *                          semantic classification produced by the interpreter pipeline</li>
 * </ul>
 *
 * @param constants list of discovered constants, one entry per unique value
 */
public record GetUnitConstantsReply(List<ConstantEntry> constants) implements Serializable {

  /**
   * One unique constant value found in the unit.
   *
   * @param value     storage form of the constant value
   * @param valueType stable type token (e.g. {@code "String"}, {@code "MethodHandle"})
   * @param usages    list of usage observations for this constant
   */
  public record ConstantEntry(
      String value,
      String valueType,
      List<UsageInfo> usages
  ) implements Serializable {}

  /**
   * A single usage observation: the structural (bytecode-level) role of the constant and
   * the semantic classification assigned by the interpreter.
   *
   * @param structuralType  bytecode-level usage role, e.g. {@code "METHOD_INVOCATION_PARAMETER"}
   * @param semanticType    semantic classification; fields are {@code null} when unknown
   */
  public record UsageInfo(
      String structuralType,
      SemanticTypeInfo semanticType
  ) implements Serializable {}

  /**
   * Semantic type classification as persisted in the constant_usages row.
   *
   * @param kind        {@code "CORE"} or {@code "CUSTOM"}
   * @param name        for CORE: the {@link org.glodean.constants.model.UnitConstant.CoreSemanticType} name;
   *                    for CUSTOM: the category
   * @param displayName human-readable label (CUSTOM only; {@code null} for CORE)
   * @param description optional description (CUSTOM only; {@code null} for CORE)
   */
  public record SemanticTypeInfo(
      String kind,
      String name,
      String displayName,
      String description
  ) implements Serializable {}
}
