package org.glodean.constants.store.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glodean.constants.model.UnitConstant;
import org.glodean.constants.model.UnitConstant.CoreSemanticType;
import org.glodean.constants.model.UnitConstant.CustomSemanticType;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.store.UnitConstantsStore;
import org.glodean.constants.store.Constants;
import org.glodean.constants.store.postgres.entity.*;
import org.glodean.constants.store.postgres.repository.*;
import org.glodean.constants.store.solr.SolrOutboxPayload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
        sourcePath, sourceKind, sizeBytes, project, version);

    return upsertDescriptor(project, version, sourceKind, sourcePath, sizeBytes, contentHash)
        .flatMap(descriptor -> upsertSnapshot(descriptor, sourcePath, unitConstantsJson))
        .flatMap(this::replaceNormalizedRows)
        .flatMap(snapshot -> persistConstantsAndUsages(snapshot, constants))
        .doOnNext(snapshot -> logger.atInfo().log(
            "Saved snapshot id={} for descriptor project={} path={} v{}",
            snapshot.id(), project, sourcePath, version))
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
  // Normalised rows
  // -------------------------------------------------------------------------

  /** Deletes all existing {@code unit_constants} + {@code constant_usages} for the snapshot. */
  private Mono<UnitSnapshotEntity> replaceNormalizedRows(UnitSnapshotEntity snapshot) {
    return constantRepo
        .findAllBySnapshotId(snapshot.id())
        .flatMap(existing -> usageRepo.findAllByConstantId(existing.id())
            .flatMap(usageRepo::delete)
            .then(constantRepo.delete(existing)))
        .then(Mono.just(snapshot));
  }

  /** Inserts fresh {@code unit_constants} and {@code constant_usages} rows. */
  private Mono<UnitSnapshotEntity> persistConstantsAndUsages(
      UnitSnapshotEntity snapshot, UnitConstants constants) {
    return Flux.fromIterable(constants.constants())
        .flatMap(uc -> saveConstantWithUsages(snapshot, uc))
        .then(Mono.just(snapshot));
  }

  private Flux<ConstantUsageEntity> saveConstantWithUsages(
      UnitSnapshotEntity snapshot, UnitConstant uc) {
    String valueStr = String.valueOf(uc.value());
    String valueType = uc.value().getClass().getSimpleName();
    var entity = new UnitConstantEntity(null, snapshot.id(), valueStr, valueType);
    return constantRepo.save(entity)
        .flatMapMany(saved -> Flux.fromIterable(uc.usages())
            .flatMap(usage -> usageRepo.save(buildUsageEntity(saved.id(), usage))));
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
        loc.className(), loc.methodName(), loc.methodDescriptor(),
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
          project, sourcePath, version);
      return Mono.just(constants);
    }
    return solrOutboxRepo
        .save(SolrOutboxEntry.newEntry(project, sourcePath, version, payloadJson))
        .doOnNext(entry -> logger.atDebug().log(
            "Queued Solr outbox entry id={} for {}:{} v{}",
            entry.id(), project, sourcePath, version))
        .thenReturn(constants);
  }

  // -------------------------------------------------------------------------
  // JSON helpers
  // -------------------------------------------------------------------------

  private String serializeUnitConstantsJson(UnitConstants constants, String sourceKind) {
    try {
      var payload = new java.util.HashMap<String, Object>();
      payload.put("source", constants.source());
      payload.put("sourceKind", sourceKind);
      payload.put("constants", constants.constants());
      return JSON.writeValueAsString(payload);
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
   * Results are cached in the {@value Constants#DATA_LOCATION} cache.
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

    return descriptorRepo
        .findByProjectAndPathAndVersion(project, unitPath, version)
        .doFirst(() -> logger.atInfo().log(
            "Querying descriptor project={} path={} v{}", project, unitPath, version))
        .switchIfEmpty(Mono.error(new IllegalArgumentException("Unknown unit!")))
        .flatMap(descriptor -> snapshotRepo.findByDescriptorIdAndUnitName(descriptor.id(), unitPath))
        .switchIfEmpty(Mono.error(new IllegalArgumentException("No snapshot found!")))
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
