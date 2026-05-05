/**
 * JWT-based authentication and Spring Security configuration.
 *
 * <p>This package is responsible for the entire security layer of the application:
 * <ul>
 *   <li>{@link org.glodean.constants.auth.SecurityConfiguration} — declares the
 *       {@code SecurityWebFilterChain} bean; conditionally enforces JWT via
 *       {@code @ConditionalOnProperty(constants.auth.enabled)}.</li>
 *   <li>{@link org.glodean.constants.auth.JwtService} — generates, validates, and
 *       invalidates signed JWTs (JJWT 0.12.x / HS256).</li>
 *   <li>{@link org.glodean.constants.auth.AuthProperties} — binds the
 *       {@code constants.auth.*} configuration block from {@code application.yaml}.</li>
 * </ul>
 *
 * <p>When {@code constants.auth.enabled=false} a bypass filter injects a fully-privileged
 * anonymous principal so that {@code @PreAuthorize} annotations still evaluate without
 * rejecting requests.
 */
package org.glodean.constants.auth;
