package org.glodean.constants.integration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class E2EIngestTest {

    // --- Redis ---
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7")
            .withExposedPorts(6379)
            .withNetworkAliases("redis")
            .waitingFor(Wait.forListeningPort());

    // --- Solr (standalone) ---
    @Container
    static GenericContainer<?> solr = new GenericContainer<>("solr:9")
            .withExposedPorts(8983)
            .withNetworkAliases("solar")
            .withCommand("solr-precreate", "Constants", "/var/solr/configsets/constants_conf")
            // put your configset under /var/solr/configsets/constants_conf (writable)
            .withCopyFileToContainer(
                    org.testcontainers.utility.MountableFile.forHostPath(Paths.get("").resolve("solr"), 0777),
                    "/var/solr/configsets/constants_conf/conf")
            .waitingFor(Wait.forHttp("/solr/admin/cores?action=STATUS").forStatusCode(200));


    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        String solrUrl = "http://" + solr.getHost() + ":" + solr.getMappedPort(8983) + "/solr/";
        r.add("constants.solr.url", () -> solrUrl); // <-- adapt to your property name

        r.add("spring.data.redis.host", () -> redis.getHost());
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        // If you use different keys (e.g., custom Redis props), set them here.
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
                .expectStatus().is2xxSuccessful();


        web.get()
                .uri("/class?project=demo&version=1&className=org/glodean/constants/samples/Greeter")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.constants").isNotEmpty();

        if (rcf instanceof LettuceConnectionFactory lcf) {
            lcf.destroy(); // closes native connections and client resources
        }
    }
}