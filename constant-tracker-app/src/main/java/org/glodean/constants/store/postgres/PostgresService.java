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

  public PostgresService(
      @Autowired UnitDescriptorRepository descriptorRepo,
      @Autowired UnitSnapshotRepository snapshotRepo,
      @Autowired UnitConstantRepository constantRepo,
      @Autowired ConstantUsageRepository usageRepo) {
    this.descriptorRepo = descriptorRepo;
    this.snapshotRepo = snapshotRepo;
    this.constantRepo = constantRepo;
    this.usageRepo = usageRepo;
  }

  @Override
  @Transactional
  public Mono<UnitConstants> store(UnitConstants constants, String project, int version) {
    var source = constants.source();
    String sourcePath = source.path();
    String sourceKind = source.sourceKind().name();
    long sizeBytes = source.sizeBytes();
    String contentHash = source.contentHash();

    logger.atInfo().log(
        "Storing to PostgreSQL: {} (kind={}, size={}) project={} version={}",
        sourcePath, sourceKind, sizeBytes, project, version);

    String unitConstantsJson;
    try {
      var payload = new java.util.HashMap<String, Object>();
      payload.put("source", source);
      payload.put("sourceKind", sourceKind);
      payload.put("constants", constants.constants());
      unitConstantsJson = JSON.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      logger.atWarn().withThrowable(e).log("Failed to serialise UnitConstants to JSON; storing empty object");
      unitConstantsJson = "{}";
    }

    var descriptorEntity = new UnitDescriptorEntity(
        null, project, version, sourceKind, sourcePath, sizeBytes, contentHash);

    String json = unitConstantsJson;
    var constantsSet = constants.constants();

    return descriptorRepo
        .findByProjectAndPathAndVersion(project, sourcePath, version)
        .switchIfEmpty(descriptorRepo.save(descriptorEntity))
        .flatMap(savedDescriptor -> {
          var updated = new UnitDescriptorEntity(
              savedDescriptor.id(), project, version, sourceKind, sourcePath, sizeBytes, contentHash);
          return descriptorRepo.save(updated);
        })
        .flatMap(savedDescriptor ->
            snapshotRepo.findByDescriptorIdAndUnitName(savedDescriptor.id(), sourcePath)
                .flatMap(existing -> {
                  var updatedSnapshot = new UnitSnapshotEntity(
                      existing.id(), savedDescriptor.id(), sourcePath, json);
                  return snapshotRepo.save(updatedSnapshot);
                })
                .switchIfEmpty(snapshotRepo.save(
                    new UnitSnapshotEntity(null, savedDescriptor.id(), sourcePath, json)))
        )
        .flatMap(savedSnapshot -> {
          // Delete existing normalized rows for this snapshot, then re-insert
          return constantRepo.findAllBySnapshotId(savedSnapshot.id())
              .flatMap(existing -> usageRepo.findAllByConstantId(existing.id())
                  .flatMap(usageRepo::delete)
                  .then(constantRepo.delete(existing)))
              .then(Mono.just(savedSnapshot));
        })
        .flatMap(savedSnapshot -> {
          // Persist normalized unit_constants and constant_usages rows
          return Flux.fromIterable(constantsSet)
              .flatMap(uc -> {
                String valueStr = String.valueOf(uc.value());
                String valueType = uc.value().getClass().getSimpleName();
                var entity = new UnitConstantEntity(null, savedSnapshot.id(), valueStr, valueType);
                return constantRepo.save(entity)
                    .flatMapMany(savedConstant ->
                        Flux.fromIterable(uc.usages())
                            .flatMap(usage -> {
                              var loc = usage.location();
                              var sem = usage.semanticType();
                              String semKind = sem instanceof CoreSemanticType ? "CORE" : "CUSTOM";
                              String semName = sem instanceof CoreSemanticType cst ? cst.name()
                                  : ((CustomSemanticType) sem).category();
                              String semDisplay = sem instanceof CustomSemanticType cust ? cust.displayName() : null;
                              String semDesc = sem instanceof CustomSemanticType cust ? cust.description() : null;
                              String metadataJson;
                              try {
                                metadataJson = JSON.writeValueAsString(usage.metadata());
                              } catch (JsonProcessingException e) {
                                metadataJson = "{}";
                              }
                              var usageEntity = new ConstantUsageEntity(
                                  null, savedConstant.id(),
                                  usage.structuralType().name(),
                                  semKind, semName, semDisplay, semDesc,
                                  loc.className(), loc.methodName(), loc.methodDescriptor(),
                                  loc.bytecodeOffset(), loc.lineNumber(),
                                  usage.confidence(), metadataJson);
                              return usageRepo.save(usageEntity);
                            }));
              })
              .then(Mono.just(savedSnapshot));
        })
        .doOnNext(saved -> logger.atInfo().log(
            "Saved snapshot id={} for descriptor project={} path={} v{}",
            saved.id(), project, sourcePath, version))
        .thenReturn(constants);
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

