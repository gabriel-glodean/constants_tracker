package org.glodean.constants.services;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

/**
 * Tests for ConcreteExtractionService focusing on the jar->Jimfs conversion and basic extractor
 * creation. Tests keep to small, concrete behavior so they don't rely on complex project internals.
 */
class ConcreteExtractionServiceTest {

  @Test
  void fromZipBytesWithJimfs_createsDirectoriesAndFiles() throws Exception {
    byte[] zip =
        createZipBytes(
            b -> {
              try {
                // create a directory entry and a file under it
                ZipEntry dir = new ZipEntry("com/example/");
                b.putNextEntry(dir);
                b.closeEntry();

                ZipEntry file = new ZipEntry("com/example/Hello.txt");
                b.putNextEntry(file);
                b.write("hello".getBytes());
                b.closeEntry();

                // root file
                ZipEntry root = new ZipEntry("root.bin");
                b.putNextEntry(root);
                b.write(new byte[] {1, 2, 3});
                b.closeEntry();
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            });

    Method m =
        ConcreteExtractionService.class.getDeclaredMethod("fromZipBytesWithJimfs", byte[].class);
    m.setAccessible(true);
    try (FileSystem fs = (FileSystem) m.invoke(null, (Object) zip)) {
      Path hello = fs.getPath("/", "com", "example", "Hello.txt");
      Path root = fs.getPath("/", "root.bin");

      assertTrue(Files.exists(hello), "Hello.txt should exist in the Jimfs filesystem");
      assertTrue(Files.exists(root), "root.bin should exist in the Jimfs filesystem");

      byte[] helloBytes = Files.readAllBytes(hello);
      assertArrayEquals("hello".getBytes(), helloBytes);

      byte[] rootBytes = Files.readAllBytes(root);
      assertArrayEquals(new byte[] {1, 2, 3}, rootBytes);
    }
  }

  @Test
  void extractorForJarFile_returnsNonNullExtractor() throws Exception {
    byte[] zip =
        createZipBytes(
            b -> {
              try {
                ZipEntry file = new ZipEntry("A.class");
                b.putNextEntry(file);
                b.write(new byte[] {0, 1, 2});
                b.closeEntry();
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            });

    // merger may be null for this basic sanity check; ensure no exceptions and non-null return
    ConcreteExtractionService svc = new ConcreteExtractionService(null);
    var extractor = svc.extractorForJarFile(zip);
    assertNotNull(extractor, "extractorForJarFile should return a non-null ModelExtractor");
  }

  // helper to create zip bytes with a simple writer action
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
