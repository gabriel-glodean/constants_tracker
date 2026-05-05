package org.glodean.constants.auth;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter;
import org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter;
import org.springframework.web.server.WebFilter;

import java.util.List;

/**
 * Spring Security configuration for the WebFlux application.
 *
 * <p>The top-level class only provides always-needed beans (e.g. {@link PasswordEncoder}).
 * The two inner configurations are mutually exclusive via {@link ConditionalOnProperty}:
 *
 * <ul>
 *   <li>{@link SecuredConfig} — active when {@code constants.auth.enabled=true} (default).
 *       Enables {@code @PreAuthorize} method security and installs a JWT bearer filter.</li>
 *   <li>{@link OpenConfig}   — active when {@code constants.auth.enabled=false}.
 *       Permits all requests and does <em>not</em> enable method security, so
 *       {@code @PreAuthorize} annotations are entirely inert.</li>
 * </ul>
 */
@Configuration
public class SecurityConfiguration {

    // ---------------------------------------------------------------------------
    // auth.enabled=true  →  JWT filter + @PreAuthorize enforcement
    // ---------------------------------------------------------------------------

    @Configuration
    @EnableReactiveMethodSecurity
    @ConditionalOnProperty(name = "constants.auth.enabled", havingValue = "true", matchIfMissing = true)
    static class SecuredConfig {

        /**
         * BCrypt encoder used by {@link JwtService} for credential verification. Always available.
         */
        @Bean
        public PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }

        @Bean
        public SecurityWebFilterChain securedFilterChain(ServerHttpSecurity http, JwtService jwtService) {
            http.csrf(ServerHttpSecurity.CsrfSpec::disable);
            // Disable HTTP Basic to prevent browser auth popup on 401
            http.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable);
            // Disable form login as well
            http.formLogin(ServerHttpSecurity.FormLoginSpec::disable);
            http.addFilterAt(jwtAuthFilter(jwtService), SecurityWebFiltersOrder.AUTHENTICATION);
            http.headers(headers -> headers
                    .frameOptions(f -> f.mode(XFrameOptionsServerHttpHeadersWriter.Mode.DENY))
                    // HSTS is intentionally omitted — TLS is terminated by Cloudflare;
                    // the application only ever sees plain HTTP internally.
                    .hsts(ServerHttpSecurity.HeaderSpec.HstsSpec::disable)
                    .referrerPolicy(r -> r.policy(
                            ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            );
            http.authorizeExchange(ex -> ex
                    .pathMatchers(
                            "/auth/login",
                            "/auth/refresh",
                            "/auth/status",
                            "/actuator/health",
                            "/actuator/health/**",
                            "/actuator/info",
                            "/actuator/prometheus"
                    ).permitAll()
                    .anyExchange().authenticated());
            // Custom entry point that returns 401 without WWW-Authenticate header
            http.exceptionHandling(exceptions -> exceptions
                    .authenticationEntryPoint((exchange, _) -> {
                        exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    }));
            return http.build();
        }

        /**
         * Reads {@code Authorization: Bearer <token>}, validates it, and populates the
         * reactive security context on success. On missing or invalid token the request
         * continues unauthenticated so that Spring Security's authorize rules return 401
         * for protected paths.
         */
        private WebFilter jwtAuthFilter(JwtService jwtService) {
            return (exchange, chain) -> {
                String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    return chain.filter(exchange);
                }
                String token = authHeader.substring(7);
                return jwtService.isValid(token)
                        .flatMap(valid -> {
                            if (!valid) return chain.filter(exchange);
                            String subject;
                            try {
                                subject = jwtService.extractSubject(token);
                            } catch (Exception e) {
                                return chain.filter(exchange);
                            }
                            Authentication auth = new UsernamePasswordAuthenticationToken(
                                    subject, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                            return chain.filter(exchange)
                                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
                        });
            };
        }
    }

    // ---------------------------------------------------------------------------
    // auth.enabled=false  →  open chain, NO method security
    // ---------------------------------------------------------------------------

    @Configuration
    @ConditionalOnProperty(name = "constants.auth.enabled", havingValue = "false")
    static class OpenConfig {

        private static final Logger log = LogManager.getLogger(OpenConfig.class);

        /**
         * Permits all requests. {@code @EnableReactiveMethodSecurity} is intentionally
         * absent so that {@code @PreAuthorize} annotations on controllers are completely
         * ignored — no authentication is needed or checked anywhere.
         */
        @Bean
        public SecurityWebFilterChain openFilterChain(ServerHttpSecurity http) {
            log.warn("*** Authentication is DISABLED — all endpoints are publicly accessible ***");
            http.csrf(ServerHttpSecurity.CsrfSpec::disable);
            http.authorizeExchange(ex -> ex.anyExchange().permitAll());
            return http.build();
        }
    }
}
