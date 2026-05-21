package org.glodean.constants.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.glodean.constants.extractor.ModelExtractorSupplierRepository;
import org.glodean.constants.extractor.bytecode.AnalysisMerger;
import org.glodean.constants.extractor.bytecode.BytecodeSourceKind;
import org.glodean.constants.extractor.bytecode.ClassModelExtractor;
import org.glodean.constants.extractor.bytecode.ConstantUsageInterpreterRegistry;
import org.glodean.constants.extractor.bytecode.InternalStringConcatPatternSplitter;
import org.glodean.constants.extractor.bytecode.interpreters.LoggingConstantUsageInterpreter;
import org.glodean.constants.extractor.configfile.ConfigFileSourceKind;
import org.glodean.constants.extractor.configfile.PropertiesConstantsExtractor;
import org.glodean.constants.extractor.configfile.YamlConstantsExtractor;
import org.glodean.constants.model.SourceKind;
import org.glodean.constants.model.UnitConstant;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.model.UnitDescriptor;
import org.glodean.constants.store.JarBatch;
import org.glodean.constants.store.postgres.entity.ProjectVersionEntity;
import org.glodean.constants.store.postgres.repository.JarExtractionRepository;
import org.glodean.constants.store.postgres.repository.UnitDescriptorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

/**
 * Integration-style test verifying that constants are extracted from every source present in a
 * fat JAR — top-level bytecode, top-level config files, embedded-JAR bytecode, and embedded-JAR
 * config files — in a single combined pass.
 *
 * <p>The fat JAR layout exercised by this test:
 * <pre>
 *   fat.jar
 *   ├── com/example/Greeter.class          ← bytecode in the fat JAR
 *   ├── application.yml                    ← YAML config in the fat JAR
 *   └── BOOT-INF/lib/nested-lib.jar
 *       ├── com/example/Greeter.class      ← bytecode inside the embedded JAR
 *       └── nested.properties              ← properties config inside the embedded JAR
 * </pre>
 *
 * <p>All four sources must contribute at least one {@link UnitConstants} to the aggregated
 * extraction result. Real (non-mocked) extractors are wired manually so the test exercises
 * the full extraction pipeline without a Spring context. The only mocked collaborators are
 * the reactive store beans ({@link UnitDescriptorRepository} and
 * {@link ProjectVersionService}), which are not relevant to the extraction logic itself.
 *
 * <p>{@link ExtractionService} uses an <em>extension-based</em>
 * {@link ModelExtractorSupplierRepository} predicate for {@code .class} files
 * ({@code name -> name.endsWith(".class")}) so that the filename-driven file-system walker
 * inside {@link org.glodean.constants.extractor.bytecode.BytecodeModelExtractor} can dispatch
 * class entries correctly.
 */
@ExtendWith(MockitoExtension.class)
class FatJarMixedExtractionTest {

  @TempDir
  Path tempDir;

  @Mock
  UnitDescriptorRepository descriptorRepository;

  @Mock
  ProjectVersionService projectVersionService;

  @Mock
  JarExtractionRepository jarExtractionRepository;

  private ExtractionService extractionService;
  private NestedJarExtractionService nestedJarExtractionService;

  private static final String PROJECT = "fat-jar-mixed-test";
  private static final int VERSION = 1;

  /** Real YAML content that contributes several leaf constants (port, host, url, username). */
  private static final String FAT_JAR_YAML = """
      server:
        port: 8080
        host: localhost
      spring:
        datasource:
          url: https://db.example.com/mydb
          username: admin
      """;

  /** Real .properties content placed inside the nested JAR. */
  private static final String NESTED_JAR_PROPERTIES =
          """
                  nested.key=nested-value
                  nested.url=https://nested.example.com/api
                  nested.timeout=30
                  """;

  // ── Setup ─────────────────────────────────────────────────────────────────

  @BeforeEach
  void setUp() {
    // Wire real extraction infrastructure — no Spring context required.
    var splitter = new InternalStringConcatPatternSplitter();
    var registry = ConstantUsageInterpreterRegistry.builder()
        .register(UnitConstant.UsageType.METHOD_INVOCATION_PARAMETER,
            new LoggingConstantUsageInterpreter())
        .build();
    var merger = new AnalysisMerger(splitter, registry);
    var executor = Executors.newFixedThreadPool(2);

    // Extension-based predicate for .class files: required so that the filename-driven
    // fileSystemFeeder / zipStreamFeeder inside BytecodeModelExtractor can match entries
    // like "Greeter.class" — the convenience register(SourceKind, factory) variant matches
    // by enum name ("CLASS_FILE"), which is not useful for filename-based dispatch.
    var repository = ModelExtractorSupplierRepository.builder()
        .register(
            name -> name.endsWith(".class"),
            BytecodeSourceKind.CLASS_FILE,
            ClassModelExtractor.supplier(merger))
        .register(
            name -> name.endsWith(".yml") || name.endsWith(".yaml"),
            ConfigFileSourceKind.YAML,
            YamlConstantsExtractor::new)
        .register(
            name -> name.endsWith(".properties"),
            ConfigFileSourceKind.PROPERTIES,
            PropertiesConstantsExtractor::new)
        .build();

    extractionService = new ConcreteExtractionService(merger, executor, repository);

    // Reactive store stubs — always looks like a fresh project/version with no cached entries.
    when(projectVersionService.getOrCreateOpenVersion(PROJECT))
        .thenReturn(Mono.just(new ProjectVersionEntity(
            1L, PROJECT, VERSION, null, ProjectVersionEntity.STATUS_OPEN, null, null)));
    when(descriptorRepository.findByProjectAndVersionAndContentHash(
        eq(PROJECT), eq(VERSION), anyString()))
        .thenReturn(Mono.empty());


    nestedJarExtractionService = new NestedJarExtractionService(
        extractionService, descriptorRepository, projectVersionService, jarExtractionRepository, 500);
  }

  // ── Test ──────────────────────────────────────────────────────────────────

  /**
   * Verifies the full extraction coverage of a fat JAR that contains bytecode and config files
   * both at the top level and inside an embedded JAR.
   */
  @Test
  void fatJar_extractsConstantsFromAllFourSources() throws Exception {
    // Load the pre-compiled Greeter sample class (real bytecode with string constants).
    byte[] greeterBytes = Files.readAllBytes(
        Path.of("src/test/resources/samples/Greeter.class"));

    // Build the nested JAR in memory — it carries bytecode and a .properties file.
    byte[] nestedJarBytes = buildZipInMemory(zip -> {
      zip.entry("com/example/Greeter.class", greeterBytes);
      zip.entry("nested.properties", NESTED_JAR_PROPERTIES.getBytes());
    });

    // Build the fat JAR on disk — top-level class, top-level YAML, and the nested JAR.
    Path fatJar = buildZipOnDisk(tempDir, zip -> {
      zip.entry("com/example/Greeter.class", greeterBytes);
      zip.entry("application.yml", FAT_JAR_YAML.getBytes());
      zip.entry("BOOT-INF/lib/nested-lib.jar", nestedJarBytes);
    });

    // ── Step 1: extract top-level contents of the fat JAR ─────────────────────
    var fatDescriptor = new UnitDescriptor(BytecodeSourceKind.JAR, "fat.jar");
    Collection<UnitConstants> fromFatJar = extractionService.extractJarFile(fatJar, fatDescriptor);

    assertThat(fromFatJar)
        .as("fat JAR top-level extraction must not be empty")
        .isNotEmpty();

    Set<SourceKind> fatKinds = fromFatJar.stream()
        .map(u -> u.source().sourceKind())
        .collect(Collectors.toSet());

    assertThat(fatKinds)
        .as("fat JAR must produce CLASS_FILE constants (Greeter.class)")
        .contains(BytecodeSourceKind.CLASS_FILE);

    assertThat(fatKinds)
        .as("fat JAR must produce YAML constants (application.yml)")
        .contains(ConfigFileSourceKind.YAML);

    // Spot-check: at least one of the known YAML leaf values must appear.
    Set<String> fatValues = fatJar_stringValues(fromFatJar, ConfigFileSourceKind.YAML);
    assertThat(fatValues)
        .as("YAML constants from fat JAR must include known leaf values")
        .containsAnyOf("8080", "localhost", "admin");

    // ── Step 2: extract nested JARs found inside the fat JAR ──────────────────
    // Each nested JAR emits one List<UnitConstants> — flatten before asserting.
    List<UnitConstants> fromNested = nestedJarExtractionService
        .extractNestedJars(fatJar, PROJECT)
        .flatMapIterable(JarBatch::units)
        .collectList()
        .block();

    assertThat(fromNested)
        .as("nested JAR extraction must not be empty")
        .isNotEmpty();

    Set<SourceKind> nestedKinds = fromNested.stream()
        .map(u -> u.source().sourceKind())
        .collect(Collectors.toSet());

    assertThat(nestedKinds)
        .as("nested-lib.jar must produce CLASS_FILE constants (Greeter.class)")
        .contains(BytecodeSourceKind.CLASS_FILE);

    assertThat(nestedKinds)
        .as("nested-lib.jar must produce PROPERTIES constants (nested.properties)")
        .contains(ConfigFileSourceKind.PROPERTIES);

    // Spot-check: the known property value must be present.
    Set<String> nestedPropValues = fatJar_stringValues(fromNested, ConfigFileSourceKind.PROPERTIES);
    assertThat(nestedPropValues)
        .as("properties constants from nested JAR must include known values")
        .containsAnyOf("nested-value", "30");

    // The embedded JAR must NOT have been double-counted in the fat JAR result.
    assertThat(fromFatJar.stream()
        .map(u -> u.source().sourceKind())
        .collect(Collectors.toSet()))
        .as("fat JAR extraction must not include PROPERTIES (belongs to nested JAR)")
        .doesNotContain(ConfigFileSourceKind.PROPERTIES);
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  /**
   * Collects the string representations of all constant values from units whose
   * {@link UnitDescriptor#sourceKind()} equals {@code kind}.
   */
  private static Set<String> fatJar_stringValues(
      Collection<UnitConstants> units, SourceKind kind) {
    return units.stream()
        .filter(u -> u.source().sourceKind() == kind)
        .flatMap(u -> u.constants().stream())
        .map(c -> String.valueOf(c.value()))
        .collect(Collectors.toSet());
  }

  // ── ZIP construction helpers ───────────────────────────────────────────────

  /** Simple builder interface passed to the zip-construction lambdas. */
  @FunctionalInterface
  interface ZipEntryWriter {
    void entry(String name, byte[] bytes) throws IOException;
  }

  @FunctionalInterface
  interface ZipBuildAction {
    void build(ZipEntryWriter writer) throws IOException;
  }

  /** Builds a ZIP archive in memory and returns its bytes. */
  private static byte[] buildZipInMemory(ZipBuildAction action) throws IOException {
    try (var baos = new ByteArrayOutputStream();
         var zos = new ZipOutputStream(baos)) {
      action.build((name, bytes) -> {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(bytes);
        zos.closeEntry();
      });
      zos.finish();
      return baos.toByteArray();
    }
  }

  /** Builds a ZIP archive in {@code dir} with the given {@code fileName} and returns its path. */
  private static Path buildZipOnDisk(Path dir, ZipBuildAction action)
      throws IOException {
    byte[] bytes = buildZipInMemory(action);
    Path out = dir.resolve("fat.jar");
    Files.write(out, bytes);
    return out;
  }
}
