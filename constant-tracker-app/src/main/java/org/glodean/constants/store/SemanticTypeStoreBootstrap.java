package org.glodean.constants.store;

import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glodean.constants.model.UnitConstant.CustomSemanticType;
import org.glodean.constants.store.postgres.repository.ConstantUsageRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Loads semantic types already present in PostgreSQL into the in-memory registry at startup.
 */
@Component
@ConditionalOnBean(ConstantUsageRepository.class)
public class SemanticTypeStoreBootstrap {

  private static final Logger logger = LogManager.getLogger(SemanticTypeStoreBootstrap.class);

  private final InMemorySemanticTypeStore semanticTypeStore;
  private final ConstantUsageRepository usageRepository;

  public SemanticTypeStoreBootstrap(
      InMemorySemanticTypeStore semanticTypeStore,
      ConstantUsageRepository usageRepository) {
    this.semanticTypeStore = semanticTypeStore;
    this.usageRepository = usageRepository;
  }

  @PostConstruct
  void loadSemanticTypesFromDatabase() {
    try {
      var loadedTypes = usageRepository
          .findDistinctCustomSemanticTypeNames()
          .flatMap(category -> Mono
              .fromCallable(() -> semanticTypeStore.register(new CustomSemanticType(category, category, "")))
              .onErrorResume(error -> {
                logger.atWarn().withThrowable(error)
                    .log("Skipping malformed persisted custom semantic type name={}", category);
                return Mono.empty();
              }))
          .collectList()
          .block();

      int loadedCount = loadedTypes == null ? 0 : loadedTypes.size();
      logger.atInfo().log("Registered {} semantic types from PostgreSQL", loadedCount);
    } catch (Exception e) {
      logger.atWarn().withThrowable(e)
          .log("Unable to preload semantic types from PostgreSQL; continuing with built-in types only");
    }
  }
}
