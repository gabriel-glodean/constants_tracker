package org.glodean.constants.store.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;
import org.glodean.constants.model.UnitConstant;
import org.glodean.constants.model.UnitConstant.ConstantUsage;
import org.glodean.constants.model.UnitConstant.CoreSemanticType;
import org.glodean.constants.model.UnitConstant.CustomSemanticType;
import org.glodean.constants.model.UnitConstant.UsageLocation;
import org.glodean.constants.model.UnitConstant.UsageType;
import org.glodean.constants.model.UnitConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class PostgresServiceTest {

  @Mock UnitDescriptorRepository descriptorRepo;
  @Mock UnitSnapshotRepository snapshotRepo;
  @Mock UnitConstantRepository constantRepo;
  @Mock ConstantUsageRepository usageRepo;

  PostgresService service;

  @BeforeEach
  void setUp() {
    service = new PostgresService(descriptorRepo, snapshotRepo, constantRepo, usageRepo);
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  static UsageLocation loc() {
    return new UsageLocation("com/example/Greeter", "greet", "()V", 0, null);
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
    when(descriptorRepo.findByProjectAndPathAndVersion("proj", "com/example/Greeter", 1))
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

    when(descriptorRepo.save(any())).thenReturn(Mono.just(savedDescriptor));
    when(snapshotRepo.save(any())).thenReturn(Mono.just(savedSnapshot));

    UnitConstants result = service.store(sampleCoreType(), "proj", 1).block();
    assertThat(result).isNotNull();
    assertThat(result.source().path()).isEqualTo("com/example/Greeter");
  }

  @Test
  void storeWithCustomSemanticTypePersistsSuccessfully() {
    var savedDescriptor = new UnitDescriptorEntity(
        43L, "proj", 2, "CLASS_FILE", "com/example/AwsClient", -1L, null);
    var savedSnapshot = new UnitSnapshotEntity(101L, 43L, "com/example/AwsClient", "{}");

    when(descriptorRepo.save(any())).thenReturn(Mono.just(savedDescriptor));
    when(snapshotRepo.save(any())).thenReturn(Mono.just(savedSnapshot));

    UnitConstants result = service.store(sampleCustomType(), "proj", 2).block();
    assertThat(result).isNotNull();
    assertThat(result.source().path()).isEqualTo("com/example/AwsClient");
  }

  @Test
  void storeWithUsageMetadataPersistsSuccessfully() {
    var loc = new UsageLocation("com/example/Client", "send", "()V", 0, null);
    var metadata = Map.<String, Object>of("key", "value", "retries", 3);
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

    when(descriptorRepo.save(any())).thenReturn(Mono.just(savedDescriptor));
    when(snapshotRepo.save(any())).thenReturn(Mono.just(savedSnapshot));

    UnitConstants result = service.store(constants, "proj", 1).block();
    assertThat(result).isNotNull();
    assertThat(result.source().path()).isEqualTo("com/example/Client");
  }
}

