package org.glodean.constants.extractor.bytecode;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;
import org.glodean.constants.extractor.ExtractionNotifier;
import org.glodean.constants.extractor.ModelExtractor;
import org.glodean.constants.model.ClassConstants;

/**
 * Walks a provided {@link FileSystem} and extracts {@link ClassConstants} for every discovered
 * {@code .class} file using an {@link AnalysisMerger}.
 */
public final class FileSystemModelExtractor implements ModelExtractor {
  private final FileSystem fileSystem;
  private final AnalysisMerger merger;
  private final Predicate<Path> ignorePathPredicate;
  private final ExtractionNotifier notifier;

  public FileSystemModelExtractor(
      FileSystem fileSystem,
      AnalysisMerger merger,
      String ignorePathPrefix,
      ExtractionNotifier notifier) {
    this.fileSystem = fileSystem;
    this.merger = merger;
    this.notifier = notifier;
    Predicate<Path> predicate =
        Files::isRegularFile; // Start with a predicate that accepts all paths
    predicate = predicate.and(path -> path.toString().endsWith(".class"));

    if (ignorePathPrefix != null) {
      predicate = predicate.and(path -> !path.toString().startsWith(ignorePathPrefix));
    }
    this.ignorePathPredicate = predicate;
  }

  public FileSystemModelExtractor(
      FileSystem fileSystem, AnalysisMerger merger, ExtractionNotifier notifier) {
    this(fileSystem, merger, null, notifier);
  }

  public FileSystemModelExtractor(
      FileSystem fileSystem, AnalysisMerger merger, String ignorePathPrefix) {
    this(fileSystem, merger, ignorePathPrefix, new ExtractionNotifier.Silent());
  }

  public FileSystemModelExtractor(FileSystem fileSystem, AnalysisMerger merger) {
    this(fileSystem, merger, null, new ExtractionNotifier.Silent());
  }

  @Override
  public Collection<ClassConstants> extract() throws ExtractionException {
    Queue<ClassConstants> ret = new ConcurrentLinkedQueue<>();
    var adder = new LongAdder();
    Map<Class<?>, Integer> exceptions = new ConcurrentHashMap<>();
    var processedCounter = new LongAdder();

    // Use available processors for optimal parallelism
    int threadCount = Runtime.getRuntime().availableProcessors();
    notifier.onExtractionStarted(threadCount);

    try (var paths = Files.walk(fileSystem.getPath("/"));
        var executor = Executors.newFixedThreadPool(threadCount)) {
      var futures =
          paths
              .filter(ignorePathPredicate)
              .map(
                  path ->
                      executor.submit(
                          () -> {
                            try {
                              notifier.onProcessingClass(path);
                              return new ClassModelExtractor(ClassFile.of().parse(path), merger)
                                  .extract();
                            } catch (Exception e) {
                              adder.add(1);
                              exceptions.compute(e.getClass(), (_, v) -> (v == null) ? 1 : v + 1);
                              notifier.onProcessingError(path, e);
                            } finally {
                              processedCounter.increment();
                              long count = processedCounter.sum();
                              if (count % 1000 == 0) {
                                notifier.onProgressUpdate(count);
                              }
                            }
                            return null;
                          }))
              .toList();
      for (var future : futures) {
        try {
          var result = future.get();
          if (result != null) {
            ret.addAll(result);
          }
        } catch (Exception e) {
          notifier.onFutureFailure(e);
        }
      }
      notifier.onExtractionCompleted(processedCounter.sum(), adder.sum(), exceptions);
      return ret;
    } catch (IOException e) {
      throw new ExtractionException(e);
    }
  }
}
