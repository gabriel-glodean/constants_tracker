package org.glodean.constants.integration;

import com.redis.testcontainers.RedisContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "test.e2e", matches = "true")
class E2EIngestTest {

    // --- Redis ---
    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer("redis:7");

    // --- PostgreSQL ---
    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17").withDatabaseName("constant_tracker");

    // --- Solr (standalone) ---
    @Container
    static GenericContainer<?> solr =
            new GenericContainer<>("solr:latest")
                    .withExposedPorts(8983)
                    .withNetworkAliases("solar")
                    .withCommand("solr-precreate", "Constants", "/var/solr/configsets/constants_conf")
                    .withCopyFileToContainer(
                            org.testcontainers.utility.MountableFile.forHostPath(
                                    Paths.get("").resolve("solr"), 0777),
                            "/var/solr/configsets/constants_conf/conf")
                    .waitingFor(Wait.forHttp("/solr/admin/cores?action=STATUS").forStatusCode(200))
                    .withStartupTimeout(Duration.ofMinutes(2));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        // Solr
        String solrUrl = "http://" + solr.getHost() + ":" + solr.getMappedPort(8983) + "/solr/";
        r.add("constants.solr.url", () -> solrUrl);

        // PostgreSQL – R2DBC (app)
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

        // PostgreSQL – Flyway JDBC (schema migrations)
        r.add("spring.flyway.url", postgres::getJdbcUrl);
        r.add("spring.flyway.user", postgres::getUsername);
        r.add("spring.flyway.password", postgres::getPassword);
    }

    @Autowired
    WebTestClient web;

    @Autowired
    private RedisConnectionFactory rcf;

    @Test
    void endToEndUploadAndQuery() throws Exception {
        byte[] clazz = Files.readAllBytes(Path.of("src/test/resources/samples/Greeter.class"));
        web.post()
                .uri("/class?project=demo&version=1")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .bodyValue(clazz)
                .exchange()
                .expectStatus()
                .is2xxSuccessful();

        web.get()
                .uri("/class?project=demo&version=1&className=org/glodean/constants/samples/Greeter")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.constants")
                .isNotEmpty();

        if (rcf instanceof LettuceConnectionFactory lcf) {
            lcf.destroy();
        }
    }
}
