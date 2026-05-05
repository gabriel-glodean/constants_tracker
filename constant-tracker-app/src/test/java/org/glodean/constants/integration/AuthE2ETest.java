package org.glodean.constants.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.redis.testcontainers.RedisContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end tests for the authentication flow.
 *
 * <p>JWT enforcement is active ({@code constants.auth.enabled=true}) and the JWT filter
 * is wired into the secured chain.  Tests exercise the full flow: login, token renewal,
 * and logout with real signed tokens.
 *
 * <p>A real user row is inserted into PostgreSQL via {@link DatabaseClient} before each
 * test, using a live BCrypt hash produced by the application's own {@link PasswordEncoder}.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
        properties = {
                "constants.auth.enabled=true",
                "constants.auth.jwt.secret=e2e-test-secret-key-for-jwt-signing-32cb",
                "constants.auth.jwt.expiration-ms=60000",
                "constants.auth.refresh-token.ttl-ms=3600000"
        })
class AuthE2ETest {
    // -------------------------------------------------------------------------
    // Containers
    // -------------------------------------------------------------------------
    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer("redis:7");
    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17").withDatabaseName("constant_tracker");
    @Container
    static GenericContainer<?> solr;

    static {
        try {
            Path configsetDir = Files.createTempDirectory("solr-configset-auth");
            Path sourceDir = Paths.get(System.getProperty("user.dir"), "solr");
            try (Stream<Path> files = Files.list(sourceDir)) {
                files
                        .filter(p -> !p.getFileName().toString().equals("core.properties"))
                        .forEach(
                                p -> {
                                    try {
                                        Files.copy(
                                                p,
                                                configsetDir.resolve(p.getFileName()),
                                                StandardCopyOption.REPLACE_EXISTING);
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                });
            }
            solr =
                    new GenericContainer<>("solr:10")
                            .withExposedPorts(8983)
                            .withNetworkAliases("solr-auth")
                            .withCommand("solr-precreate", "Constants", "/var/solr/configsets/constants_conf")
                            .withCopyFileToContainer(
                                    org.testcontainers.utility.MountableFile.forHostPath(configsetDir, 0777),
                                    "/var/solr/configsets/constants_conf")
                            .waitingFor(
                                    Wait.forHttp("/solr/admin/cores?action=STATUS").forStatusCode(200))
                            .withStartupTimeout(Duration.ofMinutes(2));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        String solrUrl =
                "http://" + solr.getHost() + ":" + solr.getMappedPort(8983) + "/solr/";
        r.add("constants.solr.url", () -> solrUrl);
        r.add(
                "spring.r2dbc.url",
                () ->
                        "r2dbc:postgresql://"
                                + postgres.getHost()
                                + ":"
                                + postgres.getMappedPort(5432)
                                + "/"
                                + postgres.getDatabaseName());
        r.add("spring.r2dbc.username", postgres::getUsername);
        r.add("spring.r2dbc.password", postgres::getPassword);
        r.add("spring.flyway.url", postgres::getJdbcUrl);
        r.add("spring.flyway.user", postgres::getUsername);
        r.add("spring.flyway.password", postgres::getPassword);
    }

    // -------------------------------------------------------------------------
    // Injected beans
    // -------------------------------------------------------------------------
    @Autowired
    WebTestClient web;
    @Autowired
    DatabaseClient db;
    @Autowired
    PasswordEncoder passwordEncoder;
    // -------------------------------------------------------------------------
    // Test data
    // -------------------------------------------------------------------------
    private static final String TEST_USER = "auth-e2e-user";
    private static final String TEST_PASS = "e2e-test-password-123";

    @BeforeEach
    void insertTestUser() {
        String hash = passwordEncoder.encode(TEST_PASS);
        db.sql(
                        """
                                INSERT INTO auth_users (username, password_hash, enabled)
                                VALUES (:user, :hash, TRUE)
                                ON CONFLICT (username) DO UPDATE SET password_hash = EXCLUDED.password_hash
                                """)
                .bind("user", TEST_USER)
                .bind("hash", hash)
                .fetch()
                .rowsUpdated()
                .block();
    }

    // -------------------------------------------------------------------------
    // GET /auth/status
    // -------------------------------------------------------------------------
    @Test
    void authStatus_returnsEnabledTrue() {
        web.get()
                .uri("/auth/status")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.enabled").isEqualTo(true);
    }

    // -------------------------------------------------------------------------
    // POST /auth/login — success path
    // -------------------------------------------------------------------------
    @Test
    void login_validCredentials_returnsSignedJwt() {
        web.post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"" + TEST_USER + "\",\"password\":\"" + TEST_PASS + "\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accessToken").isNotEmpty()
                .jsonPath("$.refreshToken").isNotEmpty()
                .jsonPath("$.expiresInMs").isEqualTo(60_000);
    }

    @Test
    void login_validCredentials_tokenHasThreeParts() {
        byte[] body =
                web.post()
                        .uri("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue("{\"username\":\"" + TEST_USER + "\",\"password\":\"" + TEST_PASS + "\"}")
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody()
                        .returnResult()
                        .getResponseBody();
        assertThat(body).isNotNull();
        String json = new String(body);
        // Extract the accessToken value (simple check that it is a 3-part JWT)
        assertThat(json).contains("accessToken");
        String token = json.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
        assertThat(token.split("\\.")).hasSize(3);
    }

    // -------------------------------------------------------------------------
    // POST /auth/login — failure paths
    // -------------------------------------------------------------------------
    @Test
    void login_wrongPassword_returns401() {
        web.post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"" + TEST_USER + "\",\"password\":\"wrong-password\"}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void login_unknownUser_returns401() {
        web.post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"ghost\",\"password\":\"anything\"}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // -------------------------------------------------------------------------
    // POST /auth/logout
    // -------------------------------------------------------------------------
    @Test
    void logout_withValidToken_returns204() {
        // First obtain a real token pair
        byte[] loginBody =
                web.post()
                        .uri("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(
                                "{\"username\":\"" + TEST_USER + "\",\"password\":\"" + TEST_PASS + "\"}")
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody()
                        .returnResult()
                        .getResponseBody();
        assertThat(loginBody).isNotNull();
        String json = new String(loginBody);
        String accessToken = json.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
        String refreshToken = json.replaceAll(".*\"refreshToken\":\"([^\"]+)\".*", "$1");

        web.post()
                .uri("/auth/logout")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"refreshToken\":\"" + refreshToken + "\"}")
                .exchange()
                .expectStatus().isNoContent();
    }

    // -------------------------------------------------------------------------
    // POST /auth/refresh
    // -------------------------------------------------------------------------
    @Test
    void refresh_withValidRefreshToken_returnsNewTokenPair() {
        // Login to get initial tokens
        byte[] loginBody =
                web.post()
                        .uri("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(
                                "{\"username\":\"" + TEST_USER + "\",\"password\":\"" + TEST_PASS + "\"}")
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody()
                        .returnResult()
                        .getResponseBody();
        assertThat(loginBody).isNotNull();
        String loginJson = new String(loginBody);
        String refreshToken = loginJson.replaceAll(".*\"refreshToken\":\"([^\"]+)\".*", "$1");

        // Exchange refresh token for a new pair
        web.post()
                .uri("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"refreshToken\":\"" + refreshToken + "\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accessToken").isNotEmpty()
                .jsonPath("$.refreshToken").isNotEmpty()
                .jsonPath("$.expiresInMs").isEqualTo(60_000);
    }

    @Test
    void refresh_withInvalidRefreshToken_returns401() {
        web.post()
                .uri("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"refreshToken\":\"not-a-real-token\"}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void refresh_afterLogout_oldRefreshTokenIsRevoked() {
        // Login
        byte[] loginBody =
                web.post()
                        .uri("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(
                                "{\"username\":\"" + TEST_USER + "\",\"password\":\"" + TEST_PASS + "\"}")
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody()
                        .returnResult()
                        .getResponseBody();
        assertThat(loginBody).isNotNull();
        String json = new String(loginBody);
        String accessToken = json.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
        String refreshToken = json.replaceAll(".*\"refreshToken\":\"([^\"]+)\".*", "$1");

        // Logout — revokes the refresh token
        web.post()
                .uri("/auth/logout")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"refreshToken\":\"" + refreshToken + "\"}")
                .exchange()
                .expectStatus().isNoContent();

        // Attempt to reuse the old refresh token — must be rejected
        web.post()
                .uri("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"refreshToken\":\"" + refreshToken + "\"}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // -------------------------------------------------------------------------
    // POST /auth/token/renew (legacy — access-token-based renewal)
    // -------------------------------------------------------------------------
    @Test
    void renewToken_afterLogin_returnsNewTokenAndRotatesRefreshToken() {
        byte[] body = web.post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                        "{\"username\":\"" + TEST_USER + "\",\"password\":\"" + TEST_PASS + "\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .returnResult()
                .getResponseBody();
        assertThat(body).isNotNull();
        String json = new String(body);
        String oldToken = json.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
        String oldRefreshToken = json.replaceAll(".*\"refreshToken\":\"([^\"]+)\".*", "$1");

        byte[] renewBody = web.post()
                .uri("/auth/token/renew")
                .header("Authorization", "Bearer " + oldToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accessToken").isNotEmpty()
                .jsonPath("$.refreshToken").isNotEmpty() // rotation: new refresh token returned
                .returnResult()
                .getResponseBody();
        assertThat(renewBody).isNotNull();
        String renewJson = new String(renewBody);
        String newRefreshToken = renewJson.replaceAll(".*\"refreshToken\":\"([^\"]+)\".*", "$1");

        // The old refresh token should be revoked — attempt to use it must be rejected
        assertThat(newRefreshToken).isNotEqualTo(oldRefreshToken);
        web.post()
                .uri("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"refreshToken\":\"" + oldRefreshToken + "\"}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // -------------------------------------------------------------------------
    // Security response headers
    // -------------------------------------------------------------------------
    @Test
    void anyResponse_includesXFrameOptionsDeny() {
        web.get()
                .uri("/auth/status")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Frame-Options", "DENY");
    }

    // Note: Strict-Transport-Security is intentionally omitted — Spring Security only emits
    // it on HTTPS connections and the E2E test server runs on plain HTTP.

    @Test
    void anyResponse_includesReferrerPolicyHeader() {
        web.get()
                .uri("/auth/status")
                .exchange()
                .expectHeader().valueEquals("Referrer-Policy", "strict-origin-when-cross-origin");
    }

    // -------------------------------------------------------------------------
    // Input validation — blank credentials
    // -------------------------------------------------------------------------
    @Test
    void login_blankUsername_returns400() {
        web.post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"\",\"password\":\"" + TEST_PASS + "\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.message").value(msg ->
                        assertThat((String) msg).contains("username"));
    }

    @Test
    void login_blankPassword_returns400() {
        web.post()
                .uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"" + TEST_USER + "\",\"password\":\"\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.message").value(msg ->
                        assertThat((String) msg).contains("password"));
    }
}
