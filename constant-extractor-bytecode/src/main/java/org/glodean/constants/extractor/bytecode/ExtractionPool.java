package org.glodean.constants.extractor.bytecode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

import org.glodean.constants.extractor.ExtractionNotifier;
import org.glodean.constants.extractor.ModelExtractor;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.model.UnitDescriptor;

/**
 * Package-private, per-invocation session that drives parallel bytecode analysis via an
 * externally-provided {@link ExecutorService}.
 *
 * <p>Callers submit class bytes or paths, then call {@link #collect()} to block until all
 * analysis tasks complete and harvest results. The executor is <em>not</em> owned or closed
 * here — lifecycle is managed by the caller ({@link BytecodeModelExtractor}).
 *
 * <pre>{@code
 * var pool = new ExtractionPool(sharedExecutor, merger, notifier);
 * for (Path p : classPaths) pool.submit(p, source);
 * return pool.collect();
 * }</pre>
 *
 * <p>{@link BytecodeModelExtractor} uses this class for both its FileSystem and ZipInputStream
 * modes so that executor lifecycle, {@link ClassModelExtractor} construction, progress tracking,
 * and error handling all live in one place.
 */
final class ExtractionPool {

  private final ExecutorService executor;
  private final ExtractionNotifier notifier;
  private final List<Future<Collection<UnitConstants>>> futures = new ArrayList<>();
  private final LongAdder processedCounter = new LongAdder();
  private final LongAdder errorCount = new LongAdder();
  private final Map<Class<?>, Integer> exceptions = new ConcurrentHashMap<>();

  ExtractionPool(ExecutorService executor, ExtractionNotifier notifier) {
    this.executor = executor;
    this.notifier = notifier;
    int threadCount = executor instanceof ThreadPoolExecutor tpe
        ? tpe.getCorePoolSize()
        : Runtime.getRuntime().availableProcessors();
    notifier.onExtractionStarted(threadCount);
  }

  /**
   * Submits pre-read {@code .class} bytes for bytecode analysis.
   * {@code name} is used only for logging/notifications.
   */
  void submit(Supplier<ModelExtractor> extractor, Path name, UnitDescriptor source) {
    futures.add(executor.submit(() -> {
      notifier.onProcessingClass(name);
      try {
        return extractor.get().extract(source);
      } catch (Exception e) {
        notifier.onProcessingError(name, e);
        throw e;
      } finally {
        processedCounter.increment();
        long count = processedCounter.sum();
        if (count % 1000 == 0) notifier.onProgressUpdate(count);
      }
    }));
  }

  /**
   * Blocks until all submitted tasks complete, then returns the aggregated results.
   * Also fires {@link ExtractionNotifier#onExtractionCompleted}.
   */
  Collection<UnitConstants> collect() {
    Queue<UnitConstants> result = new ConcurrentLinkedQueue<>();
    for (var future : futures) {
      try {
        var r = future.get();
        if (r != null) result.addAll(r);
      } catch (ExecutionException e) {
        errorCount.increment();
        Throwable cause = e.getCause();
        if (cause != null) exceptions.compute(cause.getClass(), (_, v) -> v == null ? 1 : v + 1);
        notifier.onFutureFailure(e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        notifier.onFutureFailure(e);
      }
    }
    notifier.onExtractionCompleted(processedCounter.sum(), errorCount.sum(), exceptions);
    return result;
  }
}
