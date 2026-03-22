package org.glodean.constants.store.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;
import org.glodean.constants.model.ClassConstant;
import org.glodean.constants.model.ClassConstant.ConstantUsage;
import org.glodean.constants.model.ClassConstant.CoreSemanticType;
import org.glodean.constants.model.ClassConstant.CustomSemanticType;
import org.glodean.constants.model.ClassConstant.UsageLocation;
import org.glodean.constants.model.ClassConstant.UsageType;
import org.glodean.constants.model.ClassConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class PostgresServiceTest {

  @Mock ClassSnapshotRepository snapshotRepo;
  @Mock ClassConstantRepository constantRepo;
  @Mock ConstantUsageRepository usageRepo;

  PostgresService service;

  @BeforeEach
  void setUp() {
    service = new PostgresService(snapshotRepo, constantRepo, usageRepo);
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  static UsageLocation loc() {
    return new UsageLocation("com/example/Greeter", "greet", "()V", 0, null);
  }

  static ClassConstants sampleCoreType() {
    var usage =
        new ConstantUsage(
            UsageType.METHOD_INVOCATION_PARAMETER, CoreSemanticType.LOG_MESSAGE, loc(), 0.9);
    return new ClassConstants(
        "com/example/Greeter", Set.of(new ClassConstant("Hello", Set.of(usage))));
  }

  static ClassConstants sampleCustomType() {
    var semantic = new CustomSemanticType("aws", "AWS ARN", "Amazon Resource Name");
    var usage = new ConstantUsage(UsageType.FIELD_STORE, semantic, loc(), 0.85);
    return new ClassConstants(
        "com/example/AwsClient",
        Set.of(new ClassConstant("arn:aws:s3:::bucket", Set.of(usage))));
  }

  // ── store(ClassConstants, String) — unsupported overload ──────────────────

  @Test
  void storeWithoutVersionThrowsUnsupported() {
    assertThatThrownBy(() -> service.store(sampleCoreType(), "proj"))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("CompositeClassConstantsStore");
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
  void findWithUnknownClassReturnsIllegalArgumentError() {
    when(snapshotRepo.findByProjectAndClassNameAndVersion("proj", "com/example/Greeter", 1))
        .thenReturn(Mono.empty()); // class not in DB

    assertThatThrownBy(() -> service.find("proj:com/example/Greeter:1").block())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown class!");
  }

  // ── store(ClassConstants, String, int) — entity mapping ──────────────────

  @Test
  void storeWithCoreSemanticTypePersistsSuccessfully() {
    var savedSnapshot = new ClassSnapshotEntity(42L, "proj", "com/example/Greeter", 1);
    var savedConstant = new ClassConstantEntity(10L, 42L, "Hello", "String");
    var savedUsage =
        new ConstantUsageEntity(
            1L, 10L, "METHOD_INVOCATION_PARAMETER", "CORE", "LOG_MESSAGE", null, null,
            "com/example/Greeter", "greet", "()V", 0, null, 0.9, "{}");

    when(snapshotRepo.save(any())).thenReturn(Mono.just(savedSnapshot));
    when(constantRepo.save(any())).thenReturn(Mono.just(savedConstant));
    when(usageRepo.save(any())).thenReturn(Mono.just(savedUsage));

    ClassConstants result = service.store(sampleCoreType(), "proj", 1).block();
    assertThat(result).isNotNull();
    assertThat(result.name()).isEqualTo("com/example/Greeter");
  }

  @Test
  void storeWithCustomSemanticTypePersistsSuccessfully() {
    var savedSnapshot = new ClassSnapshotEntity(43L, "proj", "com/example/AwsClient", 2);
    var savedConstant = new ClassConstantEntity(20L, 43L, "arn:aws:s3:::bucket", "String");
    var savedUsage =
        new ConstantUsageEntity(
            2L, 20L, "FIELD_STORE", "CUSTOM", "aws", "AWS ARN", "Amazon Resource Name",
            "com/example/AwsClient", "greet", "()V", 0, null, 0.85, "{}");

    when(snapshotRepo.save(any())).thenReturn(Mono.just(savedSnapshot));
    when(constantRepo.save(any())).thenReturn(Mono.just(savedConstant));
    when(usageRepo.save(any())).thenReturn(Mono.just(savedUsage));

    ClassConstants result = service.store(sampleCustomType(), "proj", 2).block();
    assertThat(result).isNotNull();
    assertThat(result.name()).isEqualTo("com/example/AwsClient");
  }

  @Test
  void storeWithUsageMetadataPersistsSuccessfully() {
    var loc = new UsageLocation("com/example/Client", "send", "()V", 0, null);
    var metadata = Map.<String, Object>of("key", "value", "retries", 3);
    var usage =
        new ConstantUsage(
            UsageType.STRING_CONCATENATION_MEMBER, CoreSemanticType.URL_RESOURCE,
            loc, 0.75, metadata);
    var cc = new ClassConstant("https://api.example.com", Set.of(usage));
    var constants = new ClassConstants("com/example/Client", Set.of(cc));

    var savedSnapshot = new ClassSnapshotEntity(44L, "proj", "com/example/Client", 1);
    var savedConstant =
        new ClassConstantEntity(30L, 44L, "https://api.example.com", "String");
    var savedUsage =
        new ConstantUsageEntity(
            3L, 30L, "STRING_CONCATENATION_MEMBER", "CORE", "URL_RESOURCE", null, null,
            "com/example/Client", "send", "()V", 0, null, 0.75, "{\"key\":\"value\"}");

    when(snapshotRepo.save(any())).thenReturn(Mono.just(savedSnapshot));
    when(constantRepo.save(any())).thenReturn(Mono.just(savedConstant));
    when(usageRepo.save(any())).thenReturn(Mono.just(savedUsage));

    ClassConstants result = service.store(constants, "proj", 1).block();
    assertThat(result).isNotNull();
    assertThat(result.name()).isEqualTo("com/example/Client");
  }
}

