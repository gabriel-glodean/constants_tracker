package org.glodean.constants.store;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.glodean.constants.model.UnitConstant.CoreSemanticType;
import org.glodean.constants.model.UnitConstant.CustomSemanticType;
import org.glodean.constants.store.postgres.repository.ConstantUsageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class SemanticTypeStoreBootstrapTest {

  @Mock
  ConstantUsageRepository usageRepository;

  @Test
  void loadSemanticTypesFromDatabaseRegistersPersistedCustomType() {
    when(usageRepository.findDistinctCustomSemanticTypeNames())
        .thenReturn(Flux.just("team.audit"));

    var store = new InMemorySemanticTypeStore();
    var bootstrap = new SemanticTypeStoreBootstrap(store, usageRepository);

    bootstrap.loadSemanticTypesFromDatabase();

    assertThat(store.getSupportedSemanticTypes()).contains(CoreSemanticType.LOG_MESSAGE);
    assertThat(store.getSupportedSemanticTypes())
        .anySatisfy(type -> assertThat(type)
            .isInstanceOf(CustomSemanticType.class)
            .extracting("category", "displayName", "description")
            .containsExactly("team.audit", "team.audit", ""));
  }

  @Test
  void loadSemanticTypesFromDatabaseSwallowsRepositoryErrors() {
    when(usageRepository.findDistinctCustomSemanticTypeNames())
        .thenReturn(Flux.error(new RuntimeException("db unavailable")));

    var store = new InMemorySemanticTypeStore();
    var bootstrap = new SemanticTypeStoreBootstrap(store, usageRepository);

    assertThatCode(bootstrap::loadSemanticTypesFromDatabase).doesNotThrowAnyException();
  }

  @Test
  void loadSemanticTypesFromDatabaseSkipsMalformedRowsAndKeepsValidOnes() {
    when(usageRepository.findDistinctCustomSemanticTypeNames()).thenReturn(Flux.just(
        "team.audit",
        "   "));

    var store = new InMemorySemanticTypeStore();
    var bootstrap = new SemanticTypeStoreBootstrap(store, usageRepository);

    assertThatCode(bootstrap::loadSemanticTypesFromDatabase).doesNotThrowAnyException();
    assertThat(store.getSupportedSemanticTypes())
        .anySatisfy(type -> assertThat(type)
            .isInstanceOf(CustomSemanticType.class)
            .extracting("category")
            .isEqualTo("team.audit"));
  }
}
