package org.glodean.constants.web.endpoints;

import org.glodean.constants.auth.AuthProperties;
import org.glodean.constants.dto.AuthStatusResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Always-available discovery endpoint that tells clients whether JWT authentication
 * is currently enforced by the server.
 *
 * <p>Exists unconditionally so that clients and UIs can query the auth mode
 * before deciding whether to show a login screen, regardless of whether the auth
 * subsystem itself ({@link LoginController}) is active.
 *
 * <pre>
 * GET /auth/status  → {"enabled": true|false}
 * </pre>
 */
@RestController
@RequestMapping("/auth")
public class AuthStatusController {

    private final AuthProperties authProperties;

    @Autowired
    public AuthStatusController(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    /**
     * Discover whether JWT authentication is currently enforced.
     *
     * @return 200 OK with {@link AuthStatusResponse}
     */
    @GetMapping("/status")
    public Mono<ResponseEntity<AuthStatusResponse>> authStatus() {
        return Mono.just(ResponseEntity.ok(new AuthStatusResponse(authProperties.enabled())));
    }
}
