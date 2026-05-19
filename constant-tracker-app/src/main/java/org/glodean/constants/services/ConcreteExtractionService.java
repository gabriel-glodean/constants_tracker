package org.glodean.constants.services;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.zip.ZipInputStream;
import org.glodean.constants.extractor.ModelExtractor;
import org.glodean.constants.extractor.ModelExtractorSupplierRepository;
import org.glodean.constants.extractor.bytecode.AnalysisMerger;
import org.glodean.constants.extractor.bytecode.BytecodeModelExtractor;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.model.UnitDescriptor;
import io.micrometer.core.annotation.Timed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Concrete service implementation that extracts constants from class files and JAR files.
 *
 * <ul>
 *   <li><b>Class file:</b> Resolved via {@link ModelExtractorSupplierRepository} keyed by the
 *       file name derived from {@link UnitDescriptor#path()} (e.g. {@code "Foo.class"}).
 *       The same extension-based predicates used by the JAR/ZIP walkers therefore apply here
 *       too — no separate source-kind lookup is required.</li>
 *   <li><b>JAR file:</b> Opens the JAR as a native ZipFileSystem directly from the provided
 *       {@link Path} — no in-memory copy is made. The FileSystem is closed after extraction.</li>
 *   <li><b>JAR via ZipInputStream:</b> Used for nested JARs already read into memory; delegates
 *       to {@link BytecodeModelExtractor#forZipStream}.</li>
 * </ul>
 *
 * <p>All bulk extractions share a single {@code bytecodeAnalysisExecutor} thread pool
 * (sized to {@link Runtime#availableProcessors()}) defined in
 * {@link ExtractionServiceConfiguration}. The pool is reused across calls — no thread-pool
 * creation overhead per extraction.
 */
@Service
public class ConcreteExtractionService implements ExtractionService {

  private final AnalysisMerger merger;
  private final ExecutorService bytecodeAnalysisExecutor;
  private final ModelExtractorSupplierRepository bytecodeExtractorRepository;

  @Autowired
  public ConcreteExtractionService(
      AnalysisMerger merger,
      @Qualifier("bytecodeAnalysisExecutor") ExecutorService bytecodeAnalysisExecutor,
      ModelExtractorSupplierRepository bytecodeExtractorRepository) {
    this.merger = merger;
    this.bytecodeAnalysisExecutor = bytecodeAnalysisExecutor;
    this.bytecodeExtractorRepository = bytecodeExtractorRepository;
  }

  @Timed(value = "extraction.class", description = "Time to extract a class file")
  @Override
  public Collection<UnitConstants> extractClassFile(byte[] classFileBytes, UnitDescriptor descriptor)
      throws ModelExtractor.ExtractionException {
    // Resolve by file name so the same extension-based predicates used by the JAR/ZIP
    // walkers apply here too — no separate source-kind lookup needed.
    String fileName = Path.of(descriptor.path()).getFileName().toString();
    return bytecodeExtractorRepository.resolve(fileName, classFileBytes)
        .orElseThrow(() -> new ModelExtractor.ExtractionException(
            "No extractor registered for file: " + fileName))
        .extractor()
        .extract(descriptor);
  }

  @Timed(value = "extraction.jar", description = "Time to extract a JAR file")
  @Override
  public Collection<UnitConstants> extractJarFile(Path jarPath, UnitDescriptor descriptor)
      throws ModelExtractor.ExtractionException {
    try (FileSystem fs = FileSystems.newFileSystem(jarPath, (ClassLoader) null)) {
      return BytecodeModelExtractor
          .forFileSystem(bytecodeAnalysisExecutor, fs, merger, new LoggingExtractionNotifier(), bytecodeExtractorRepository)
          .extract(descriptor);
    } catch (ModelExtractor.ExtractionException e) {
      throw e;
    } catch (IOException e) {
      throw new ModelExtractor.ExtractionException(e);
    }
  }

  @Timed(value = "extraction.zip_stream", description = "Time to extract a JAR via ZipInputStream")
  @Override
  public Collection<UnitConstants> extractZipStream(ZipInputStream zis, UnitDescriptor descriptor)
      throws ModelExtractor.ExtractionException {
    return BytecodeModelExtractor
        .forZipStream(bytecodeAnalysisExecutor, zis, merger, bytecodeExtractorRepository)
        .extract(descriptor);
  }
}
