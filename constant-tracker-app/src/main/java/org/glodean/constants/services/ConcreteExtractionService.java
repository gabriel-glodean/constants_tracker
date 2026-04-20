package org.glodean.constants.services;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.classfile.ClassFile;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.glodean.constants.extractor.ModelExtractor;
import org.glodean.constants.extractor.bytecode.AnalysisMerger;
import org.glodean.constants.extractor.bytecode.ClassModelExtractor;
import org.glodean.constants.extractor.bytecode.FileSystemModelExtractor;
import io.micrometer.core.annotation.Timed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Concrete service implementation that provides ModelExtractor instances for different input
 * formats (raw class file bytes or jar/zip bytes).
 *
 * <p>This service acts as a factory for creating configured {@link ModelExtractor}s. Key features:
 * <ul>
 *   <li><b>Class file support:</b> Directly parses .class bytes using Java 25 Class-File API</li>
 *   <li><b>JAR file support:</b> Uses Jimfs (in-memory filesystem) to expand JAR contents</li>
 *   <li><b>Dependency injection:</b> Shares a single {@link AnalysisMerger} across all extractors</li>
 * </ul>
 *
 * <p><b>Why Jimfs?</b> Loading JAR files into an in-memory filesystem allows the
 * {@link FileSystemModelExtractor} to walk and process all contained classes without
 * disk I/O overhead. This is especially beneficial when analyzing many JARs repeatedly
 * (e.g., CI/CD pipelines).
 *
 * @param merger the analysis merger shared by all created extractors
 */
@Service
public class ConcreteExtractionService implements ExtractionService {

  private final AnalysisMerger merger;

  @Autowired
  public ConcreteExtractionService(AnalysisMerger merger) {
    this.merger = merger;
  }

  @Timed(value = "extraction.class", description = "Time to create a class file extractor")
  @Override
  public ModelExtractor extractorForClassFile(byte[] classFileBytes) {
    return new ClassModelExtractor(ClassFile.of().parse(classFileBytes), merger);
  }

  @Timed(value = "extraction.jar", description = "Time to create a JAR file extractor")
  @Override
  public ModelExtractor extractorForJarFile(byte[] jarFileBytes) {
    return new FileSystemModelExtractor(
        fromZipBytesWithJimfs(jarFileBytes), merger, new LoggingExtractionNotifier());
  }

  private static FileSystem fromZipBytesWithJimfs(byte[] zipBytes) {
    FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        Path path = fs.getPath("/", entry.getName());
        if (entry.isDirectory()) {
          Files.createDirectories(path);
        } else {
          Path parent = path.getParent();
          if (parent != null) {
            Files.createDirectories(parent);
          }
          try (OutputStream out = Files.newOutputStream(path)) {
            zis.transferTo(out);
          }
        }
        zis.closeEntry();
      }
    } catch (IOException e) {
      throw new IllegalArgumentException(e);
    }
    return fs;
  }
}
