package org.glodean.constants.store;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glodean.constants.dto.FuzzySearchResponse;
import org.glodean.constants.model.UnitConstant;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.services.ProjectVersionService;
import org.glodean.constants.store.postgres.PostgresService;
import org.glodean.constants.store.solr.SolrService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Primary {@link UnitConstantsStore} that writes to PostgreSQL (authoritative) and queues a Solr
 * outbox entry atomically in the same transaction via {@link PostgresService}.
 *
 * <p>Solr indexing is handled asynchronously by
 * {@link org.glodean.constants.store.solr.SolrOutboxProcessor}, which drains the
 * {@code solr_outbox} table with retries and exponential back-off. This means Solr is never
 * a synchronous dependency of write operations — a Solr outage only delays search results,
 * it never fails uploads.
 *
 * <p>Version inheritance is managed by {@link ProjectVersionService}. When a unit is not found
 * in the requested version, the lookup walks the parent version chain until a match is found
 * or an explicit deletion is encountered.
 */
@Primary
@Service
public class CompositeUnitConstantsStore implements UnitConstantsStore {

  private static final Logger logger = LogManager.getLogger(CompositeUnitConstantsStore.class);
  private static final int MAX_INHERITANCE_DEPTH = 50;

  private final SolrService solrService;
  private final PostgresService postgresService;
  private final ProjectVersionService projectVersionService;

  public CompositeUnitConstantsStore(
      @Autowired SolrService solrService,
      @Autowired PostgresService postgresService,
      @Autowired ProjectVersionService projectVersionService) {
    this.solrService = solrService;
    this.postgresService = postgresService;
    this.projectVersionService = projectVersionService;
  }

  @Override
  public Mono<UnitConstants> store(UnitConstants constants, String project) {
    return projectVersionService
        .getOrCreateOpenVersion(project)
        .flatMap(versionEntity -> doStore(constants, project, versionEntity.version()));
  }

  @Override
  public Mono<UnitConstants> store(UnitConstants constants, String project, int version) {
    return projectVersionService
        .ensureVersionExists(project, version)
        .then(projectVersionService.isVersionOpen(project, version))
        .flatMap(open -> {
          if (!open) {
            return Mono.error(new IllegalStateException(
                "Version " + version + " is finalized for project " + project
                    + "; cannot accept new uploads"));
          }
          return doStore(constants, project, version);
        });
  }

  private Mono<UnitConstants> doStore(UnitConstants constants, String project, int version) {
    // PostgresService.store() is @Transactional and inserts an outbox row atomically.
    // Solr indexing happens asynchronously via SolrOutboxProcessor.
    return postgresService
        .store(constants, project, version)
        .doOnError(e -> logger.atError().withThrowable(e).log(
            "Postgres store failed for {}:{} v{}", project, constants.source().path(), version));
  }

  /**
   * Stores a complete batch of units for a project, assigns a single version, and
   * automatically records deletions for any units that existed in the parent version
   * but are absent from this batch.
   *
   * <p>This is the preferred method for JAR uploads where the full set of units is known.
   */
  @Override
  public Mono<List<UnitConstants>> storeAll(List<UnitConstants> allConstants, String project) {
    return projectVersionService
        .getOrCreateOpenVersion(project)
        .flatMap(versionEntity -> {
          int version = versionEntity.version();
          return Flux.fromIterable(allConstants)
              .flatMap(uc -> doStore(uc, project, version))
              .collectList()
              .flatMap(stored -> {
                Set<String> uploadedPaths = allConstants.stream()
                    .map(uc -> uc.source().path())
                    .collect(Collectors.toSet());
                return projectVersionService
                    .recordRemovals(project, version, uploadedPaths)
                    .doOnNext(removedPath -> logger.atInfo().log(
                        "Recorded removal of {} in v{} of {}", removedPath, version, project))
                    .then(Mono.just(stored));
              });
        });
  }

  /**
   * Reads from PostgreSQL with version inheritance. If the unit is not found in the requested
   * version, walks the parent version chain (up to {@value #MAX_INHERITANCE_DEPTH} levels)
   * until a match is found or an explicit deletion is encountered.
   */
  @Override
  public Mono<Map<Object, Collection<UnitConstant.UsageType>>> find(String key) {
    return findWithInheritance(key, 0);
  }

  private Mono<Map<Object, Collection<UnitConstant.UsageType>>> findWithInheritance(
      String key, int depth) {
    if (depth > MAX_INHERITANCE_DEPTH) {
      return Mono.error(new IllegalArgumentException("Unknown unit!"));
    }

    return postgresService
        .find(key)
        .onErrorResume(IllegalArgumentException.class, _ -> {
          // Unit not found in this version — try the parent
          String[] parts = key.split(":", 3);
          if (parts.length != 3) {
            return Mono.error(new IllegalArgumentException("Invalid key format: " + key));
          }
          String project = parts[0];
          String unitPath = parts[1];
          int version;
          try {
            version = Integer.parseInt(parts[2]);
          } catch (NumberFormatException e) {
            return Mono.error(new IllegalArgumentException("Invalid version in key: " + key));
          }

          return projectVersionService
              .getVersion(project, version)
              .flatMap(versionEntity -> {
                Integer parentVersion = versionEntity.parentVersion();
                if (parentVersion == null) {
                  return Mono.error(new IllegalArgumentException("Unknown unit!"));
                }
                // Check if the unit was explicitly deleted in this version
                return projectVersionService
                    .isUnitDeleted(project, version, unitPath)
                    .flatMap(deleted -> {
                      if (deleted) {
                        return Mono.error(new IllegalArgumentException("Unknown unit!"));
                      }
                      String parentKey = project + ":" + unitPath + ":" + parentVersion;
                      return findWithInheritance(parentKey, depth + 1);
                    });
              })
              .switchIfEmpty(Mono.error(new IllegalArgumentException("Unknown unit!")));
        });
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
