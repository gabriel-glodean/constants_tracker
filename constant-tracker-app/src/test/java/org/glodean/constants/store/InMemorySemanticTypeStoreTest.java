package org.glodean.constants.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.glodean.constants.model.UnitConstant;
import org.glodean.constants.model.UnitConstant.CoreSemanticType;
import org.glodean.constants.model.UnitConstant.CustomSemanticType;
import org.junit.jupiter.api.Test;

class InMemorySemanticTypeStoreTest {

  private final InMemorySemanticTypeStore store = new InMemorySemanticTypeStore();

  @Test
  void registerNormalizesCustomCategory() {
    var custom = new CustomSemanticType("AWS", "AWS ARN", "Amazon resource identifier");
    UnitConstant.SemanticType resolved = store.register(custom);

    assertThat(resolved)
        .isInstanceOf(CustomSemanticType.class)
        .extracting("category", "displayName", "description")
        .containsExactly("aws", "AWS ARN", "Amazon resource identifier");
    assertThat(store.getSupportedSemanticTypes()).contains(resolved);
  }

  @Test
  void registerCoreTypeReturnsCoreType() {
    UnitConstant.SemanticType resolved = store.register(CoreSemanticType.LOG_MESSAGE);
    assertThat(resolved).isEqualTo(CoreSemanticType.LOG_MESSAGE);
  }

  @Test
  void registerRejectsNullSemanticType() {
    assertThatThrownBy(() -> store.register(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("semanticType cannot be null");
  }

  @Test
  void getSupportedSemanticTypesContainsCoreAndRegisteredCustom() {
    store.register(new CustomSemanticType("team.audit", "Team Audit", ""));

    Set<UnitConstant.SemanticType> supported = store.getSupportedSemanticTypes();

    assertThat(supported).contains(CoreSemanticType.LOG_MESSAGE);
    assertThat(supported)
        .anySatisfy(t -> assertThat(t)
            .isInstanceOf(CustomSemanticType.class)
            .extracting("category")
            .isEqualTo("team.audit"));
  }

  @Test
  void registerDeduplicatesCustomByNormalizedCategory() {
    UnitConstant.SemanticType first =
        store.register(new CustomSemanticType("team.audit", "Team Audit", ""));
    UnitConstant.SemanticType second =
        store.register(new CustomSemanticType("TEAM.AUDIT", "Other Name", "Other Description"));

    assertThat(second).isSameAs(first);
  }
}
