package org.glodean.constants.services;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
        ConcreteExtractionService svc = new ConcreteExtractionService(null, null, ModelExtractorSupplierRepository.builder().build());
        Collection<UnitConstants> result = svc.extractJarFile(jarPath, descriptor);
        assertNotNull(result, "extractJarFile should return a non-null collection");
    }

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
