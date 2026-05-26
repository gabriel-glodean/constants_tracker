package org.glodean.constants.store;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.glodean.constants.model.UnitConstant;
import org.glodean.constants.model.UnitConstant.CoreSemanticType;
import org.glodean.constants.model.UnitConstant.CustomSemanticType;
import org.springframework.stereotype.Component;

/**
 * In-memory semantic type registry that supports both built-in and DB-loaded custom types.
 */
@Component
public class InMemorySemanticTypeStore implements SemanticTypeStore {

  private final ConcurrentMap<String, CustomSemanticType> customTypesByCategory =
      new ConcurrentHashMap<>();

  public InMemorySemanticTypeStore() {}

  @Override
  public Set<UnitConstant.SemanticType> getSupportedSemanticTypes() {
    var all = new LinkedHashSet<UnitConstant.SemanticType>();
    all.addAll(EnumSet.allOf(CoreSemanticType.class));
    all.addAll(customTypesByCategory.values());
    return Set.copyOf(all);
  }

  @Override
  public UnitConstant.SemanticType register(UnitConstant.SemanticType semanticType) {
    Objects.requireNonNull(semanticType, "semanticType cannot be null");

    if (semanticType instanceof CoreSemanticType) {
      return semanticType;
    }
    if (semanticType instanceof CustomSemanticType(String category1, String displayName, String description)) {
      String category = normalizeCategory(category1);
      CustomSemanticType normalized =
          new CustomSemanticType(category, displayName, description);
      return customTypesByCategory.computeIfAbsent(category, ignored -> normalized);
    }

    throw new IllegalArgumentException("Unsupported semantic type: " + semanticType.getClass());
  }

  private static String normalizeName(String name) {
    String normalized = name == null ? "" : name.trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("Semantic type name cannot be blank");
    }
    return normalized;
  }

  private static String normalizeCategory(String category) {
    return normalizeName(category).toLowerCase(Locale.ROOT);
  }


}
