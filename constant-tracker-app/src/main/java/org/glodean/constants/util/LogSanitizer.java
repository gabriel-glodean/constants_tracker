package org.glodean.constants.util;

import org.owasp.encoder.Encode;

/**
 * Utility methods for sanitizing values before they are written to log output.
 *
 * <p>Unsanitized user-controlled data written to logs can enable log-injection attacks
 * (CWE-117): a malicious value containing newline characters could forge additional log
 * lines or corrupt structured log output.  Every string that originates from an external
 * caller (file paths, project names, search terms, …) should be passed through
 * {@link #sanitize(String)} before it is included in a log message.
 *
 * <p>Sanitization is delegated to the
 * <a href="https://owasp.org/www-project-java-encoder/">OWASP Java Encoder</a> library
 * ({@link Encode#forJava(String)}), which escapes control characters — including CR/LF —
 * to their Java string-literal equivalents (e.g. {@code \n}, {@code \r}).  This
 * preserves the original information in the log while preventing injection.
 */
public final class LogSanitizer {

  private LogSanitizer() {}

  /**
   * Encodes {@code input} for safe inclusion in a log message using the OWASP Java
   * Encoder.  Control characters (CR, LF, …) are replaced with their Java
   * string-literal escape sequences so that they cannot be used to forge additional
   * log lines.
   *
   * @param input the string to sanitize; may be {@code null}
   * @return the sanitized string, or {@code null} if {@code input} was {@code null}
   */
  public static String sanitize(String input) {
    if (input == null) return null;
    return Encode.forJava(input);
  }
}
