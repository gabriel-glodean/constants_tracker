package org.glodean.constants.extractor.bytecode;

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.glodean.constants.extractor.ExtractionNotifier;
import org.glodean.constants.extractor.ModelExtractor;
import org.glodean.constants.extractor.ModelExtractorSupplierRepository;
import org.glodean.constants.model.UnitConstant;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.model.UnitDescriptor;
import org.glodean.constants.samples.Greeter;
import org.glodean.constants.samples.SimpleIteration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BytecodeModelExtractor} — covers all factory methods and error paths.
 *
 * <p><strong>Repository note:</strong> The internal {@code defaultRepository()} inside
 * {@code BytecodeModelExtractor} matches entries whose name equals the {@link BytecodeSourceKind}
 * enum constant name (e.g. {@code "CLASS_FILE"}), not by file extension. Tests that need
 * to actually extract bytecode constants therefore use the shared-executor variants with
 * an explicit extension-based {@link ModelExtractorSupplierRepository}.
 */
@DisplayName("BytecodeModelExtractor Tests")
class BytecodeModelExtractorTest {

  private AnalysisMerger merger;

  @BeforeEach
  void setUp() {
    merger = new AnalysisMerger(new InternalStringConcatPatternSplitter());
  }

  // -------------------------------------------------------------------------
  // Utility helpers
  // -------------------------------------------------------------------------

  private static byte[] loadClassBytes(Class<?> clazz) throws IOException {
    String resource = clazz.getName().replace('.', '/') + ".class";
    try (InputStream is = clazz.getClassLoader().getResourceAsStream(resource)) {
      assertNotNull(is, "Could not locate class resource: " + resource);
      return is.readAllBytes();
    }
  }

  /**
   * A repository that matches {@code .class} files by extension —
   * required for actual extraction when file names follow the usual convention.
   */
  private ModelExtractorSupplierRepository extensionRepository() {
    return ModelExtractorSupplierRepository.builder()
        .register(
            name -> name.endsWith(".class"),
            BytecodeSourceKind.CLASS_FILE,
            ClassModelExtractor.supplier(merger))
        .build();
  }

  private FileSystem buildSingleClassFs(Class<?> clazz) throws IOException {
    FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
    Path dir = Files.createDirectories(fs.getPath("/classes"));
    Files.write(dir.resolve(clazz.getSimpleName() + ".class"), loadClassBytes(clazz));
    return fs;
  }

  private static byte[] buildZip(String entryName, byte[] content) throws IOException {
    var baos = new ByteArrayOutputStream();
    try (var zos = new ZipOutputStream(baos)) {
      zos.putNextEntry(new ZipEntry(entryName));
      zos.write(content);
      zos.closeEntry();
    }
    return baos.toByteArray();
  }

  private static ZipInputStream toZipInputStream(byte[] zipBytes) {
    return new ZipInputStream(new ByteArrayInputStream(zipBytes));
  }

  private static UnitDescriptor anyDescriptor() {
    return new UnitDescriptor(BytecodeSourceKind.DIRECTORY, "/classes");
  }

  // -------------------------------------------------------------------------
  // forFileSystem — standalone (no executor argument)
  // The internal default repository matches ".class" files by extension,
  // so real class files are extracted correctly.
  // -------------------------------------------------------------------------

  @Nested
  @DisplayName("forFileSystem — standalone (internal default repository)")
  class FileSystemStandaloneTests {

    @Test
    @DisplayName("Extracts constants from .class files using the default extension-matched repository")
    void extractsConstantsFromClassNamedFiles() throws Exception {
      try (FileSystem fs = buildSingleClassFs(Greeter.class)) {
        var extractor = BytecodeModelExtractor.forFileSystem(fs, merger);
        assertFalse(extractor.extract(anyDescriptor()).isEmpty(),
            "Default repo matches .class extension — expected non-empty results");
      }
    }

    @Test
    @DisplayName("Does not crash with an ignorePathPrefix when nothing is matched")
    void withPrefixFilterDoesNotCrash() throws Exception {
      try (FileSystem fs = buildSingleClassFs(Greeter.class)) {
        var extractor = BytecodeModelExtractor.forFileSystem(fs, merger, "/skip");
        assertDoesNotThrow(() -> extractor.extract(anyDescriptor()));
      }
    }

    @Test
    @DisplayName("Empty filesystem — extract returns empty without error")
    void emptyFilesystem() throws Exception {
      try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
        Files.createDirectories(fs.getPath("/classes"));
        var extractor = BytecodeModelExtractor.forFileSystem(fs, merger);
        assertTrue(extractor.extract(anyDescriptor()).isEmpty());
      }
    }

    @Test
    @DisplayName("Silent-notifier factory overload runs without throwing")
    void silentNotifierVariantDoesNotThrow() throws Exception {
      try (FileSystem fs = buildSingleClassFs(Greeter.class)) {
        var extractor = BytecodeModelExtractor.forFileSystem(fs, merger);
        assertDoesNotThrow(() -> extractor.extract(anyDescriptor()));
      }
    }

    @Test
    @DisplayName("Explicit null-prefix + silent-notifier overload runs without throwing")
    void explicitNullPrefixSilentVariant() throws Exception {
      try (FileSystem fs = buildSingleClassFs(Greeter.class)) {
        var extractor = BytecodeModelExtractor.forFileSystem(
            fs, merger, (String) null, new ExtractionNotifier.Silent());
        assertDoesNotThrow(() -> extractor.extract(anyDescriptor()));
      }
    }

    @Test
    @DisplayName("Standalone extract() can be called multiple times without error")
    void canBeCalledMultipleTimes() throws Exception {
      try (FileSystem fs = buildSingleClassFs(Greeter.class)) {
        var extractor = BytecodeModelExtractor.forFileSystem(fs, merger);
        assertDoesNotThrow(() -> extractor.extract(anyDescriptor()));
        assertDoesNotThrow(() -> extractor.extract(anyDescriptor()));
      }
    }
  }

  // -------------------------------------------------------------------------
  // forFileSystem — shared executor + explicit extension-based repository
  // -------------------------------------------------------------------------

  @Nested
  @DisplayName("forFileSystem — shared executor + extension repository")
  class FileSystemSharedExecutorTests {

    @Test
    @DisplayName("Extracts constants from Greeter.class in an in-memory filesystem")
    void extractsFromClassFile() throws Exception {
      try (FileSystem fs = buildSingleClassFs(Greeter.class);
           ExecutorService exec = Executors.newFixedThreadPool(2)) {

        var extractor = BytecodeModelExtractor.forFileSystem(
            exec, fs, new ExtractionNotifier.Silent(), extensionRepository());
        Collection<UnitConstants> result = extractor.extract(anyDescriptor());

        assertFalse(result.isEmpty(), "Should find at least one UnitConstants for Greeter");
        assertFalse(result.iterator().next().constants().isEmpty(),
            "Greeter should have at least one extracted constant");
      }
    }

    @Test
    @DisplayName("Extracts integer constants from SimpleIteration.class")
    void extractsIntegerConstants() throws Exception {
      try (FileSystem fs = buildSingleClassFs(SimpleIteration.class);
           ExecutorService exec = Executors.newFixedThreadPool(2)) {

        var extractor = BytecodeModelExtractor.forFileSystem(
            exec, fs, new ExtractionNotifier.Silent(), extensionRepository());
        Collection<UnitConstants> result = extractor.extract(anyDescriptor());

        assertFalse(result.isEmpty());
        Set<Object> values = new java.util.HashSet<>();
        for (UnitConstant c : result.iterator().next().constants()) values.add(c.value());
        assertTrue(values.contains(0) || values.contains(1),
            "Expected constants 0 or 1 from SimpleIteration, got: " + values);
      }
    }

    @Test
    @DisplayName("Extracts from multiple class files in the same directory")
    void extractsMultipleClasses() throws Exception {
      try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
           ExecutorService exec = Executors.newFixedThreadPool(4)) {

        Path dir = Files.createDirectories(fs.getPath("/classes"));
        Files.write(dir.resolve("Greeter.class"), loadClassBytes(Greeter.class));
        Files.write(dir.resolve("SimpleIteration.class"), loadClassBytes(SimpleIteration.class));

        var extractor = BytecodeModelExtractor.forFileSystem(
            exec, fs, new ExtractionNotifier.Silent(), extensionRepository());
        Collection<UnitConstants> result = extractor.extract(anyDescriptor());

        assertEquals(2, result.size(), "Both class files should produce one UnitConstants each");
      }
    }

    @Test
    @DisplayName("Non-.class files are ignored by the extension repository")
    void ignoresNonClassFiles() throws Exception {
      try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
           ExecutorService exec = Executors.newFixedThreadPool(2)) {

        Path dir = Files.createDirectories(fs.getPath("/classes"));
        Files.writeString(dir.resolve("readme.txt"), "not a class");
        Files.write(dir.resolve("Greeter.class"), loadClassBytes(Greeter.class));

        var extractor = BytecodeModelExtractor.forFileSystem(
            exec, fs, new ExtractionNotifier.Silent(), extensionRepository());
        Collection<UnitConstants> result = extractor.extract(anyDescriptor());

        assertEquals(1, result.size(), "Only the .class file should produce a result");
      }
    }

    @Test
    @DisplayName("Path prefix filter excludes files whose path starts with the prefix")
    void pathPrefixFilterExcludesMatchingPaths() throws Exception {
      try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
           ExecutorService exec = Executors.newFixedThreadPool(2)) {

        Path keepDir = Files.createDirectories(fs.getPath("/keep"));
        Path skipDir = Files.createDirectories(fs.getPath("/skip"));
        Files.write(keepDir.resolve("SimpleIteration.class"), loadClassBytes(SimpleIteration.class));
        Files.write(skipDir.resolve("Greeter.class"), loadClassBytes(Greeter.class));

        var extractor = BytecodeModelExtractor.forFileSystem(
            exec, fs, "/skip", new ExtractionNotifier.Silent(), extensionRepository());
        Collection<UnitConstants> result = extractor.extract(anyDescriptor());

        assertEquals(1, result.size(), "Only the file outside /skip should be extracted");
        assertTrue(result.iterator().next().source().path().contains("SimpleIteration"),
            "Retained class should be SimpleIteration");
      }
    }

    @Test
    @DisplayName("Null prefix means no filtering — extracts all class files")
    void nullPrefixMeansNoFilter() throws Exception {
      try (FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
           ExecutorService exec = Executors.newFixedThreadPool(2)) {

        Path dir = Files.createDirectories(fs.getPath("/classes"));
        Files.write(dir.resolve("Greeter.class"), loadClassBytes(Greeter.class));
        Files.write(dir.resolve("SimpleIteration.class"), loadClassBytes(SimpleIteration.class));

        var extractor = BytecodeModelExtractor.forFileSystem(
            exec, fs, (String) null, new ExtractionNotifier.Silent(), extensionRepository());
        Collection<UnitConstants> result = extractor.extract(anyDescriptor());

        assertEquals(2, result.size(), "Both class files should be extracted");
      }
    }

    @Test
    @DisplayName("Shared executor is NOT shut down after extract()")
    void sharedExecutorNotShutDown() throws Exception {
      try (FileSystem fs = buildSingleClassFs(Greeter.class);
           ExecutorService exec = Executors.newFixedThreadPool(2)) {

        var extractor = BytecodeModelExtractor.forFileSystem(
            exec, fs, new ExtractionNotifier.Silent(), extensionRepository());
        extractor.extract(anyDescriptor());

        assertFalse(exec.isShutdown(), "Shared executor must not be shut down by the extractor");
      }
    }

    @Test
    @DisplayName("Notifier receives full lifecycle callbacks during extraction")
    void notifierReceivesLifecycleCallbacks() throws Exception {
      var notifier = new ExtractionPoolTest.TrackingNotifier();
      try (FileSystem fs = buildSingleClassFs(Greeter.class);
           ExecutorService exec = Executors.newFixedThreadPool(2)) {

        var extractor = BytecodeModelExtractor.forFileSystem(
            exec, fs, notifier, extensionRepository());
        extractor.extract(anyDescriptor());
      }

      assertTrue(notifier.startedThreadCount > 0, "onExtractionStarted should fire");
      assertEquals(1, notifier.processedClasses.size(), "onProcessingClass should fire once");
      assertEquals(1L, notifier.completedTotal, "onExtractionCompleted: 1 processed");
      assertEquals(0L, notifier.completedErrors, "No errors expected");
    }
  }

  // -------------------------------------------------------------------------
  // forZipStream — standalone (internal default repository)
  // -------------------------------------------------------------------------

  @Nested
  @DisplayName("forZipStream — standalone (internal default repository)")
  class ZipStreamStandaloneTests {

    @Test
    @DisplayName("Extracts constants from .class-named entries using the default extension-matched repository")
    void extractsConstantsFromClassNamedEntries() throws Exception {
      byte[] zipBytes = buildZip("Greeter.class", loadClassBytes(Greeter.class));
      try (ZipInputStream zis = toZipInputStream(zipBytes)) {
        var extractor = BytecodeModelExtractor.forZipStream(zis, merger);
        assertFalse(extractor.extract(anyDescriptor()).isEmpty(),
            "Default repo matches .class extension — expected non-empty results");
      }
    }

    @Test
    @DisplayName("Does not crash on an empty ZIP stream")
    void doesNotCrashOnEmptyZip() throws Exception {
      var baos = new ByteArrayOutputStream();
      try (var ignored = new ZipOutputStream(baos)) { /* empty */ }
      try (ZipInputStream zis = toZipInputStream(baos.toByteArray())) {
        var extractor = BytecodeModelExtractor.forZipStream(zis, merger);
        assertDoesNotThrow(() -> extractor.extract(anyDescriptor()));
      }
    }

    @Test
    @DisplayName("Skips directory entries without error")
    void skipsDirectoryEntries() throws Exception {
      var baos = new ByteArrayOutputStream();
      try (var zos = new ZipOutputStream(baos)) {
        zos.putNextEntry(new ZipEntry("org/"));
        zos.closeEntry();
        zos.putNextEntry(new ZipEntry("org/Greeter.class"));
        zos.write(loadClassBytes(Greeter.class));
        zos.closeEntry();
      }
      try (ZipInputStream zis = toZipInputStream(baos.toByteArray())) {
        var extractor = BytecodeModelExtractor.forZipStream(zis, merger);
        assertDoesNotThrow(() -> extractor.extract(anyDescriptor()));
      }
    }

    @Test
    @DisplayName("Silent-notifier overload runs without throwing")
    void silentNotifierVariant() throws Exception {
      byte[] zipBytes = buildZip("Greeter.class", loadClassBytes(Greeter.class));
      try (ZipInputStream zis = toZipInputStream(zipBytes)) {
        var extractor = BytecodeModelExtractor.forZipStream(zis, merger, new ExtractionNotifier.Silent());
        assertDoesNotThrow(() -> extractor.extract(anyDescriptor()));
      }
    }
  }

  // -------------------------------------------------------------------------
  // forZipStream — shared executor + explicit extension-based repository
  // -------------------------------------------------------------------------

  @Nested
  @DisplayName("forZipStream — shared executor + extension repository")
  class ZipStreamSharedExecutorTests {

    @Test
    @DisplayName("Extracts constants from a class inside a ZIP stream")
    void extractsFromClassInsideZip() throws Exception {
      byte[] zipBytes = buildZip("org/glodean/Greeter.class", loadClassBytes(Greeter.class));
      try (ZipInputStream zis = toZipInputStream(zipBytes);
           ExecutorService exec = Executors.newFixedThreadPool(2)) {
        var extractor = BytecodeModelExtractor.forZipStream(exec, zis, extensionRepository());
        Collection<UnitConstants> result = extractor.extract(anyDescriptor());

        assertFalse(result.isEmpty(), "Should extract constants from Greeter.class in the ZIP");
        assertFalse(result.iterator().next().constants().isEmpty(),
            "Greeter should have at least one extracted constant");
      }
    }

    @Test
    @DisplayName("Extracts from multiple class entries in a ZIP")
    void extractsMultipleClassesFromZip() throws Exception {
      var baos = new ByteArrayOutputStream();
      try (var zos = new ZipOutputStream(baos)) {
        zos.putNextEntry(new ZipEntry("Greeter.class"));
        zos.write(loadClassBytes(Greeter.class));
        zos.closeEntry();
        zos.putNextEntry(new ZipEntry("SimpleIteration.class"));
        zos.write(loadClassBytes(SimpleIteration.class));
        zos.closeEntry();
      }
      try (ZipInputStream zis = toZipInputStream(baos.toByteArray());
           ExecutorService exec = Executors.newFixedThreadPool(4)) {
        var extractor = BytecodeModelExtractor.forZipStream(exec, zis, extensionRepository());
        Collection<UnitConstants> result = extractor.extract(anyDescriptor());

        assertEquals(2, result.size(), "Both class entries should produce one UnitConstants each");
      }
    }

    @Test
    @DisplayName("Non-.class entries are skipped by the extension repository")
    void skipsNonClassEntries() throws Exception {
      var baos = new ByteArrayOutputStream();
      try (var zos = new ZipOutputStream(baos)) {
        zos.putNextEntry(new ZipEntry("readme.txt"));
        zos.write("hello".getBytes());
        zos.closeEntry();
        zos.putNextEntry(new ZipEntry("Greeter.class"));
        zos.write(loadClassBytes(Greeter.class));
        zos.closeEntry();
      }
      try (ZipInputStream zis = toZipInputStream(baos.toByteArray());
           ExecutorService exec = Executors.newFixedThreadPool(2)) {
        var extractor = BytecodeModelExtractor.forZipStream(exec, zis, extensionRepository());
        assertEquals(1, extractor.extract(anyDescriptor()).size(),
            "Only the .class entry should produce a result");
      }
    }

    @Test
    @DisplayName("Directory-style ZIP entries are skipped gracefully")
    void skipsDirectoryEntries() throws Exception {
      var baos = new ByteArrayOutputStream();
      try (var zos = new ZipOutputStream(baos)) {
        zos.putNextEntry(new ZipEntry("org/"));
        zos.closeEntry();
        zos.putNextEntry(new ZipEntry("org/Greeter.class"));
        zos.write(loadClassBytes(Greeter.class));
        zos.closeEntry();
      }
      try (ZipInputStream zis = toZipInputStream(baos.toByteArray());
           ExecutorService exec = Executors.newFixedThreadPool(2)) {
        var extractor = BytecodeModelExtractor.forZipStream(exec, zis, extensionRepository());
        assertEquals(1, extractor.extract(anyDescriptor()).size(),
            "Directory entry must be skipped");
      }
    }

    @Test
    @DisplayName("Shared executor is NOT shut down after extract()")
    void sharedExecutorNotShutDown() throws Exception {
      byte[] zipBytes = buildZip("Greeter.class", loadClassBytes(Greeter.class));
      try (ZipInputStream zis = toZipInputStream(zipBytes);
           ExecutorService exec = Executors.newFixedThreadPool(2)) {
        BytecodeModelExtractor.forZipStream(exec, zis, extensionRepository())
            .extract(anyDescriptor());
        assertFalse(exec.isShutdown(), "Shared executor must not be shut down by the extractor");
      }
    }

    @Test
    @DisplayName("Notifier receives lifecycle callbacks during ZIP extraction")
    void notifierReceivesCallbacks() throws Exception {
      byte[] zipBytes = buildZip("Greeter.class", loadClassBytes(Greeter.class));
      var notifier = new ExtractionPoolTest.TrackingNotifier();
      try (ZipInputStream zis = toZipInputStream(zipBytes);
           ExecutorService exec = Executors.newFixedThreadPool(2)) {
        BytecodeModelExtractor.forZipStream(exec, zis, notifier, extensionRepository())
            .extract(anyDescriptor());
      }
      assertTrue(notifier.startedThreadCount > 0, "onExtractionStarted should fire");
      assertEquals(1, notifier.processedClasses.size(), "onProcessingClass should fire once");
      assertEquals(0L, notifier.completedErrors, "No errors expected");
    }

    @Test
    @DisplayName("Returns empty for a truly empty ZIP")
    void returnsEmptyForEmptyZip() throws Exception {
      var baos = new ByteArrayOutputStream();
      try (var ignored = new ZipOutputStream(baos)) { /* empty */ }
      try (ZipInputStream zis = toZipInputStream(baos.toByteArray());
           ExecutorService exec = Executors.newFixedThreadPool(2)) {
        assertTrue(
            BytecodeModelExtractor.forZipStream(exec, zis, extensionRepository())
                .extract(anyDescriptor()).isEmpty());
      }
    }
  }

  // -------------------------------------------------------------------------
  // ExtractionException propagation
  // -------------------------------------------------------------------------

  @Nested
  @DisplayName("ExtractionException wrapping")
  class ExtractionExceptionTests {

    @Test
    @DisplayName("Wraps IOException from a failing ZipInputStream in ExtractionException")
    void wrapsIoExceptionFromFailingZipStream() {
      // An InputStream that throws on every read — forces ZipInputStream.getNextEntry() to throw
      var throwingStream = new java.io.InputStream() {
        @Override public int read() throws IOException { throw new IOException("simulated IO error"); }
        @Override public int read(byte[] b, int off, int len) throws IOException {
          throw new IOException("simulated IO error");
        }
      };
      var zis = new ZipInputStream(throwingStream);
      var extractor = BytecodeModelExtractor.forZipStream(zis, merger);

      assertThrows(
          ModelExtractor.ExtractionException.class,
          () -> extractor.extract(anyDescriptor()),
          "IOException from ZipInputStream should be wrapped in ExtractionException");
    }

    @Test
    @DisplayName("Closing a Jimfs filesystem causes ClosedFileSystemException (RuntimeException — not wrapped)")
    void closedJimfsFsThrowsRuntimeException() throws Exception {
      FileSystem fs = buildSingleClassFs(Greeter.class);
      var extractor = BytecodeModelExtractor.forFileSystem(fs, merger);
      fs.close();

      // ClosedFileSystemException extends IllegalStateException (RuntimeException),
      // so it propagates through the IOException catch and is NOT wrapped.
      assertThrows(
          java.nio.file.ClosedFileSystemException.class,
          () -> extractor.extract(anyDescriptor()));
    }
  }

  // -------------------------------------------------------------------------
  // Custom ModelExtractorSupplierRepository
  // -------------------------------------------------------------------------

  @Nested
  @DisplayName("Custom ModelExtractorSupplierRepository")
  class CustomRepositoryTests {

    @Test
    @DisplayName("Repository matching nothing produces empty results even with .class files")
    void repositoryMatchingNothingProducesEmpty() throws Exception {
      var repo = ModelExtractorSupplierRepository.builder()
          .register(
              name -> name.endsWith(".properties"),
              BytecodeSourceKind.CLASS_FILE,
              bytes -> source -> List.of(new UnitConstants(source, Set.of())))
          .build();

      try (FileSystem fs = buildSingleClassFs(Greeter.class);
           ExecutorService exec = Executors.newFixedThreadPool(2)) {
        var extractor = BytecodeModelExtractor.forFileSystem(
            exec, fs, new ExtractionNotifier.Silent(), repo);
        assertTrue(extractor.extract(anyDescriptor()).isEmpty(),
            "Repository that doesn't match .class should yield nothing");
      }
    }

    @Test
    @DisplayName("ClassModelExtractor.supplier() resolve by .class extension succeeds")
    void classModelExtractorSupplierResolvesByExtension() throws Exception {
      var repo = ModelExtractorSupplierRepository.builder()
          .register(name -> name.endsWith(".class"), BytecodeSourceKind.CLASS_FILE,
              ClassModelExtractor.supplier(merger))
          .build();

      var supply = repo.resolve("Greeter.class", loadClassBytes(Greeter.class));
      assertTrue(supply.isPresent(), "Resolution by .class extension should succeed");

      var result = supply.get().extractor().extract(
          new UnitDescriptor(BytecodeSourceKind.CLASS_FILE, "Greeter.class"));
      assertFalse(result.isEmpty(), "Extractor from supplier should produce constants");
    }
  }
}
