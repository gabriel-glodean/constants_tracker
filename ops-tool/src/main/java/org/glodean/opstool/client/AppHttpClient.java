package org.glodean.opstool.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.glodean.opstool.util.Json;
import org.glodean.opstool.util.Log;

/** HTTP client for the constant-tracker app API (login, JAR upload, sync, finalize). */
public class AppHttpClient {

    private static final Log log = Log.getLogger(AppHttpClient.class);

    private final String appUrl;
    private final HttpClient http;
    private String authHeader;

    public AppHttpClient(String appUrl) {
        this.appUrl = appUrl;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public void login(String username, String password) throws Exception {
        log.info("=== Authenticating as '{}' ===", username);
        String body =
                "{\"username\":\""
                        + Json.escape(username)
                        + "\",\"password\":\""
                        + Json.escape(password)
                        + "\"}";
        var req =
                HttpRequest.newBuilder(URI.create(appUrl + "/auth/login"))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
        var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (!isOk(resp.statusCode())) {
            throw new RuntimeException("Login failed (" + resp.statusCode() + "): " + resp.body());
        }
        String token = Json.extractString(resp.body(), "accessToken");
        if (token == null || token.isBlank()) {
            throw new RuntimeException("Login returned an empty token. Response: " + resp.body());
        }
        this.authHeader = "Bearer " + token;
        log.info("Login OK");
    }

    public void uploadJar(String project, String jarName, byte[] data) throws Exception {
        var b =
                HttpRequest.newBuilder(
                                URI.create(
                                        appUrl + "/jar?project=" + project + "&jarName=" + jarName))
                        .timeout(Duration.ofSeconds(120))
                        .header("Content-Type", "application/octet-stream")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(data));
        if (authHeader != null) b.header("Authorization", authHeader);
        var resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (isOk(resp.statusCode())) {
            log.info("  OK");
        } else {
            log.error("  FAILED ({}): {}", resp.statusCode(), resp.body());
        }
    }

    public void syncAndFinalize(String project, int version) throws Exception {
        post("/project/" + project + "/version/" + version + "/sync", 60, "sync");
        post("/project/" + project + "/version/" + version + "/finalize", 60, "finalize");
    }

    private void post(String path, int timeoutS, String label) throws Exception {
        var b =
                HttpRequest.newBuilder(URI.create(appUrl + path))
                        .timeout(Duration.ofSeconds(timeoutS))
                        .POST(HttpRequest.BodyPublishers.noBody());
        if (authHeader != null) b.header("Authorization", authHeader);
        var resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (isOk(resp.statusCode())) {
            log.info("  {} OK", label);
        } else {
            log.error("  {} FAILED ({}): {}", label, resp.statusCode(), resp.body());
        }
    }

    private static boolean isOk(int status) {
        return status >= 200 && status < 300;
    }
}
