package org.glodean.constants.services;

import java.nio.file.Path;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glodean.constants.extractor.ExtractionNotifier;

/**
 * An {@link ExtractionNotifier} implementation that logs all extraction events using Log4j.
 *
 * <p>This notifier provides visibility into the extraction process through structured logging
 * at various levels:
 * <ul>
 *   <li><b>INFO:</b> Extraction start, progress updates (every N classes)</li>
 *   <li><b>DEBUG:</b> Individual class processing errors (continue on error)</li>
 *   <li><b>TRACE:</b> Every class file being processed (verbose)</li>
 *   <li><b>WARN:</b> Future/async task failures</li>
 *   <li><b>ERROR:</b> Critical failures preventing continuation</li>
 * </ul>
 *
 * <p>This implementation is production-ready and used by default in
 * {@link ConcreteExtractionService}. For testing or custom monitoring, implement
 * {@link ExtractionNotifier} directly.
 */
public class LoggingExtractionNotifier implements ExtractionNotifier {
  private static final Logger logger = LogManager.getLogger(LoggingExtractionNotifier.class);

  @Override
  public void onExtractionStarted(int threadCount) {
    logger.atInfo().log("Starting extraction with {} threads", threadCount);
  }

  @Override
  public void onProcessingClass(Path path) {
    logger.atTrace().log("Processing class file: {}", path);
  }

  @Override
  public void onProcessingError(Path path, Exception error) {
    logger.atDebug().log("Error processing {}: {}", path, error.getMessage());
  }

  @Override
  public void onProgressUpdate(long processedCount) {
    logger.atInfo().log("Processed {} classes", processedCount);
  }

  @Override
  public void onFutureFailure(Exception error) {
    logger.atWarn().log("Future failed: {}", error.getMessage());
  }

  @Override
  public void onExtractionCompleted(
      long totalProcessed, long totalExceptions, Map<Class<?>, Integer> exceptionsByType) {
    logger.atInfo().log(
        "Extraction complete. Total classes processed: {}, Exceptions: {}, Details: {}",
        totalProcessed,
        totalExceptions,
        exceptionsByType);
  }
}
