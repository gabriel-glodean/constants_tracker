package org.glodean.constants.web.endpoints;

import jakarta.validation.Valid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glodean.constants.auth.JwtService;
import org.glodean.constants.dto.LoginRequest;
import org.glodean.constants.dto.LogoutRequest;
import org.glodean.constants.dto.RefreshRequest;
import org.glodean.constants.dto.TokenResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Authentication endpoints — only registered when {@code constants.auth.enabled=true}.
 *
 * <p>When auth is disabled these routes do not exist: there is nothing to log in to.
 *
 * <pre>
 * POST /auth/login          – exchange credentials for a JWT + refresh token (public)
 * POST /auth/refresh        – exchange a refresh token for new tokens        (public)
 * POST /auth/token/renew    – exchange a valid token for a fresh one         (authenticated)
 * POST /auth/logout         – invalidate the current session                 (authenticated)
 * </pre>
 *
 * @see org.glodean.constants.web.endpoints.AuthStatusController for GET /auth/status
 *      (always available, regardless of whether auth is enforced)
 */
@RestController
@RequestMapping("/auth")
@ConditionalOnProperty(name = "constants.auth.enabled", havingValue = "true", matchIfMissing = true)
public class LoginController {

    private static final Logger log = LogManager.getLogger(LoginController.class);

    private final JwtService jwtService;

    @Autowired
    public LoginController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    /**
     * Authenticate with username + password and receive a signed JWT + refresh token.
     *
     * @return 200 OK with {@link TokenResponse}, or 401 Unauthorized on bad credentials
     */
    @PreAuthorize("permitAll()")
    @PostMapping("/login")
    public Mono<ResponseEntity<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        return jwtService.authenticate(request.username(), request.password())
                .map(ResponseEntity::ok);
    }

    /**
     * Exchange a valid refresh token for a new access token and a rotated refresh token.
     *
     * <p>The old refresh token is revoked as part of the rotation — callers must store the new
     * refresh token returned in the response.
     *
     * @param request body containing the raw refresh token
     * @return 200 OK with a new {@link TokenResponse}, or 401 if the refresh token is invalid
     */
    @PreAuthorize("permitAll()")
    @PostMapping("/refresh")
    public Mono<ResponseEntity<TokenResponse>> refresh(@RequestBody RefreshRequest request) {
        return jwtService.refresh(request.refreshToken())
                .map(ResponseEntity::ok);
    }

    /**
     * Issue a fresh access token for the already-authenticated caller and rotate the refresh token.
     *
     * <p>The old access token is blacklisted before the new one is returned, preventing reuse.
     * All active refresh tokens for the user are revoked and a new refresh token is issued,
     * so the caller must store the new refresh token returned in the response.
     *
     * @param authHeader     the {@code Authorization: Bearer <token>} header (used to invalidate
     *                       the old access token)
     * @param authentication injected by Spring Security's JWT filter
     * @return 200 OK with a new {@link TokenResponse} containing both new tokens, or 401 if
     *         no valid token is present
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/token/renew")
    public Mono<ResponseEntity<TokenResponse>> renewToken(
            @RequestHeader("Authorization") String authHeader,
            Authentication authentication) {
        String oldToken = extractBearer(authHeader);
        return jwtService.invalidate(oldToken)
                .then(jwtService.renewToken(authentication.getName()))
                .map(ResponseEntity::ok);
    }

    /**
     * Invalidate the caller's current session: blacklists the access token and, if a refresh token
     * is provided in the request body, revokes it too.
     *
     * @param authHeader     the {@code Authorization: Bearer <token>} header
     * @param body           optional body; supply {@code refreshToken} to also revoke it
     * @param authentication injected by Spring Security's JWT filter
     * @return 204 No Content
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/logout")
    public Mono<ResponseEntity<Void>> logout(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody(required = false) LogoutRequest body,
            Authentication authentication) {
        String accessToken = extractBearer(authHeader);
        String rawRefreshToken = body != null ? body.refreshToken() : null;
        log.atInfo().log("Logout requested by: {}", authentication.getName());
        return jwtService.logout(accessToken, rawRefreshToken)
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }

    private static String extractBearer(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return authHeader != null ? authHeader : "";
    }
}
