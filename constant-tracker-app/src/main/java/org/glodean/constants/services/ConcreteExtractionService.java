package org.glodean.constants.services;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Collection;
import org.glodean.constants.extractor.ModelExtractor;
import org.glodean.constants.extractor.bytecode.AnalysisMerger;
import org.glodean.constants.extractor.bytecode.ClassModelExtractor;
import org.glodean.constants.extractor.bytecode.FileSystemModelExtractor;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.model.UnitDescriptor;
import io.micrometer.core.annotation.Timed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Concrete service implementation that extracts constants from class files and JAR files.
 *
 * <ul>
 *   <li><b>Class file:</b> Parses bytes using Java 25 Class-File API</li>
 *   <li><b>JAR file:</b> Opens the JAR as a native ZipFileSystem directly from the provided
 *       {@link Path} — no in-memory copy is made. The FileSystem is closed after extraction.</li>
 * </ul>
 */
@Service
public class ConcreteExtractionService implements ExtractionService {

  private final AnalysisMerger merger;

  @Autowired
  public ConcreteExtractionService(AnalysisMerger merger) {
    this.merger = merger;
  }

  @Timed(value = "extraction.class", description = "Time to extract a class file")
  @Override
  public Collection<UnitConstants> extractClassFile(byte[] classFileBytes, UnitDescriptor descriptor)
      throws ModelExtractor.ExtractionException {
    return new ClassModelExtractor(ClassFile.of().parse(classFileBytes), merger).extract(descriptor);
  }

  @Timed(value = "extraction.jar", description = "Time to extract a JAR file")
  @Override
  public Collection<UnitConstants> extractJarFile(Path jarPath, UnitDescriptor descriptor)
      throws ModelExtractor.ExtractionException {
    try (FileSystem fs = FileSystems.newFileSystem(jarPath, (ClassLoader) null)) {
      return new FileSystemModelExtractor(fs, merger, new LoggingExtractionNotifier()).extract(descriptor);
    } catch (ModelExtractor.ExtractionException e) {
      throw e;
    } catch (IOException e) {
      throw new ModelExtractor.ExtractionException(e);
    }
  }
}
