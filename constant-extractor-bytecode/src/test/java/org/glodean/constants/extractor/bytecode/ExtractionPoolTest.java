package org.glodean.constants.extractor.bytecode;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.glodean.constants.extractor.ExtractionNotifier;
import org.glodean.constants.extractor.ModelExtractor;
import org.glodean.constants.model.UnitConstants;
import org.glodean.constants.model.UnitDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ExtractionPool Tests")
class ExtractionPoolTest {

  // -------------------------------------------------------------------------
  // Test helpers
  // -------------------------------------------------------------------------

  /** Counting ExtractionNotifier that records every callback. */
  static class TrackingNotifier implements ExtractionNotifier {

    int startedThreadCount = -1;
    final java.util.List<Path> processedClasses = new java.util.ArrayList<>();
    final java.util.List<Exception> processingErrors = new java.util.ArrayList<>();
    final java.util.List<Long> progressUpdates = new java.util.ArrayList<>();
    final java.util.List<Exception> futureFailures = new java.util.ArrayList<>();
    long completedTotal = -1;
    long completedErrors = -1;
    Map<Class<?>, Integer> completedExceptionsByType;

    @Override
    public void onExtractionStarted(int threadCount) {
      this.startedThreadCount = threadCount;
    }

    @Override
    public void onProcessingClass(Path path) {
      processedClasses.add(path);
    }

    @Override
    public void onProcessingError(Path path, Exception error) {
      processingErrors.add(error);
    }

    @Override
    public void onProgressUpdate(long processedCount) {
      progressUpdates.add(processedCount);
    }

    @Override
    public void onFutureFailure(Exception error) {
      futureFailures.add(error);
    }

    @Override
    public void onExtractionCompleted(
        long totalProcessed, long totalExceptions, Map<Class<?>, Integer> exceptionsByType) {
      this.completedTotal = totalProcessed;
      this.completedErrors = totalExceptions;
      this.completedExceptionsByType = exceptionsByType;
    }
  }

  private static UnitDescriptor descriptor(String path) {
    return new UnitDescriptor(BytecodeSourceKind.CLASS_FILE, path);
  }

  /** A ModelExtractor stub that returns a single empty UnitConstants. */
  private static ModelExtractor successExtractor(String path) {
    return _ -> List.of(new UnitConstants(descriptor(path), Set.of()));
  }

  /** A ModelExtractor stub that throws a RuntimeException. */
  private static ModelExtractor failingExtractor() {
    return _ -> {
      throw new RuntimeException("simulated failure");
    };
  }

  // -------------------------------------------------------------------------
  // Constructor / onExtractionStarted
  // -------------------------------------------------------------------------

  @Nested
  @DisplayName("Constructor notifications")
  class ConstructorTests {

    @Test
    @DisplayName("Uses ThreadPoolExecutor core-pool size for thread count")
    void usesThreadPoolExecutorCorePoolSize() {
      var notifier = new TrackingNotifier();
      try (ExecutorService exec = Executors.newFixedThreadPool(4)) {
        // newFixedThreadPool returns a ThreadPoolExecutor with corePoolSize == 4
          assertInstanceOf(ThreadPoolExecutor.class, exec);
        new ExtractionPool(exec, notifier);
        assertEquals(4, notifier.startedThreadCount);
      }
    }

    @Test
    @DisplayName("Falls back to availableProcessors for non-ThreadPoolExecutor")
    void fallsBackToAvailableProcessors() {
      var notifier = new TrackingNotifier();
      // newVirtualThreadPerTaskExecutor is NOT a ThreadPoolExecutor
      try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
        new ExtractionPool(exec, notifier);
        assertEquals(Runtime.getRuntime().availableProcessors(), notifier.startedThreadCount);
      }
    }
  }

  // -------------------------------------------------------------------------
  // submit + collect — happy path
  // -------------------------------------------------------------------------

  @Nested
  @DisplayName("submit and collect — happy path")
  class HappyPathTests {

    @Test
    @DisplayName("Collects results from a single successful task")
    void collectsSingleResult() {
      var notifier = new TrackingNotifier();
      try (ExecutorService exec = Executors.newFixedThreadPool(2)) {
        var pool = new ExtractionPool(exec, notifier);
        var path = Path.of("Foo.class");

        pool.submit(() -> successExtractor("Foo.class"), path, descriptor("Foo.class"));
        Collection<UnitConstants> result = pool.collect();

        assertEquals(1, result.size());
        assertEquals("Foo.class", result.iterator().next().source().path());
        assertEquals(1, notifier.processedClasses.size());
        assertEquals(path, notifier.processedClasses.getFirst());

        // onExtractionCompleted should report 1 processed, 0 errors
        assertEquals(1L, notifier.completedTotal);
        assertEquals(0L, notifier.completedErrors);
        assertTrue(notifier.completedExceptionsByType.isEmpty());
      }
    }

    @Test
    @DisplayName("Collects results from multiple successful tasks")
    void collectsMultipleResults(){
      var notifier = new TrackingNotifier();
      try (ExecutorService exec = Executors.newFixedThreadPool(4)) {
        var pool = new ExtractionPool(exec, notifier);

        for (int i = 0; i < 5; i++) {
          String name = "Class" + i + ".class";
          pool.submit(() -> successExtractor(name), Path.of(name), descriptor(name));
        }

        Collection<UnitConstants> result = pool.collect();

        assertEquals(5, result.size());
        assertEquals(5L, notifier.completedTotal);
        assertEquals(0L, notifier.completedErrors);
      }
    }

    @Test
    @DisplayName("Returns empty collection when no tasks are submitted")
    void emptyWhenNoTasksSubmitted() {
      var notifier = new TrackingNotifier();
      try (ExecutorService exec = Executors.newFixedThreadPool(2)) {
        var pool = new ExtractionPool(exec, notifier);
        Collection<UnitConstants> result = pool.collect();

        assertTrue(result.isEmpty());
        assertEquals(0L, notifier.completedTotal);
        assertEquals(0L, notifier.completedErrors);
      }
    }

    @Test
    @DisplayName("Handles extractor returning null gracefully")
    void handlesNullReturn(){
      var notifier = new TrackingNotifier();
      try (ExecutorService exec = Executors.newFixedThreadPool(2)) {
        var pool = new ExtractionPool(exec, notifier);
        pool.submit(() -> _ -> null, Path.of("Null.class"), descriptor("Null.class"));
        Collection<UnitConstants> result = pool.collect();

        // null result should be skipped
        assertTrue(result.isEmpty());
        assertEquals(1L, notifier.completedTotal);
        assertEquals(0L, notifier.completedErrors);
      }
    }
  }

  // -------------------------------------------------------------------------
  // Error handling
  // -------------------------------------------------------------------------

  @Nested
  @DisplayName("Error handling")
  class ErrorHandlingTests {

    @Test
    @DisplayName("Counts ExecutionException and fires onFutureFailure")
    void countsExecutionExceptionFailure() {
      var notifier = new TrackingNotifier();
      try (ExecutorService exec = Executors.newFixedThreadPool(2)) {
        var pool = new ExtractionPool(exec, notifier);
        var path = Path.of("Bad.class");
        pool.submit(ExtractionPoolTest::failingExtractor, path, descriptor("Bad.class"));

        Collection<UnitConstants> result = pool.collect();

        assertTrue(result.isEmpty()); // failed task contributes nothing
        assertEquals(1L, notifier.completedErrors);
        assertEquals(1, notifier.futureFailures.size());
        // onProcessingError should have been fired inside the task
        assertEquals(1, notifier.processingErrors.size());
        // exception type should be tracked
        assertEquals(
            1,
            notifier.completedExceptionsByType.getOrDefault(RuntimeException.class, 0),
            "RuntimeException should appear in the exception map");
      }
    }

    @Test
    @DisplayName("Mixes successes and failures correctly")
    void mixedSuccessAndFailure() {
      var notifier = new TrackingNotifier();
      try (ExecutorService exec = Executors.newFixedThreadPool(4)) {
        var pool = new ExtractionPool(exec, notifier);

        pool.submit(() -> successExtractor("Good.class"), Path.of("Good.class"), descriptor("Good.class"));
        pool.submit(ExtractionPoolTest::failingExtractor, Path.of("Bad.class"), descriptor("Bad.class"));
        pool.submit(() -> successExtractor("Good2.class"), Path.of("Good2.class"), descriptor("Good2.class"));

        Collection<UnitConstants> result = pool.collect();

        assertEquals(2, result.size());
        assertEquals(3L, notifier.completedTotal);
        assertEquals(1L, notifier.completedErrors);
        assertEquals(1, notifier.futureFailures.size());
      }
    }

    @Test
    @DisplayName("Multiple failures accumulate in the exception type map")
    void multipleFailuresAccumulate() {
      var notifier = new TrackingNotifier();
      try (ExecutorService exec = Executors.newFixedThreadPool(4)) {
        var pool = new ExtractionPool(exec, notifier);

        for (int i = 0; i < 3; i++) {
          String name = "Bad" + i + ".class";
          pool.submit(ExtractionPoolTest::failingExtractor, Path.of(name), descriptor(name));
        }

        pool.collect();

        assertEquals(3L, notifier.completedErrors);
        assertEquals(3, notifier.futureFailures.size());
        assertEquals(
            3,
            notifier.completedExceptionsByType.getOrDefault(RuntimeException.class, 0));
      }
    }
  }

  // -------------------------------------------------------------------------
  // Progress reporting
  // -------------------------------------------------------------------------

  @Nested
  @DisplayName("Progress reporting")
  class ProgressReportingTests {

    @Test
    @DisplayName("Fires onProgressUpdate every 1000 processed classes")
    void firesProgressEvery1000() {
      var notifier = new TrackingNotifier();
      // Use a single-thread executor so ordering is deterministic
      try (ExecutorService exec = Executors.newSingleThreadExecutor()) {
        var pool = new ExtractionPool(exec, notifier);

        for (int i = 0; i < 2000; i++) {
          String name = "Class" + i + ".class";
          final int idx = i;
          pool.submit(() -> successExtractor("Class" + idx + ".class"),
              Path.of(name), descriptor(name));
        }
        pool.collect();
      }

      assertEquals(2, notifier.progressUpdates.size(),
          "Should fire exactly twice (at 1000 and 2000)");
      assertEquals(1000L, notifier.progressUpdates.get(0));
      assertEquals(2000L, notifier.progressUpdates.get(1));
    }

    @Test
    @DisplayName("Does not fire onProgressUpdate for fewer than 1000 classes")
    void doesNotFireProgressBelow1000() {
      var notifier = new TrackingNotifier();
      try (ExecutorService exec = Executors.newSingleThreadExecutor()) {
        var pool = new ExtractionPool(exec, notifier);
        for (int i = 0; i < 999; i++) {
          String name = "C" + i + ".class";
          final int idx = i;
          pool.submit(() -> successExtractor("C" + idx + ".class"),
              Path.of(name), descriptor(name));
        }
        pool.collect();
      }

      assertTrue(notifier.progressUpdates.isEmpty(),
          "Should not fire any progress update for < 1000 classes");
    }
  }

  // -------------------------------------------------------------------------
  // InterruptedException
  // -------------------------------------------------------------------------

  @Nested
  @DisplayName("InterruptedException handling")
  class InterruptedTests {

    @Test
    @DisplayName("Sets interrupt flag and fires onFutureFailure when interrupted")
    void setsInterruptFlagOnInterruption() throws InterruptedException {
      var notifier = new TrackingNotifier();
      var interrupted = new AtomicBoolean(false);

      // We need a task that blocks, and we interrupt the collecting thread
      Object lock = new Object();
      AtomicBoolean taskStarted = new AtomicBoolean(false);

      try (ExecutorService exec = Executors.newFixedThreadPool(2)) {
        var pool = new ExtractionPool(exec, notifier);

        // Submit a task that blocks until the test interrupts the collecting thread
        pool.submit(
            () ->
                _ -> {
                  synchronized (lock) {
                    taskStarted.set(true);
                    lock.notifyAll();
                    try {
                      lock.wait(5_000); // wait until interrupted / released
                    } catch (InterruptedException ignored) {
                      Thread.currentThread().interrupt();
                    }
                  }
                  return List.of();
                },
            Path.of("Blocking.class"),
            descriptor("Blocking.class"));

        Thread collectThread =
            new Thread(
                () -> {
                  pool.collect();
                  interrupted.set(Thread.currentThread().isInterrupted());
                });

        collectThread.start();

        // Wait for the task to actually start
        synchronized (lock) {
          while (!taskStarted.get()) lock.wait(1_000);
        }

        // Interrupt the collecting thread so future.get() throws InterruptedException
        collectThread.interrupt();
        collectThread.join(5_000);

        assertTrue(interrupted.get(), "Collecting thread must have its interrupt flag restored");
        assertEquals(1, notifier.futureFailures.size());
      }
    }
  }
}
