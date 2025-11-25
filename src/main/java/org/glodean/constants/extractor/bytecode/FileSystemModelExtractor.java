package org.glodean.constants.extractor.bytecode;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glodean.constants.extractor.ModelExtractor;
import org.glodean.constants.model.ClassConstants;

/**
 * Walks a provided {@link FileSystem} and extracts {@link ClassConstants} for every discovered
 * {@code .class} file using an {@link AnalysisMerger}.
 */
public record FileSystemModelExtractor(FileSystem fileSystem, AnalysisMerger merger)
    implements ModelExtractor {
  private static final Logger logger = LogManager.getLogger(FileSystemModelExtractor.class);

  @Override
  public Collection<ClassConstants> extract() throws ExtractionException {
    Queue<ClassConstants> ret = new ConcurrentLinkedQueue<>();
    try (var paths = Files.walk(fileSystem.getPath("/"));
        var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      paths
          .filter(Files::isRegularFile)
          .filter(path -> path.toString().endsWith(".class"))
          .forEach(
              path ->
                  executor.execute(
                      () -> {
                        try {
                          ret.addAll(
                              new ClassModelExtractor(ClassFile.of().parse(path), merger)
                                  .extract());
                        } catch (IOException e) {
                          logger.atInfo().log("Exception {} happened, ignoring {}", e, path);
                        }
                      }));
    } catch (IOException e) {
      throw new ExtractionException(e);
    }
    return ret;
  }
}
