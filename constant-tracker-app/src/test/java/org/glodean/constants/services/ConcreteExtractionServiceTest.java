package org.glodean.constants.services;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.glodean.constants.extractor.ModelExtractor;
import org.glodean.constants.extractor.ModelExtractorSupplierRepository;
import org.glodean.constants.extractor.bytecode.BytecodeSourceKind;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.model.UnitDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for ConcreteExtractionService focusing on basic extraction.
 */
class ConcreteExtractionServiceTest {

    @TempDir
    Path tempDir;

    // ── extractJarFile ────────────────────────────────────────────────────────

    @Test
    void extractJarFile_returnsNonNullResult() throws Exception {
        byte[] zip = createZipBytes(b -> {
            try {
                ZipEntry file = new ZipEntry("A.class");
                b.putNextEntry(file);
                b.write(new byte[]{0, 1, 2});
                b.closeEntry();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        Path jarPath = tempDir.resolve("test.jar");
        Files.write(jarPath, zip);

        var descriptor = new UnitDescriptor(BytecodeSourceKind.JAR, "test.jar");
        ConcreteExtractionService svc = new ConcreteExtractionService(
                null, null, ModelExtractorSupplierRepository.builder().build());
        Collection<UnitConstants> result = svc.extractJarFile(jarPath, descriptor);
        assertNotNull(result, "extractJarFile should return a non-null collection");
    }

    @Test
    void extractJarFile_throwsExtractionExceptionForInvalidPath() {
        Path nonExistent = tempDir.resolve("does-not-exist.jar");
        var descriptor = new UnitDescriptor(BytecodeSourceKind.JAR, "does-not-exist.jar");
        ConcreteExtractionService svc = new ConcreteExtractionService(
                null, null, ModelExtractorSupplierRepository.builder().build());

        assertThrows(ModelExtractor.ExtractionException.class,
                () -> svc.extractJarFile(nonExistent, descriptor),
                "extractJarFile on a non-existent path should throw ExtractionException");
    }

    // ── extractClassFile ──────────────────────────────────────────────────────

    @Test
    void extractClassFile_throwsExceptionWhenNoExtractorRegistered() {
        var descriptor = new UnitDescriptor(BytecodeSourceKind.CLASS_FILE, "com/example/Foo.class");
        ConcreteExtractionService svc = new ConcreteExtractionService(
                null, null, ModelExtractorSupplierRepository.builder().build());

        assertThrows(ModelExtractor.ExtractionException.class,
                () -> svc.extractClassFile(new byte[]{1, 2, 3}, descriptor),
                "extractClassFile with no matching extractor should throw ExtractionException");
    }

    // ── extractZipStream ──────────────────────────────────────────────────────

    @Test
    void extractZipStream_returnsEmptyForEmptyZip() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        try {
            var descriptor = new UnitDescriptor(BytecodeSourceKind.JAR, "test.jar");
            ConcreteExtractionService svc = new ConcreteExtractionService(
                    null, executor, ModelExtractorSupplierRepository.builder().build());

            byte[] zip = createZipBytes(ignore -> {});
            try (var zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
                Collection<UnitConstants> result = svc.extractZipStream(zis, descriptor);
                assertNotNull(result, "extractZipStream should never return null");
                assertTrue(result.isEmpty(), "Empty zip should produce empty result");
            }
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void extractZipStream_ignoresUnrecognisedEntries() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        try {
            var descriptor = new UnitDescriptor(BytecodeSourceKind.JAR, "test.jar");
            ConcreteExtractionService svc = new ConcreteExtractionService(
                    null, executor, ModelExtractorSupplierRepository.builder().build());

            byte[] zip = createZipBytes(zos -> {
                try {
                    zos.putNextEntry(new ZipEntry("README.txt"));
                    zos.write("hello".getBytes());
                    zos.closeEntry();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            try (var zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
                Collection<UnitConstants> result = svc.extractZipStream(zis, descriptor);
                assertNotNull(result);
                assertTrue(result.isEmpty(), "No registered extractor → result should be empty");
            }
        } finally {
            executor.shutdown();
        }
    }

    // ── extractJarFileStreaming ────────────────────────────────────────────────

    @Test
    void extractJarFileStreaming_completesWithoutEmittingWhenNoExtractorRegistered() throws Exception {
        byte[] zip = createZipBytes(zos -> {
            try {
                zos.putNextEntry(new ZipEntry("A.class"));
                zos.write(new byte[]{0, 1, 2});
                zos.closeEntry();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        Path jarPath = tempDir.resolve("stream.jar");
        Files.write(jarPath, zip);

        var executor = Executors.newSingleThreadExecutor();
        try {
            ConcreteExtractionService svc = new ConcreteExtractionService(
                    null, executor, ModelExtractorSupplierRepository.builder().build());

            List<List<UnitConstants>> batches =
                    svc.extractJarFileStreaming(jarPath, 100)
                       .collectList()
                       .block();

            assertNotNull(batches, "Flux should complete and collectList should not be null");
            // No extractor registered → all chunks are empty → filtered out → 0 emitted batches
            assertTrue(batches.isEmpty(), "Expected no batches when no extractor matches");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void extractJarFileStreaming_emitsNothingForEmptyJar() throws Exception {
        byte[] zip = createZipBytes(ignore -> {});
        Path jarPath = tempDir.resolve("empty.jar");
        Files.write(jarPath, zip);

        var executor = Executors.newSingleThreadExecutor();
        try {
             ConcreteExtractionService svc = new ConcreteExtractionService(
                    null, executor, ModelExtractorSupplierRepository.builder().build());

            List<List<UnitConstants>> batches =
                    svc.extractJarFileStreaming(jarPath, 10)
                       .collectList()
                       .block();

            assertNotNull(batches);
            assertTrue(batches.isEmpty(), "Empty JAR should produce no batches");
        } finally {
            executor.shutdown();
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static byte[] createZipBytes(ZipWriter writer) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            writer.write(zos);
            zos.finish();
            return baos.toByteArray();
        }
    }

    @FunctionalInterface
    private interface ZipWriter {
        void write(ZipOutputStream zos) throws IOException;
    }
}
