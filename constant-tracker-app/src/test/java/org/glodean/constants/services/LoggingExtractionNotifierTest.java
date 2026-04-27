package org.glodean.constants.services;

import static org.assertj.core.api.Assertions.assertThatNoException;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Smoke tests that verify every {@link LoggingExtractionNotifier} method executes without error. */
class LoggingExtractionNotifierTest {

  private final LoggingExtractionNotifier notifier = new LoggingExtractionNotifier();

  @Test
  void onExtractionStartedDoesNotThrow() {
    assertThatNoException().isThrownBy(() -> notifier.onExtractionStarted(4));
  }

  @Test
  void onProcessingClassDoesNotThrow() {
    assertThatNoException()
        .isThrownBy(() -> notifier.onProcessingClass(Path.of("com/example/Greeter.class")));
  }

  @Test
  void onProcessingErrorDoesNotThrow() {
    assertThatNoException()
        .isThrownBy(
            () ->
                notifier.onProcessingError(
                    Path.of("com/example/Broken.class"), new RuntimeException("bad class")));
  }

  @Test
  void onProgressUpdateDoesNotThrow() {
    assertThatNoException().isThrownBy(() -> notifier.onProgressUpdate(100L));
  }

  @Test
  void onFutureFailureDoesNotThrow() {
    assertThatNoException()
        .isThrownBy(() -> notifier.onFutureFailure(new RuntimeException("future failed")));
  }

  @Test
  void onExtractionCompletedDoesNotThrow() {
    assertThatNoException()
        .isThrownBy(
            () ->
                notifier.onExtractionCompleted(
                    200L, 3L, Map.of(IllegalArgumentException.class, 2, NullPointerException.class, 1)));
  }

  @Test
  void onExtractionCompletedWithZeroExceptionsDoesNotThrow() {
    assertThatNoException()
        .isThrownBy(() -> notifier.onExtractionCompleted(50L, 0L, Map.of()));
  }
}
