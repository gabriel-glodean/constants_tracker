package org.glodean.constants.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code constants.auth} block from {@code application.yaml}.
 *
 * <pre>
 * constants:
 *   auth:
 *     enabled: true
 *     jwt:
 *       secret: ...
 *       expiration-ms: 3600000
 *     refresh-token:
 *       ttl-ms: 604800000
 * </pre>
 */
@ConfigurationProperties(prefix = "constants.auth")
public record AuthProperties(boolean enabled, Jwt jwt, RefreshToken refreshToken) {

  /** JWT-specific settings. */
  public record Jwt(String secret, long expirationMs) {}

  /** Refresh-token–specific settings. */
  public record RefreshToken(long ttlMs) {}
}
