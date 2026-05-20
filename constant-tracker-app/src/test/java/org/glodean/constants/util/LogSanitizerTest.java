package org.glodean.constants.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for LogSanitizer.
 */
class LogSanitizerTest {

  @Test
  void sanitize_returnsNullWhenInputIsNull() {
    assertThat(LogSanitizer.sanitize(null)).isNull();
  }

  @Test
  void sanitize_passesPlainStringThrough() {
    assertThat(LogSanitizer.sanitize("my-project")).isEqualTo("my-project");
  }

  @Test
  void sanitize_escapesNewlineCharacters() {
    String sanitized = LogSanitizer.sanitize("line1\nline2");
    assertThat(sanitized).doesNotContain("\n");
    assertThat(sanitized).contains("\\n");
  }

  @Test
  void sanitize_escapesCarriageReturn() {
    String sanitized = LogSanitizer.sanitize("line1\rline2");
    assertThat(sanitized).doesNotContain("\r");
  }

  @Test
  void sanitize_emptyStringReturnsEmpty() {
    assertThat(LogSanitizer.sanitize("")).isEmpty();
  }
}
