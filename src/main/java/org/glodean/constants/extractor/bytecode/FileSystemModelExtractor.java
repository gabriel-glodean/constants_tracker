package org.glodean.constants.extractor.bytecode;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import org.glodean.constants.extractor.ModelExtractor;
import org.glodean.constants.model.ClassConstants;

public record FileSystemModelExtractor(Path filePath) implements ModelExtractor {
  @Override
  public Collection<ClassConstants> extract() throws ExtractionException {
    Map<String, Object> props = Collections.emptyMap();
    Queue<ClassConstants> ret = new ConcurrentLinkedQueue<>();
    try (var fileSystem = FileSystems.newFileSystem(filePath, props);
        var paths = Files.walk(fileSystem.getPath("/"));
        var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      paths
          .filter(Files::isRegularFile)
          .filter(path -> path.toString().endsWith(".class"))
          .forEach(
              path ->
                  executor.execute(
                      () -> {
                        try {
                          ret.addAll(new ClassModelExtractor(ClassFile.of().parse(path)).extract());
                        } catch (IOException e) {
                          IO.println("Exception %s happened, ignoring %s".formatted(e, path));
                        }
                      }));
    } catch (IOException e) {
      throw new ExtractionException(e);
    }
    return ret;
  }
}
