package org.glodean.constants.store;

import java.util.Collection;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glodean.constants.dto.FuzzySearchResponse;
import org.glodean.constants.model.ClassConstant;
import org.glodean.constants.model.ClassConstants;
import org.glodean.constants.store.postgres.PostgresService;
import org.glodean.constants.store.solr.SolrService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Primary {@link ClassConstantsStore} that dual-writes to PostgreSQL (authoritative) and Solr
 * (search index with a simplified flat model). Reads always come from PostgreSQL.
 *
 * <p>Version assignment for the auto-version overload uses {@link VersionIncrementer} (Redis).
 * Solr failures are non-fatal: they are logged as warnings and the operation succeeds as long as
 * the PostgreSQL write succeeds.
 */
@Primary
@Service
public record CompositeClassConstantsStore(
    @Autowired SolrService solrService,
    @Autowired PostgresService postgresService,
    @Autowired VersionIncrementer versionIncrementer)
    implements ClassConstantsStore {

  private static final Logger logger = LogManager.getLogger(CompositeClassConstantsStore.class);

  @Override
  public Mono<ClassConstants> store(ClassConstants constants, String project) {
    int version = versionIncrementer.getNextVersion(project, constants.name());
    return store(constants, project, version);
  }

  @Override
  public Mono<ClassConstants> store(ClassConstants constants, String project, int version) {
    Mono<ClassConstants> pgWrite = postgresService.store(constants, project, version);
    Mono<ClassConstants> solrWrite =
        solrService
            .store(constants, project, version)
            .doOnError(
                e ->
                    logger
                        .atWarn()
                        .withThrowable(e)
                        .log(
                            "Solr store failed for {}:{} v{} – continuing (Postgres is authoritative)",
                            project,
                            constants.name(),
                            version))
            .onErrorReturn(constants);
    return Mono.zip(pgWrite, solrWrite).map(_ -> constants);
  }

  /** Reads from PostgreSQL, which holds the full model. */
  @Override
  public Mono<Map<Object, Collection<ClassConstant.UsageType>>> find(String key) {
    return postgresService.find(key);
  }

  /**
   * Delegates fuzzy search to Solr, which is the search-index side-car.
   * PostgreSQL does not support relevance-ranked full-text search on constant values.
   */
  @Override
  public Mono<FuzzySearchResponse> fuzzySearch(
      String project, String term, int editDistance, int maxRows) {
    return solrService.fuzzySearch(project, term, editDistance, maxRows);
  }
}

