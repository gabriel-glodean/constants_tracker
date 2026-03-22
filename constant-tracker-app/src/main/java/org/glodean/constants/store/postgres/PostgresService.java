package org.glodean.constants.store.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glodean.constants.model.ClassConstant;
import org.glodean.constants.model.ClassConstant.CoreSemanticType;
import org.glodean.constants.model.ClassConstant.CustomSemanticType;
import org.glodean.constants.model.ClassConstants;
import org.glodean.constants.store.ClassConstantsStore;
import org.glodean.constants.store.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * PostgreSQL R2DBC-backed implementation of {@link ClassConstantsStore}.
 *
 * <p>Stores the full {@link ClassConstants} model across three tables:
 * {@code class_snapshots}, {@code class_constants}, and {@code constant_usages}.
 * Version assignment is delegated to {@code CompositeClassConstantsStore};
 * the {@link #store(ClassConstants, String)} overload throws {@link UnsupportedOperationException}.
 */
@Service
public class PostgresService implements ClassConstantsStore {

  private static final Logger logger = LogManager.getLogger(PostgresService.class);
  private static final ObjectMapper JSON = new ObjectMapper();

  private final ClassSnapshotRepository snapshotRepo;
  private final ClassConstantRepository constantRepo;
  private final ConstantUsageRepository usageRepo;

  /**
   * Constructs a {@code PostgresService} with the three required repositories.
   *
   * @param snapshotRepo  repository for {@code class_snapshots} rows
   * @param constantRepo  repository for {@code class_constants} rows
   * @param usageRepo     repository for {@code constant_usages} rows
   */
  public PostgresService(
      @Autowired ClassSnapshotRepository snapshotRepo,
      @Autowired ClassConstantRepository constantRepo,
      @Autowired ConstantUsageRepository usageRepo) {
    this.snapshotRepo = snapshotRepo;
    this.constantRepo = constantRepo;
    this.usageRepo = usageRepo;
  }

  @Override
  @Transactional
  public Mono<ClassConstants> store(ClassConstants constants, String project, int version) {
    logger.atInfo().log(
        "Storing to PostgreSQL: {} project={} version={}", constants.name(), project, version);
    var snapshot = new ClassSnapshotEntity(null, project, constants.name(), version);
    return snapshotRepo
        .save(snapshot)
        .flatMap(
            saved ->
                Flux.fromIterable(constants.constants())
                    .flatMap(
                        cc -> {
                          String value = cc.value().toString();
                          String valueType = cc.value().getClass().getSimpleName();
                          var entity =
                              new ClassConstantEntity(null, saved.id(), value, valueType);
                          return constantRepo
                              .save(entity)
                              .flatMapMany(
                                  savedConst ->
                                      Flux.fromIterable(cc.usages())
                                          .flatMap(
                                              usage ->
                                                  usageRepo.save(
                                                      toEntity(savedConst.id(), usage))));
                        })
                    .then(Mono.just(constants)));
  }

  /**
   * Not supported — version assignment is the responsibility of
   * {@link org.glodean.constants.store.CompositeClassConstantsStore}.
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  public Mono<ClassConstants> store(ClassConstants constants, String project) {
    throw new UnsupportedOperationException(
        "PostgresService does not manage version assignment; use CompositeClassConstantsStore");
  }

  /**
   * Looks up constants for a versioned class key ({@code project:className:version}).
   * Results are cached in the {@value Constants#DATA_LOCATION} cache.
   */
  @Override
  @Cacheable(cacheNames = Constants.DATA_LOCATION, key = "#key")
  public Mono<Map<Object, Collection<ClassConstant.UsageType>>> find(String key) {
    String[] parts = key.split(":", 3);
    if (parts.length != 3) {
      return Mono.error(new IllegalArgumentException("Invalid key format: " + key));
    }
    String project = parts[0];
    String className = parts[1];
    int version;
    try {
      version = Integer.parseInt(parts[2]);
    } catch (NumberFormatException e) {
      return Mono.error(new IllegalArgumentException("Invalid version in key: " + key));
    }

    return snapshotRepo
        .findByProjectAndClassNameAndVersion(project, className, version)
        .switchIfEmpty(Mono.error(new IllegalArgumentException("Unknown class!")))
        .flatMapMany(snapshot -> constantRepo.findAllBySnapshotId(snapshot.id()))
        .flatMap(
            cc ->
                usageRepo
                    .findAllByConstantId(cc.id())
                    .map(
                        u ->
                            Map.entry(
                                (Object) cc.constantValue(),
                                ClassConstant.UsageType.valueOf(u.structuralType()))))
        .collectList()
        .map(
            entries -> {
              Map<Object, Collection<ClassConstant.UsageType>> result =
                  HashMap.newHashMap(entries.size());
              for (var entry : entries) {
                result
                    .computeIfAbsent(
                        entry.getKey(), _ -> EnumSet.noneOf(ClassConstant.UsageType.class))
                    .add(entry.getValue());
              }
              return result;
            });
  }

  /**
   * Converts a domain {@link ClassConstant.ConstantUsage} into a storable
   * {@link ConstantUsageEntity}, serialising the semantic-type hierarchy and
   * the metadata map to JSON.
   *
   * @param constantId the foreign key referencing the owning {@link ClassConstantEntity}
   * @param usage      the domain usage to convert
   * @return a fully populated {@link ConstantUsageEntity} ready for persistence
   */
  private static ConstantUsageEntity toEntity(Long constantId, ClassConstant.ConstantUsage usage) {
    var loc = usage.location();
    String kind;
    String name;
    String displayName;
    String description;
    if (usage.semanticType() instanceof CoreSemanticType cst) {
      kind = "CORE";
      name = cst.name();
      displayName = null;
      description = null;
    } else if (usage.semanticType() instanceof CustomSemanticType(
            String category, String displayName1, String description1
    )) {
      kind = "CUSTOM";
      name = category;
      displayName = displayName1;
      description = description1;
    } else {
      kind = "CORE";
      name = "UNKNOWN";
      displayName = null;
      description = null;
    }
    String metadataJson;
    try {
      metadataJson = JSON.writeValueAsString(usage.metadata());
    } catch (JsonProcessingException e) {
      metadataJson = "{}";
    }
    return new ConstantUsageEntity(
        null,
        constantId,
        usage.structuralType().name(),
        kind,
        name,
        displayName,
        description,
        loc.className(),
        loc.methodName(),
        loc.methodDescriptor(),
        loc.bytecodeOffset(),
        loc.lineNumber(),
        usage.confidence(),
        metadataJson);
  }
}


