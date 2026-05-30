package org.glodean.constants.store.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.glodean.constants.dto.GetUnitConstantsReply;
import org.glodean.constants.model.UnitConstant;
import org.glodean.constants.model.UnitConstant.ConstantUsage;
import org.glodean.constants.model.UnitConstant.CoreSemanticType;
import org.glodean.constants.model.UnitConstant.CustomSemanticType;
import org.glodean.constants.model.UnitConstant.UsageLocation;
import org.glodean.constants.model.UnitConstant.UsageType;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.store.postgres.entity.ConstantUsageEntity;
import org.glodean.constants.store.postgres.entity.UnitConstantEntity;
import org.glodean.constants.store.postgres.entity.UnitDescriptorEntity;
import org.glodean.constants.store.postgres.entity.UnitSnapshotEntity;
import org.glodean.constants.store.postgres.repository.ConstantUsageRepository;
import org.glodean.constants.store.postgres.repository.SolrOutboxRepository;
import org.glodean.constants.store.postgres.repository.UnitConstantRepository;
import org.glodean.constants.store.postgres.repository.UnitDescriptorRepository;
import org.glodean.constants.store.postgres.repository.UnitSnapshotRepository;
import org.glodean.constants.store.postgres.entity.SolrOutboxEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class PostgresServiceTest {

  @Mock
  UnitDescriptorRepository descriptorRepo;
  @Mock
  UnitSnapshotRepository snapshotRepo;
  @Mock
  UnitConstantRepository constantRepo;
  @Mock
  ConstantUsageRepository usageRepo;
  @Mock
  SolrOutboxRepository solrOutboxRepo;

  PostgresService service;

  @BeforeEach
  void setUp() {
    service = new PostgresService(descriptorRepo, snapshotRepo, constantRepo, usageRepo, solrOutboxRepo);
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  static UsageLocation loc() {
    return new UsageLocation("com/example/Greeter", "greet", "()V", 0, null);
  }

  static SolrOutboxEntry savedOutboxEntry(String project, String path, int version) {
    java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
    return new SolrOutboxEntry(99L, now, project, path, version, "{}", 0, null, now);
  }

  static UnitConstants sampleCoreType() {
    var usage =
        new ConstantUsage(
            UsageType.METHOD_INVOCATION_PARAMETER, CoreSemanticType.LOG_MESSAGE, loc(), 0.9);
    var cc = new UnitConstant("Hello", Set.of(usage));
    var descriptor = new org.glodean.constants.model.UnitDescriptor(
        org.glodean.constants.extractor.bytecode.BytecodeSourceKind.CLASS_FILE,
        "com/example/Greeter");
    return new UnitConstants(descriptor, Set.of(cc));
  }

  static UnitConstants sampleCustomType() {
    var semantic = new CustomSemanticType("aws", "AWS ARN", "Amazon Resource Name");
    var usage = new ConstantUsage(UsageType.FIELD_STORE, semantic, loc(), 0.85);
    var cc = new UnitConstant("arn:aws:s3:::bucket", Set.of(usage));
    var descriptor = new org.glodean.constants.model.UnitDescriptor(
        org.glodean.constants.extractor.bytecode.BytecodeSourceKind.CLASS_FILE,
        "com/example/AwsClient");
    return new UnitConstants(descriptor, Set.of(cc));
  }

  // ── store(UnitConstants, String) — unsupported overload ──────────────────

  @Test
  void storeWithoutVersionThrowsUnsupported() {
    assertThatThrownBy(() -> service.store(sampleCoreType(), "proj"))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("CompositeUnitConstantsStore");
  }

  // ── find(String) — key parsing errors ─────────────────────────────────────

  @Test
  void findWithTooFewKeyPartsReturnsError() {
    assertThatThrownBy(() -> service.find("proj:com/example/Greeter").block())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid key format");
  }

  @Test
  void findWithSinglePartKeyReturnsError() {
    assertThatThrownBy(() -> service.find("justOneSegment").block())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void findWithNonNumericVersionReturnsError() {
    assertThatThrownBy(() -> service.find("proj:com/example/Greeter:notANumber").block())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid version");
  }

  @Test
  void findWithUnknownUnitReturnsIllegalArgumentError() {
    when(snapshotRepo.findByProjectAndVersionAndUnitName("proj", 1, "com/example/Greeter"))
        .thenReturn(Mono.empty());

    assertThatThrownBy(() -> service.find("proj:com/example/Greeter:1").block())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown unit!");
  }

  // ── store(UnitConstants, String, int) — entity mapping ──────────────────

  @Test
  void storeWithCoreSemanticTypePersistsSuccessfully() {
    var savedDescriptor = new UnitDescriptorEntity(
        42L, "proj", 1, "CLASS_FILE", "com/example/Greeter", -1L, null);
    var savedSnapshot = new UnitSnapshotEntity(100L, 42L, "com/example/Greeter", "{}");

    when(descriptorRepo.findByProjectAndPathAndVersion("proj", "com/example/Greeter", 1))
        .thenReturn(Mono.empty());
    when(descriptorRepo.save(any())).thenReturn(Mono.just(savedDescriptor));
    when(snapshotRepo.findByDescriptorIdAndUnitName(42L, "com/example/Greeter"))
        .thenReturn(Mono.empty());
    when(snapshotRepo.save(any())).thenReturn(Mono.just(savedSnapshot));
    when(constantRepo.findAllBySnapshotId(100L)).thenReturn(Flux.empty());
    when(constantRepo.save(any())).thenReturn(Mono.just(new UnitConstantEntity(1L, 100L, "Hello", "String")));
    when(usageRepo.saveAll(any(Iterable.class))).thenReturn(Flux.just(new ConstantUsageEntity(
        1L, 1L, "METHOD_INVOCATION_PARAMETER", "CORE", "LOG_MESSAGE", null, null,
        "com/example/Greeter", "greet", "()V", 0, null, 0.9, "{}")));
    when(solrOutboxRepo.save(any())).thenReturn(Mono.just(savedOutboxEntry("proj", "com/example/Greeter", 1)));

    UnitConstants result = service.store(sampleCoreType(), "proj", 1).block();
    assertThat(result).isNotNull();
    assertThat(result.source().path()).isEqualTo("com/example/Greeter");
  }

  @Test
  void storeWithCustomSemanticTypePersistsSuccessfully() {
    var savedDescriptor = new UnitDescriptorEntity(
        43L, "proj", 2, "CLASS_FILE", "com/example/AwsClient", -1L, null);
    var savedSnapshot = new UnitSnapshotEntity(101L, 43L, "com/example/AwsClient", "{}");

    when(descriptorRepo.findByProjectAndPathAndVersion("proj", "com/example/AwsClient", 2))
        .thenReturn(Mono.empty());
    when(descriptorRepo.save(any())).thenReturn(Mono.just(savedDescriptor));
    when(snapshotRepo.findByDescriptorIdAndUnitName(43L, "com/example/AwsClient"))
        .thenReturn(Mono.empty());
    when(snapshotRepo.save(any())).thenReturn(Mono.just(savedSnapshot));
    when(constantRepo.findAllBySnapshotId(101L)).thenReturn(Flux.empty());
    when(constantRepo.save(any())).thenReturn(Mono.just(new UnitConstantEntity(2L, 101L, "arn:aws:s3:::bucket", "String")));
    when(usageRepo.saveAll(any(Iterable.class))).thenReturn(Flux.just(new ConstantUsageEntity(
        2L, 2L, "FIELD_STORE", "CUSTOM", "aws", "AWS ARN", "Amazon Resource Name",
        "com/example/Greeter", "greet", "()V", 0, null, 0.85, "{}")));
    when(solrOutboxRepo.save(any())).thenReturn(Mono.just(savedOutboxEntry("proj", "com/example/AwsClient", 2)));

    UnitConstants result = service.store(sampleCustomType(), "proj", 2).block();
    assertThat(result).isNotNull();
    assertThat(result.source().path()).isEqualTo("com/example/AwsClient");
  }

  @Test
  void storeWithUsageMetadataPersistsSuccessfully() {
    var loc = new UsageLocation("com/example/Client", "send", "()V", 0, null);
    var metadata = new LinkedHashMap<>(Map.<String, Object>of("key", "value", "retries", 3));
    var usage =
        new ConstantUsage(
            UsageType.STRING_CONCATENATION_MEMBER, CoreSemanticType.URL_RESOURCE,
            loc, 0.75, metadata);
    var cc = new UnitConstant("https://api.example.com", Set.of(usage));
    var descriptor = new org.glodean.constants.model.UnitDescriptor(
        org.glodean.constants.extractor.bytecode.BytecodeSourceKind.CLASS_FILE,
        "com/example/Client");
    var constants = new UnitConstants(descriptor, Set.of(cc));

    var savedDescriptor = new UnitDescriptorEntity(
        44L, "proj", 1, "CLASS_FILE", "com/example/Client", -1L, null);
    var savedSnapshot = new UnitSnapshotEntity(102L, 44L, "com/example/Client", "{}");

    when(descriptorRepo.findByProjectAndPathAndVersion("proj", "com/example/Client", 1))
        .thenReturn(Mono.empty());
    when(descriptorRepo.save(any())).thenReturn(Mono.just(savedDescriptor));
    when(snapshotRepo.findByDescriptorIdAndUnitName(44L, "com/example/Client"))
        .thenReturn(Mono.empty());
    when(snapshotRepo.save(any())).thenReturn(Mono.just(savedSnapshot));
    when(constantRepo.findAllBySnapshotId(102L)).thenReturn(Flux.empty());
    when(constantRepo.save(any())).thenReturn(Mono.just(new UnitConstantEntity(3L, 102L, "https://api.example.com", "String")));
    when(usageRepo.saveAll(any(Iterable.class))).thenReturn(Flux.just(new ConstantUsageEntity(
        3L, 3L, "STRING_CONCATENATION_MEMBER", "CORE", "URL_RESOURCE", null, null,
        "com/example/Client", "send", "()V", 0, null, 0.75, "{\"key\":\"value\",\"retries\":3}")));
    when(solrOutboxRepo.save(any())).thenReturn(Mono.just(savedOutboxEntry("proj", "com/example/Client", 1)));

    UnitConstants result = service.store(constants, "proj", 1).block();
    assertThat(result).isNotNull();
    assertThat(result.source().path()).isEqualTo("com/example/Client");
  }

  // ── storeBatch — new JAR-hierarchy path ────────────────────────────────────

  static org.glodean.constants.model.UnitDescriptor jarContainer() {
    return new org.glodean.constants.model.UnitDescriptor(
        org.glodean.constants.extractor.bytecode.BytecodeSourceKind.JAR,
        "spring-core.jar", 2048L, "abc123");
  }

  private void stubStoreBatchCommon(long descriptorId, long snapshotId) {
    var savedDescriptor = new UnitDescriptorEntity(
        descriptorId, "proj", 1, "JAR", "spring-core.jar", 2048L, "abc123");
    var savedSnapshot = new UnitSnapshotEntity(snapshotId, descriptorId, "com/example/Greeter", "{}");
    when(descriptorRepo.findByProjectAndPathAndVersion("proj", "spring-core.jar", 1))
        .thenReturn(Mono.empty());
    when(descriptorRepo.save(any())).thenReturn(Mono.just(savedDescriptor));
    when(snapshotRepo.upsert(eq(descriptorId), eq("com/example/Greeter"), anyString()))
        .thenReturn(Mono.just(savedSnapshot));
    when(constantRepo.saveAll(anyList())).thenReturn(Flux.just(
        new UnitConstantEntity(1L, snapshotId, "Hello", "String")));
    when(usageRepo.saveAll(anyList())).thenReturn(Flux.just(new ConstantUsageEntity(
        1L, 1L, "METHOD_INVOCATION_PARAMETER", "CORE", "LOG_MESSAGE", null, null,
        "com/example/Greeter", "greet", "()V", 0, null, 0.9, "{}")));
    when(solrOutboxRepo.save(any())).thenReturn(
        Mono.just(savedOutboxEntry("proj", "com/example/Greeter", 1)));
  }

  @Test
  void storeBatchFirstBatch_deletesExistingSnapshotsAndInsertsNew() {
    stubStoreBatchCommon(42L, 100L);
    when(snapshotRepo.deleteAllByDescriptorId(42L)).thenReturn(Mono.empty());

    List<UnitConstants> result =
        service.storeBatch(jarContainer(), List.of(sampleCoreType()), true, "proj", 1).block();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).source().path()).isEqualTo("com/example/Greeter");
    verify(snapshotRepo).deleteAllByDescriptorId(42L);
    verify(snapshotRepo, times(1)).upsert(eq(42L), eq("com/example/Greeter"), anyString());
  }

  @Test
  void storeBatchNonFirstBatch_skipsDeleteAndInserts() {
    stubStoreBatchCommon(42L, 100L);

    List<UnitConstants> result =
        service.storeBatch(jarContainer(), List.of(sampleCoreType()), false, "proj", 1).block();

    assertThat(result).hasSize(1);
    verify(snapshotRepo, never()).deleteAllByDescriptorId(anyLong());
    verify(snapshotRepo, times(1)).upsert(eq(42L), eq("com/example/Greeter"), anyString());
  }

  @Test
  void storeBatchEmpty_returnsEmptyListImmediately() {
    List<UnitConstants> result =
        service.storeBatch(jarContainer(), List.of(), true, "proj", 1).block();

    assertThat(result).isEmpty();
    verify(snapshotRepo, never()).upsert(anyLong(), anyString(), anyString());
  }

  @Test
  void storeBatchDeduplicates_duplicateUnitNamesInSameBatch() {
    stubStoreBatchCommon(42L, 100L);
    when(snapshotRepo.deleteAllByDescriptorId(42L)).thenReturn(Mono.empty());

    // Two UnitConstants with the same source path — first occurrence wins, one upsert expected
    List<UnitConstants> result =
        service.storeBatch(jarContainer(), List.of(sampleCoreType(), sampleCoreType()),
            true, "proj", 1).block();

    assertThat(result).hasSize(2);
    verify(snapshotRepo, times(1)).upsert(eq(42L), eq("com/example/Greeter"), anyString());
  }

  @Test
  void storeBatchExistingDescriptor_reusesDescriptorId() {
    var existing = new UnitDescriptorEntity(99L, "proj", 1, "JAR", "spring-core.jar", 2048L, "abc123");
    var savedSnapshot = new UnitSnapshotEntity(200L, 99L, "com/example/Greeter", "{}");
    when(descriptorRepo.findByProjectAndPathAndVersion("proj", "spring-core.jar", 1))
        .thenReturn(Mono.just(existing));
    when(descriptorRepo.save(any())).thenReturn(Mono.just(existing));
    when(snapshotRepo.upsert(eq(99L), eq("com/example/Greeter"), anyString()))
        .thenReturn(Mono.just(savedSnapshot));
    when(constantRepo.saveAll(anyList())).thenReturn(Flux.just(
        new UnitConstantEntity(1L, 200L, "Hello", "String")));
    when(usageRepo.saveAll(anyList())).thenReturn(Flux.just(new ConstantUsageEntity(
        1L, 1L, "METHOD_INVOCATION_PARAMETER", "CORE", "LOG_MESSAGE", null, null,
        "com/example/Greeter", "greet", "()V", 0, null, 0.9, "{}")));
    when(solrOutboxRepo.save(any())).thenReturn(
        Mono.just(savedOutboxEntry("proj", "com/example/Greeter", 1)));

    List<UnitConstants> result =
        service.storeBatch(jarContainer(), List.of(sampleCoreType()), false, "proj", 1).block();

    assertThat(result).hasSize(1);
    verify(snapshotRepo).upsert(eq(99L), anyString(), anyString());
  }

  // ── find(String) — success path ────────────────────────────────────────────

  @Test
  void findWithExistingUnit_returnsConstantUsageMap() {
    var snapshot = new UnitSnapshotEntity(100L, 42L, "com/example/Greeter", "{}");
    when(snapshotRepo.findByProjectAndVersionAndUnitName("proj", 1, "com/example/Greeter"))
        .thenReturn(Mono.just(snapshot));
    when(constantRepo.findAllBySnapshotId(100L)).thenReturn(Flux.just(
        new UnitConstantEntity(1L, 100L, "Hello", "String"),
        new UnitConstantEntity(2L, 100L, "World", "String")));
    when(usageRepo.findAllByConstantId(1L)).thenReturn(Flux.just(new ConstantUsageEntity(
        1L, 1L, "METHOD_INVOCATION_PARAMETER", "CORE", "LOG_MESSAGE", null, null,
        "com/example/Greeter", "greet", "()V", 0, null, 0.9, "{}")));
    when(usageRepo.findAllByConstantId(2L)).thenReturn(Flux.just(new ConstantUsageEntity(
        2L, 2L, "FIELD_STORE", "CORE", "CONFIGURATION_VALUE", null, null,
        "com/example/Greeter", "greet", "()V", 0, null, 0.8, "{}")));

    GetUnitConstantsReply reply = service.find("proj:com/example/Greeter:1").block();

    assertThat(reply).isNotNull();
    assertThat(reply.constants()).anyMatch(e -> e.value().equals("Hello")
        && e.valueType().equals("String")
        && e.usages().stream().anyMatch(u -> u.structuralType().equals("METHOD_INVOCATION_PARAMETER")));
    assertThat(reply.constants()).anyMatch(e -> e.value().equals("World")
        && e.usages().stream().anyMatch(u -> u.structuralType().equals("FIELD_STORE")));
  }

  @Test
  void findWithDuplicateConstantValue_mergesUsageTypes() {
    var snapshot = new UnitSnapshotEntity(100L, 42L, "com/example/Greeter", "{}");
    when(snapshotRepo.findByProjectAndVersionAndUnitName("proj", 1, "com/example/Greeter"))
        .thenReturn(Mono.just(snapshot));
    when(constantRepo.findAllBySnapshotId(100L)).thenReturn(Flux.just(
        new UnitConstantEntity(1L, 100L, "Hello", "String"),
        new UnitConstantEntity(2L, 100L, "Hello", "String")));
    when(usageRepo.findAllByConstantId(1L)).thenReturn(Flux.just(new ConstantUsageEntity(
        1L, 1L, "METHOD_INVOCATION_PARAMETER", "CORE", "LOG_MESSAGE", null, null,
        "com/example/Greeter", "greet", "()V", 0, null, 0.9, "{}")));
    when(usageRepo.findAllByConstantId(2L)).thenReturn(Flux.just(new ConstantUsageEntity(
        2L, 2L, "FIELD_STORE", "CORE", "CONFIGURATION_VALUE", null, null,
        "com/example/Greeter", "field", "Ljava/lang/String;", 0, null, 0.8, "{}")));

    GetUnitConstantsReply reply = service.find("proj:com/example/Greeter:1").block();

    assertThat(reply).isNotNull();
    assertThat(reply.constants()).hasSize(1);
    GetUnitConstantsReply.ConstantEntry entry = reply.constants().get(0);
    assertThat(entry.value()).isEqualTo("Hello");
    assertThat(entry.usages())
        .extracting(GetUnitConstantsReply.UsageInfo::structuralType)
        .containsExactlyInAnyOrder("METHOD_INVOCATION_PARAMETER", "FIELD_STORE");
  }
}
