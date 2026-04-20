package org.glodean.constants.store;

import java.util.Collection;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glodean.constants.dto.FuzzySearchResponse;
import org.glodean.constants.model.UnitConstant;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.store.postgres.PostgresService;
import org.glodean.constants.store.solr.SolrService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Primary {@link UnitConstantsStore} that dual-writes to PostgreSQL (authoritative) and Solr
 * (search index with a simplified flat model). Reads always come from PostgreSQL.
 *
 * <p>Version assignment for the auto-version overload uses {@link VersionIncrementer} (Redis).
 * Solr failures are non-fatal: they are logged as warnings and the operation succeeds as long as
 * the PostgreSQL write succeeds.
 */
@Primary
@Service
public record CompositeUnitConstantsStore(
    @Autowired SolrService solrService,
    @Autowired PostgresService postgresService,
    @Autowired VersionIncrementer versionIncrementer)
    implements UnitConstantsStore {

  private static final Logger logger = LogManager.getLogger(CompositeUnitConstantsStore.class);

  @Override
  public Mono<UnitConstants> store(UnitConstants constants, String project) {
    int version = versionIncrementer.getNextVersion(project, constants.source().path());
    return store(constants, project, version);
  }

  @Override
  public Mono<UnitConstants> store(UnitConstants constants, String project, int version) {
    Mono<UnitConstants> pgWrite = postgresService.store(constants, project, version);
    Mono<UnitConstants> solrWrite =
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
                            constants.source().path(),
                            version))
            .onErrorReturn(constants);
    pgWrite = pgWrite.doOnError(e -> logger.atError().withThrowable(e).log("Postgres store failed for {}:{} v{}", project, constants.source().path(), version));
    return Mono.zip(pgWrite, solrWrite).map(_ -> constants);
  }

  /** Reads from PostgreSQL, which holds the full model. */
  @Override
  public Mono<Map<Object, Collection<UnitConstant.UsageType>>> find(String key) {
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


