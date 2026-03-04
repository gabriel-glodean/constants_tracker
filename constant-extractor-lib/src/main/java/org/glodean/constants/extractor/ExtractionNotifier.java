package org.glodean.constants.extractor;

import java.nio.file.Path;
import java.util.Map;

/**
 * Interface for receiving notifications about the progress and status of extraction operations.
 * Implementations can log, track metrics, update UIs, or perform other actions in response to
 * extraction events.
 */
public interface ExtractionNotifier {

  /**
   * Called when extraction starts.
   *
   * @param threadCount the number of threads that will be used for parallel processing
   */
  void onExtractionStarted(int threadCount);

  /**
   * Called when a class file is about to be processed.
   *
   * @param path the path to the class file being processed
   */
  void onProcessingClass(Path path);

  /**
   * Called when an error occurs while processing a class file.
   *
   * @param path the path to the class file that caused the error
   * @param error the exception that was thrown
   */
  void onProcessingError(Path path, Exception error);

  /**
   * Called periodically to report progress.
   *
   * @param processedCount the number of classes processed so far
   */
  void onProgressUpdate(long processedCount);

  /**
   * Called when a future fails to complete.
   *
   * @param error the exception that was thrown
   */
  void onFutureFailure(Exception error);

  /**
   * Called when extraction completes.
   *
   * @param totalProcessed the total number of classes processed
   * @param totalExceptions the total number of exceptions encountered
   * @param exceptionsByType a breakdown of exceptions by their class type
   */
  void onExtractionCompleted(
      long totalProcessed, long totalExceptions, Map<Class<?>, Integer> exceptionsByType);

  /** A no-op implementation that ignores all notifications. */
  class Silent implements ExtractionNotifier {
    @Override
    public void onExtractionStarted(int threadCount) {}

    @Override
    public void onProcessingClass(Path path) {}

    @Override
    public void onProcessingError(Path path, Exception error) {}

    @Override
    public void onProgressUpdate(long processedCount) {}

    @Override
    public void onFutureFailure(Exception error) {}

    @Override
    public void onExtractionCompleted(
        long totalProcessed, long totalExceptions, Map<Class<?>, Integer> exceptionsByType) {}
  }
}
