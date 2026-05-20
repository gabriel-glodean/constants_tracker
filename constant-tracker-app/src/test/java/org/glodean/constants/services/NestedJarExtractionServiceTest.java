package org.glodean.constants.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.glodean.constants.extractor.bytecode.BytecodeSourceKind;
import org.glodean.constants.model.UnitConstant;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.model.UnitDescriptor;
import org.glodean.constants.store.JarBatch;
import org.glodean.constants.store.postgres.entity.ProjectVersionEntity;
import org.glodean.constants.store.postgres.entity.UnitDescriptorEntity;
import org.glodean.constants.store.postgres.repository.UnitDescriptorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for {@link NestedJarExtractionService}.
 *
 * <p>Real ZIP/JAR files are written to a temp directory; all I/O-adjacent dependencies
 * (ExtractionService, UnitDescriptorRepository, ProjectVersionService) are mocked.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NestedJarExtractionServiceTest {

  @TempDir Path tempDir;

  @Mock ExtractionService extractionService;
  @Mock UnitDescriptorRepository descriptorRepository;
  @Mock ProjectVersionService projectVersionService;

  NestedJarExtractionService service;

  static final String PROJECT = "test-project";
  static final int VERSION = 1;

  /** A minimal UnitConstants returned by the mocked extractionService. */
  static UnitConstants stubUnitConstants(String name) {
    var descriptor = new UnitDescriptor(BytecodeSourceKind.JAR, name);
    var usage = new UnitConstant.ConstantUsage(
        UnitConstant.UsageType.METHOD_INVOCATION_PARAMETER,
        UnitConstant.CoreSemanticType.LOG_MESSAGE,
        new UnitConstant.UsageLocation("com/Example", "m", "()V", 0, null),
        1.0);
    return new UnitConstants(descriptor, Set.of(new UnitConstant("hello", Set.of(usage))));
  }

  @BeforeEach
  void setUp() {
    service = new NestedJarExtractionService(
        extractionService, descriptorRepository, projectVersionService, 500);

    // Default: project has an open version.
    when(projectVersionService.getOrCreateOpenVersion(PROJECT))
        .thenReturn(Mono.just(new ProjectVersionEntity(
            1L, PROJECT, VERSION, null, ProjectVersionEntity.STATUS_OPEN, null, null)));

    // Default: no existing hash in unit_descriptors → not yet indexed.
    when(descriptorRepository.findByProjectAndVersionAndContentHash(
        eq(PROJECT), eq(VERSION), anyString()))
        .thenReturn(Mono.empty());
  }

  // ── Helpers ──────────────────────────────────────────────────────────────────

  /**
   * Builds a minimal valid JAR (ZIP) in memory containing a single fake {@code .class} entry.
   */
  static byte[] minimalJarBytes() throws IOException {
    try (var baos = new ByteArrayOutputStream();
         var zos = new ZipOutputStream(baos)) {
      zos.putNextEntry(new ZipEntry("com/example/Foo.class"));
      zos.write(new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE}); // magic
      zos.closeEntry();
      zos.finish();
      return baos.toByteArray();
    }
  }

  /**
   * Builds a fat JAR on disk. The {@code nestedEntries} map is entry-name → bytes.
   */
  static Path buildFatJar(Path tempDir, String fileName, java.util.Map<String, byte[]> entries)
      throws IOException {
    Path out = tempDir.resolve(fileName);
    try (var zos = new ZipOutputStream(Files.newOutputStream(out))) {
      for (var e : entries.entrySet()) {
        zos.putNextEntry(new ZipEntry(e.getKey()));
        zos.write(e.getValue());
        zos.closeEntry();
      }
      zos.finish();
    }
    return out;
  }

  // ── Tests: layout patterns ────────────────────────────────────────────────────

  @Test
  void extractsBootInfLibJar() throws Exception {
    byte[] libBytes = minimalJarBytes();
    Path fatJar = buildFatJar(tempDir, "app.jar",
        java.util.Map.of("BOOT-INF/lib/mylib.jar", libBytes));

    UnitConstants expected = stubUnitConstants("mylib.jar");
    when(extractionService.extractZipStream(any(), any())).thenReturn(List.of(expected));

    StepVerifier.create(service.extractNestedJars(fatJar, PROJECT))
        .assertNext(batch -> assertThat(batch.units()).containsExactly(expected))
        .verifyComplete();

    verify(extractionService, times(1)).extractZipStream(any(), any());
  }

  @Test
  void extractsWebInfLibJar() throws Exception {
    byte[] libBytes = minimalJarBytes();
    Path fatJar = buildFatJar(tempDir, "app.war",
        java.util.Map.of("WEB-INF/lib/servlet-lib.jar", libBytes));

    UnitConstants expected = stubUnitConstants("servlet-lib.jar");
    when(extractionService.extractZipStream(any(), any())).thenReturn(List.of(expected));

    StepVerifier.create(service.extractNestedJars(fatJar, PROJECT))
        .assertNext(batch -> assertThat(batch.units()).containsExactly(expected))
        .verifyComplete();

    verify(extractionService, times(1)).extractZipStream(any(), any());
  }

  @Test
  void extractsRootLevelJar() throws Exception {
    byte[] libBytes = minimalJarBytes();
    Path fatJar = buildFatJar(tempDir, "uber.jar",
        java.util.Map.of("commons.jar", libBytes));

    UnitConstants expected = stubUnitConstants("commons.jar");
    when(extractionService.extractZipStream(any(), any())).thenReturn(List.of(expected));

    StepVerifier.create(service.extractNestedJars(fatJar, PROJECT))
        .assertNext(batch -> assertThat(batch.units()).containsExactly(expected))
        .verifyComplete();
  }

  @Test
  void extractsMultipleNestedJarsFromBothLocations() throws Exception {
    byte[] libBytes = minimalJarBytes();
    Path fatJar = buildFatJar(tempDir, "multi.jar", java.util.Map.of(
        "BOOT-INF/lib/alpha.jar", libBytes,
        "BOOT-INF/lib/beta.jar", libBytes
    ));

    UnitConstants a = stubUnitConstants("alpha.jar");
    UnitConstants b = stubUnitConstants("beta.jar");
    when(extractionService.extractZipStream(any(), any()))
        .thenReturn(List.of(a))
        .thenReturn(List.of(b));

    // Each nested JAR emits one JarBatch — unwrap units and compare all UnitConstants
    List<UnitConstants> result = service.extractNestedJars(fatJar, PROJECT)
        .flatMapIterable(JarBatch::units)
        .collectList().block();
    assertThat(result).containsExactlyInAnyOrder(a, b);

    verify(extractionService, times(2)).extractZipStream(any(), any());
  }

  // ── Tests: two levels of nesting ─────────────────────────────────────────────

  /**
   * Verifies that a JAR nested inside a nested JAR (two levels deep) is NOT extracted.
   *
   * <p>The outer fat JAR contains {@code BOOT-INF/lib/outer-lib.jar}. That lib itself
   * contains {@code inner.jar}. Since we only walk the top-level ZipFileSystem entries
   * and never recurse into nested JARs, {@code inner.jar} never appears as a candidate
   * and is silently ignored — only {@code outer-lib.jar} is extracted.
   */
  @Test
  void twoLevelNesting_onlyFirstLevelExtracted() throws Exception {
    // Build the inner JAR (level 2 — should be ignored)
    byte[] innerJarBytes;
    try (var baos = new ByteArrayOutputStream(); var zos = new ZipOutputStream(baos)) {
      zos.putNextEntry(new ZipEntry("deep/Deep.class"));
      zos.write(new byte[]{0, 0, 0, 1});
      zos.closeEntry();
      zos.finish();
      innerJarBytes = baos.toByteArray();
    }

    // Build the level-1 nested JAR that contains the inner JAR
    byte[] outerLibBytes;
    try (var baos = new ByteArrayOutputStream(); var zos = new ZipOutputStream(baos)) {
      zos.putNextEntry(new ZipEntry("com/example/Lib.class"));
      zos.write(new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});
      zos.closeEntry();
      // A JAR entry nested inside this JAR — should be ignored
      zos.putNextEntry(new ZipEntry("inner.jar"));
      zos.write(innerJarBytes);
      zos.closeEntry();
      zos.finish();
      outerLibBytes = baos.toByteArray();
    }

    Path fatJar = buildFatJar(tempDir, "fat.jar",
        java.util.Map.of("BOOT-INF/lib/outer-lib.jar", outerLibBytes));

    UnitConstants extracted = stubUnitConstants("outer-lib.jar");
    when(extractionService.extractZipStream(any(), any())).thenReturn(List.of(extracted));

    StepVerifier.create(service.extractNestedJars(fatJar, PROJECT))
        .assertNext(batch -> assertThat(batch.units()).containsExactly(extracted))
        .verifyComplete();

    verify(extractionService, times(1)).extractZipStream(any(), any());
  }

  /**
   * Verifies that a deeply nested path such as {@code lib/sub/deep.jar} does NOT match
   * the recognized layout patterns and is therefore silently ignored.
   */
  @Test
  void jarInUnrecognisedSubdirectory_ignored() throws Exception {
    Path fatJar = buildFatJar(tempDir, "fat.jar",
        java.util.Map.of("lib/sub/deep.jar", minimalJarBytes()));

    StepVerifier.create(service.extractNestedJars(fatJar, PROJECT))
        .verifyComplete();

    verify(extractionService, never()).extractZipStream(any(), any());
  }

  // ── Tests: dedup ─────────────────────────────────────────────────────────────

  @Test
  void alreadyIndexedNestedJar_skipped() throws Exception {
    Path fatJar = buildFatJar(tempDir, "app.jar",
        java.util.Map.of("BOOT-INF/lib/cached.jar", minimalJarBytes()));

    when(descriptorRepository.findByProjectAndVersionAndContentHash(
        eq(PROJECT), eq(VERSION), anyString()))
        .thenReturn(Mono.just(new UnitDescriptorEntity(
            1L, PROJECT, VERSION, "JAR", "cached.jar", 100L, "somehash")));

    StepVerifier.create(service.extractNestedJars(fatJar, PROJECT))
        .verifyComplete();

    verify(extractionService, never()).extractZipStream(any(), any());
  }

  @Test
  void onlyNewNestedJarsExtracted_cachedOnesSkipped() throws Exception {
    byte[] libBytes = minimalJarBytes();
    Path fatJar = buildFatJar(tempDir, "app.jar", java.util.Map.of(
        "BOOT-INF/lib/cached.jar", libBytes,
        "BOOT-INF/lib/fresh.jar", libBytes
    ));

    when(descriptorRepository.findByProjectAndVersionAndContentHash(
        eq(PROJECT), eq(VERSION), anyString()))
        .thenReturn(Mono.just(new UnitDescriptorEntity(
            1L, PROJECT, VERSION, "JAR", "cached.jar", 100L, "hash")))
        .thenReturn(Mono.empty());

    UnitConstants fresh = stubUnitConstants("fresh.jar");
    when(extractionService.extractZipStream(any(), any())).thenReturn(List.of(fresh));

    StepVerifier.create(service.extractNestedJars(fatJar, PROJECT))
        .assertNext(batch -> assertThat(batch.units()).containsExactly(fresh))
        .verifyComplete();

    verify(extractionService, times(1)).extractZipStream(any(), any());
  }

  // ── Tests: empty / edge cases ─────────────────────────────────────────────────

  @Test
  void noNestedJars_returnsEmptyList() throws Exception {
    Path fatJar = buildFatJar(tempDir, "plain.jar",
        java.util.Map.of("com/example/App.class", new byte[]{1, 2, 3}));

    StepVerifier.create(service.extractNestedJars(fatJar, PROJECT))
        .verifyComplete();

    verify(extractionService, never()).extractZipStream(any(), any());
  }

  @Test
  void corruptNestedJar_skippedGracefully_otherJarsStillExtracted() throws Exception {
    byte[] corrupt = new byte[]{0x00, 0x01, 0x02}; // not a valid ZIP
    Path fatJar = buildFatJar(tempDir, "app.jar", java.util.Map.of(
        "BOOT-INF/lib/corrupt.jar", corrupt,
        "BOOT-INF/lib/good.jar", minimalJarBytes()
    ));

    UnitConstants good = stubUnitConstants("good.jar");
    when(extractionService.extractZipStream(any(), any()))
        .thenThrow(new org.glodean.constants.extractor.ModelExtractor.ExtractionException("bad zip"))
        .thenReturn(List.of(good));

    StepVerifier.create(service.extractNestedJars(fatJar, PROJECT))
        .assertNext(batch -> assertThat(batch.units()).containsExactly(good))
        .verifyComplete();
  }

  @Test
  void descriptorCreatedWithCorrectJarName() throws Exception {
    byte[] libBytes = minimalJarBytes();
    Path fatJar = buildFatJar(tempDir, "app.jar",
        java.util.Map.of("BOOT-INF/lib/specific-name-1.0.0.jar", libBytes));

    when(extractionService.extractZipStream(any(), any())).thenReturn(List.of());

    StepVerifier.create(service.extractNestedJars(fatJar, PROJECT))
        .verifyComplete();

    var captor = org.mockito.ArgumentCaptor.forClass(UnitDescriptor.class);
    verify(extractionService).extractZipStream(any(), captor.capture());
    assertThat(captor.getValue().path()).isEqualTo("specific-name-1.0.0.jar");
  }
}
