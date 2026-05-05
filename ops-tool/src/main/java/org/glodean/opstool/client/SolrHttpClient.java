package org.glodean.opstool.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.glodean.opstool.util.Json;
import org.glodean.opstool.util.Log;

/**
 * Thin wrapper around {@link java.net.http.HttpClient} for Solr update operations.
 */
public class SolrHttpClient {

    private static final Log log = Log.getLogger(SolrHttpClient.class);

    private final String coreUrl;
    private final int pingTimeoutS;
    private final HttpClient http;

    public SolrHttpClient(String coreUrl, int pingTimeoutS) {
        this.coreUrl = coreUrl;
        this.pingTimeoutS = pingTimeoutS;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    /** Blocks until the Solr core ping endpoint returns HTTP 200 or the timeout elapses. */
    public void waitForReady() throws InterruptedException {
        String pingUrl = coreUrl + "/admin/ping";
        long deadline = System.currentTimeMillis() + pingTimeoutS * 1000L;
        int attempt = 0;
        while (System.currentTimeMillis() < deadline) {
            attempt++;
            try {
                var req =
                        HttpRequest.newBuilder(URI.create(pingUrl))
                                .timeout(Duration.ofSeconds(5))
                                .GET()
                                .build();
                var resp = http.send(req, HttpResponse.BodyHandlers.discarding());
                if (resp.statusCode() == 200) {
                    log.debug("Solr ready after {} attempt(s)", attempt);
                    return;
                }
            } catch (Exception ignored) {
                // connection refused / timeout — keep polling
            }
            Thread.sleep(1000);
        }
        throw new RuntimeException(
                "Solr core at " + coreUrl + " was not ready within " + pingTimeoutS + "s");
    }

    /**
     * Posts a {@code delete} command to Solr and commits.
     *
     * <p>Validates that {@code responseHeader.status == 0}.
     */
    public void deleteByQuery(String query) throws Exception {
        String body = "{\"delete\":{\"query\":\"" + Json.escape(query) + "\"}}";
        var req =
                HttpRequest.newBuilder(URI.create(coreUrl + "/update?commit=true"))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
        var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new RuntimeException(
                    "Solr update failed (" + resp.statusCode() + "): " + resp.body());
        }
        int status = Json.extractInt(resp.body(), "status", -1);
        if (status != 0) {
            throw new RuntimeException("Solr returned non-zero status: " + status);
        }
    }
}
