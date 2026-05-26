package org.glodean.constants.store.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glodean.constants.model.UnitConstant;
import org.glodean.constants.model.UnitConstant.CoreSemanticType;
import org.glodean.constants.model.UnitConstant.CustomSemanticType;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.model.UnitDescriptor;
import org.glodean.constants.store.UnitConstantsStore;
import org.glodean.constants.store.Constants;
import org.glodean.constants.store.postgres.entity.*;
import org.glodean.constants.store.postgres.repository.*;
import org.glodean.constants.store.solr.SolrOutboxPayload;
import org.glodean.constants.util.ConstantValueTypeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.glodean.constants.util.LogSanitizer.sanitize;

/**
 * PostgreSQL R2DBC-backed implementation of {@link UnitConstantsStore}.
 *
 * <p>Persists a {@link org.glodean.constants.model.UnitDescriptor} as a row in
 * {@code unit_descriptors} (project + version + source metadata) and stores the full
 * {@link UnitConstants} JSON in a child {@code unit_snapshots} row.
 *
 * <p>Version assignment is delegated to {@code CompositeUnitConstantsStore};
 * the {@link #store(UnitConstants, String)} overload throws {@link UnsupportedOperationException}.
 */
@Service
public class PostgresService implements UnitConstantsStore {

  private static final Logger logger = LogManager.getLogger(PostgresService.class);
  private static final ObjectMapper JSON = createMapper();


  private static ObjectMapper createMapper() {
    var mapper = new ObjectMapper();
    mapper.addMixIn(UnitConstant.SemanticType.class, SemanticTypeMixin.class);
    return mapper;
  }

  @com.fasterxml.jackson.annotation.JsonTypeInfo(
      use = com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME,
      property = "@semanticTypeKind")
  @com.fasterxml.jackson.annotation.JsonSubTypes({
    @com.fasterxml.jackson.annotation.JsonSubTypes.Type(value = CoreSemanticType.class, name = "core"),
    @com.fasterxml.jackson.annotation.JsonSubTypes.Type(value = CustomSemanticType.class, name = "custom")
  })
  private interface SemanticTypeMixin {}

  private final UnitDescriptorRepository descriptorRepo;
  private final UnitSnapshotRepository snapshotRepo;
  private final UnitConstantRepository constantRepo;
  private final ConstantUsageRepository usageRepo;
  private final SolrOutboxRepository solrOutboxRepo;

  public PostgresService(
      @Autowired UnitDescriptorRepository descriptorRepo,
      @Autowired UnitSnapshotRepository snapshotRepo,
      @Autowired UnitConstantRepository constantRepo,
      @Autowired ConstantUsageRepository usageRepo,
      @Autowired SolrOutboxRepository solrOutboxRepo) {
    this.descriptorRepo = descriptorRepo;
    this.snapshotRepo = snapshotRepo;
    this.constantRepo = constantRepo;
    this.usageRepo = usageRepo;
    this.solrOutboxRepo = solrOutboxRepo;
  }

  /**
   * Stores one chunk of class/config files under a JAR-level container descriptor,
   * all in a single {@code @Transactional} call.
   *
   * <p>Model:
   * <ul>
   *   <li>{@code unit_descriptors} — one row per JAR (the container)</li>
   *   <li>{@code unit_snapshots}   — one row per class/config file (the leaf)</li>
   *   <li>{@code unit_constants} / {@code constant_usages} — under each snapshot</li>
   * </ul>
   *
   * <p>When {@code firstBatch = true}, all existing snapshots for the container are
   * deleted (cascading to constants and usages) before the fresh rows are inserted.
   * Subsequent batches for the same container ({@code firstBatch = false}) simply
   * append without clearing, so large JARs split across multiple chunks work correctly.
   *
   * @param containerDescriptor JAR-level descriptor (path = JAR filename, kind = JAR)
   * @param batch               class/config files in this chunk
   * @param firstBatch          true iff this is the first chunk for the container
   * @param project             owning project identifier
   * @param version             target project version
   * @return the input batch, unchanged, after all DB writes have completed
   */
  @Transactional
  public Mono<List<UnitConstants>> storeBatch(
      UnitDescriptor containerDescriptor,
      List<UnitConstants> batch,
      boolean firstBatch,
      String project,
      int version) {
    if (batch.isEmpty()) return Mono.just(List.of());

    logger.atInfo().log(
        "Storing batch of {} units (container={}, firstBatch={}) to PostgreSQL project={} v{}",
        batch.size(), sanitize(containerDescriptor.path()), firstBatch, sanitize(project), version);

    return upsertDescriptor(project, version,
            containerDescriptor.sourceKind().name(),
            containerDescriptor.path(),
            containerDescriptor.sizeBytes(),
            containerDescriptor.contentHash())
        .flatMap(descriptor -> {
          // On the first batch, wipe stale snapshots for this descriptor.
          // DB-level CASCADE deletes their unit_constants and constant_usages automatically.
          Mono<Void> cleanup = firstBatch
              ? snapshotRepo.deleteAllByDescriptorId(descriptor.id())
              : Mono.empty();
          return cleanup.thenReturn(descriptor);
        })
        .flatMap(descriptor -> persistBatch(descriptor, batch).thenReturn(descriptor))
        .flatMap(descriptor ->
            Flux.fromIterable(batch)
                .concatMap(uc -> queueSolrOutbox(uc, project, uc.source().path(), version))
                .then(Mono.just(batch)));
  }

  // -------------------------------------------------------------------------
  // Batch persistence helpers
  // -------------------------------------------------------------------------

  /**
   * Inserts one {@link UnitSnapshotEntity} per class file in {@code batch} under
   * {@code descriptor}, then streams constants and usages for each snapshot.
   */
  private Mono<Void> persistBatch(UnitDescriptorEntity descriptor, List<UnitConstants> batch) {
    record SnapshotSpec(String unitName, String json, List<UnitConstant> constants) {}

    // De-duplicate within the batch: a JAR may contain duplicate ZIP entries with the same path.
    // LinkedHashMap preserves encounter order; first occurrence wins.
    List<SnapshotSpec> specs = batch.stream()
        .collect(java.util.stream.Collectors.toMap(
            uc -> uc.source().path(),
            uc -> new SnapshotSpec(
                uc.source().path(),
                serializeUnitConstantsJson(uc, uc.source().sourceKind().name()),
                List.copyOf(uc.constants())),
            (a, b) -> a,        // first occurrence wins
            java.util.LinkedHashMap::new))
        .values().stream().toList();

    // Use UPSERT so duplicate entries from duplicate ZIP entries never raise a constraint error.
    return Flux.fromIterable(specs)
        .concatMap(s -> snapshotRepo.upsert(descriptor.id(), s.unitName(), s.json()))
        .index()
        .concatMap(t -> persistConstantsForSnapshot(
            t.getT2(), specs.get(t.getT1().intValue()).constants()))
        .then();
  }

  /** Inserts all constants (and their usages) for a single snapshot. */
  private Flux<ConstantUsageEntity> persistConstantsForSnapshot(
      UnitSnapshotEntity snapshot, List<UnitConstant> constants) {
    if (constants.isEmpty()) return Flux.empty();

    record ConstantWithUsages(UnitConstantEntity entity,
                              Set<UnitConstant.ConstantUsage> usages) {}

    List<ConstantWithUsages> contexts = constants.stream()
        .map(uc -> new ConstantWithUsages(
            new UnitConstantEntity(null, snapshot.id(),
                sanitizeForPostgres(String.valueOf(uc.value())),
                ConstantValueTypeMapper.map(uc.value())),
            uc.usages()))
        .toList();

    return constantRepo.saveAll(contexts.stream().map(ConstantWithUsages::entity).toList())
        .index()
        .concatMap(t -> {
          List<ConstantUsageEntity> usageEntities =
              contexts.get(t.getT1().intValue()).usages().stream()
                  .map(u -> buildUsageEntity(t.getT2().id(), u))
                  .toList();
          return usageEntities.isEmpty() ? Flux.empty() : usageRepo.saveAll(usageEntities);
        });
  }

  @Override
  @Transactional
  public Mono<UnitConstants> store(UnitConstants constants, String project, int version) {
    var source = constants.source();
    String sourcePath = source.path();
    String sourceKind = source.sourceKind().name();
    long sizeBytes = source.sizeBytes();
    String contentHash = source.contentHash();
    String unitConstantsJson = serializeUnitConstantsJson(constants, sourceKind);

    logger.atInfo().log(
        "Storing to PostgreSQL: {} (kind={}, size={}) project={} version={}",
        sanitize(sourcePath), sourceKind, sizeBytes, sanitize(project), version);

    return upsertDescriptor(project, version, sourceKind, sourcePath, sizeBytes, contentHash)
        .flatMap(descriptor -> upsertSnapshot(descriptor, sourcePath, unitConstantsJson))
        .flatMap(this::replaceNormalizedRows)
        .flatMap(snapshot -> persistConstantsAndUsages(snapshot, constants))
        .doOnNext(snapshot -> logger.atInfo().log(
            "Saved snapshot id={} for descriptor project={} path={} v{}",
            snapshot.id(), sanitize(project), sanitize(sourcePath), version))
        .thenReturn(constants)
        .flatMap(uc -> queueSolrOutbox(uc, project, sourcePath, version));
  }

  // -------------------------------------------------------------------------
  // Upsert helpers
  // -------------------------------------------------------------------------

  private Mono<UnitDescriptorEntity> upsertDescriptor(
      String project, int version, String sourceKind,
      String sourcePath, long sizeBytes, String contentHash) {
    var entity = new UnitDescriptorEntity(
        null, project, version, sourceKind, sourcePath, sizeBytes, contentHash);
    return descriptorRepo
        .findByProjectAndPathAndVersion(project, sourcePath, version)
        .switchIfEmpty(descriptorRepo.save(entity))
        .flatMap(saved -> descriptorRepo.save(new UnitDescriptorEntity(
            saved.id(), project, version, sourceKind, sourcePath, sizeBytes, contentHash)));
  }

  private Mono<UnitSnapshotEntity> upsertSnapshot(
      UnitDescriptorEntity descriptor, String sourcePath, String json) {
    return snapshotRepo
        .findByDescriptorIdAndUnitName(descriptor.id(), sourcePath)
        .flatMap(existing -> snapshotRepo.save(
            new UnitSnapshotEntity(existing.id(), descriptor.id(), sourcePath, json)))
        .switchIfEmpty(snapshotRepo.save(
            new UnitSnapshotEntity(null, descriptor.id(), sourcePath, json)));
  }

  // -------------------------------------------------------------------------
  // Normalised rows — used by single-file store() only
  // -------------------------------------------------------------------------

  /**
   * Deletes all existing {@code unit_constants} + {@code constant_usages} for one snapshot.
   * Used by the single-file {@link #store} path; the batch path uses
   * {@code snapshotRepo.deleteAllByDescriptorId} instead (cascade via FK).
   */
  private Mono<UnitSnapshotEntity> replaceNormalizedRows(UnitSnapshotEntity snapshot) {
    return constantRepo
        .findAllBySnapshotId(snapshot.id())
        .map(UnitConstantEntity::id)
        .collectList()
        .flatMap(ids -> {
          if (ids.isEmpty()) return Mono.just(snapshot);
          return Flux.fromIterable(ids)
              .concatMap(usageRepo::deleteAllByConstantId)
              .then(constantRepo.deleteAllBySnapshotId(snapshot.id()))
              .thenReturn(snapshot);
        })
        .defaultIfEmpty(snapshot);
  }

  /** Inserts constants + usages for a single snapshot (single-file {@code store()} path). */
  private Mono<UnitSnapshotEntity> persistConstantsAndUsages(
      UnitSnapshotEntity snapshot, UnitConstants constants) {
    return Flux.fromIterable(constants.constants())
        .concatMap(uc -> saveConstantWithUsages(snapshot, uc))
        .then(Mono.just(snapshot));
  }

  /** Replaces null bytes ({@code \0}) with the literal {@code \\0} so they survive PostgreSQL's
   *  UTF-8 encoding check and remain visible/searchable rather than being silently dropped. */
  private static String sanitizeForPostgres(String s) {
    return s == null ? null : s.replace("\0", "\\0");
  }

  private Flux<ConstantUsageEntity> saveConstantWithUsages(
      UnitSnapshotEntity snapshot, UnitConstant uc) {
    String valueStr = sanitizeForPostgres(String.valueOf(uc.value()));
    String valueType = ConstantValueTypeMapper.map(uc.value());
    var entity = new UnitConstantEntity(null, snapshot.id(), valueStr, valueType);
    return constantRepo.save(entity)
        .flatMapMany(saved -> {
          var usageEntities = uc.usages().stream()
              .map(usage -> buildUsageEntity(saved.id(), usage))
              .toList();
          return usageRepo.saveAll(usageEntities);
        });
  }

  private ConstantUsageEntity buildUsageEntity(Long constantId, UnitConstant.ConstantUsage usage) {
    var loc = usage.location();
    var sem = usage.semanticType();
    String semKind = sem instanceof CoreSemanticType ? "CORE" : "CUSTOM";
    String semName;
    if (sem instanceof CoreSemanticType cst) {
      semName = cst.name();
    } else if (sem instanceof CustomSemanticType cust) {
      semName = cust.category();
    } else {
      semName = "";
    }
    String semDisplay = sem instanceof CustomSemanticType cust ? cust.displayName() : null;
    String semDesc = sem instanceof CustomSemanticType cust ? cust.description() : null;
    return new ConstantUsageEntity(
        null, constantId,
        usage.structuralType().name(),
        semKind, semName, semDisplay, semDesc,
        sanitizeForPostgres(loc.className()),
        sanitizeForPostgres(loc.methodName()),
        sanitizeForPostgres(loc.methodDescriptor()),
        loc.bytecodeOffset(), loc.lineNumber(),
        usage.confidence(), serializeJson(usage.metadata()));
  }

  // -------------------------------------------------------------------------
  // Solr outbox
  // -------------------------------------------------------------------------

  private Mono<UnitConstants> queueSolrOutbox(
      UnitConstants constants, String project, String sourcePath, int version) {
    String payloadJson;
    try {
      payloadJson = JSON.writeValueAsString(SolrOutboxPayload.from(constants, project, version));
    } catch (JsonProcessingException e) {
      logger.atWarn().withThrowable(e).log(
          "Failed to serialise Solr outbox payload for {}:{} v{} — Solr indexing skipped",
          sanitize(project), sanitize(sourcePath), version);
      return Mono.just(constants);
    }
    return solrOutboxRepo
        .save(SolrOutboxEntry.newEntry(project, sourcePath, version, payloadJson))
        .doOnNext(entry -> logger.atDebug().log(
            "Queued Solr outbox entry id={} for {}:{} v{}",
            entry.id(), sanitize(project), sanitize(sourcePath), version))
        .thenReturn(constants);
  }

  // -------------------------------------------------------------------------
  // JSON helpers
  // -------------------------------------------------------------------------

  /**
   * Returns a JSON-safe representation of a constant value.
   * Strings, Numbers and Booleans are returned as-is; anything else (e.g. JDK internal
   * types like {@code ClassOrInterfaceDescImpl}) is converted via {@code toString()} so
   * Jackson doesn't try to reflect into unexported JDK modules.
   */
  private static Object toJsonSafeValue(Object v) {
    if (v instanceof String || v instanceof Number || v instanceof Boolean) return v;
    return String.valueOf(v);
  }

  private String serializeUnitConstantsJson(UnitConstants constants, String sourceKind) {
    try {
      var payload = new java.util.HashMap<String, Object>();
      payload.put("source", constants.source());
      payload.put("sourceKind", sourceKind);
      // Normalize constant values to JSON-safe primitives so JDK internal types
      // (e.g. ClassOrInterfaceDescImpl) don't cause Jackson module-access failures.
      var normalized = constants.constants().stream()
          .map(uc -> Map.of("value", toJsonSafeValue(uc.value()), "usages", uc.usages()))
          .toList();
      payload.put("constants", normalized);
      return sanitizeForPostgres(JSON.writeValueAsString(payload));
    } catch (JsonProcessingException e) {
      logger.atWarn().withThrowable(e).log(
          "Failed to serialise UnitConstants to JSON; storing empty object");
      return "{}";
    }
  }

  private String serializeJson(Object value) {
    try {
      return JSON.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      return "{}";
    }
  }

  /**
   * Not supported — version assignment is the responsibility of
   * {@link org.glodean.constants.store.CompositeUnitConstantsStore}.
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  public Mono<UnitConstants> store(UnitConstants constants, String project) {
    throw new UnsupportedOperationException(
        "PostgresService does not manage version assignment; use CompositeUnitConstantsStore");
  }

  /**
   * Looks up constants for a versioned unit key ({@code project:unitPath:version}).
   *
   * <p>In the JAR-hierarchy model, {@code unitPath} is the class-file path stored in
   * {@code unit_snapshots.unit_name}, not the JAR path in {@code unit_descriptors.path}.
   * The query JOINs via {@link UnitSnapshotRepository#findByProjectAndVersionAndUnitName}.
   *
   * <p>Results are cached in the {@value Constants#DATA_LOCATION} cache.
   */
  @Override
  @Cacheable(cacheNames = Constants.DATA_LOCATION, key = "#key")
  public Mono<Map<Object, Collection<UnitConstant.UsageType>>> find(String key) {
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

    return snapshotRepo.findByProjectAndVersionAndUnitName(project, version, unitPath)
        .doFirst(() -> logger.atInfo().log(
            "Querying snapshot project={} unitPath={} v{}",
            sanitize(project), sanitize(unitPath), version))
        .switchIfEmpty(Mono.error(new IllegalArgumentException("Unknown unit!")))
        .flatMap(snapshot -> constantRepo.findAllBySnapshotId(snapshot.id())
            .flatMap(constantEntity -> usageRepo.findAllByConstantId(constantEntity.id())
                .map(usageEntity -> Map.entry(
                    (Object) constantEntity.constantValue(),
                    UnitConstant.UsageType.valueOf(usageEntity.structuralType()))))
            .collectList()
            .map(entries -> {
              Map<Object, Collection<UnitConstant.UsageType>> result = new HashMap<>();
              for (var entry : entries) {
                result.computeIfAbsent(entry.getKey(), _ -> EnumSet.noneOf(UnitConstant.UsageType.class))
                    .add(entry.getValue());
              }
              return result;
            }));
  }
}
