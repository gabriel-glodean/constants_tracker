package org.glodean.opstool;

import java.util.Set;

/**
 * Immutable configuration record built from environment variables.
 *
 * <p>All validation happens inside {@link #fromEnv()} so callers receive a fully-valid object or
 * get a descriptive {@link IllegalArgumentException}.
 */
public record Config(
        String cmd,
        // PostgreSQL
        String postgresHost,
        int postgresPort,
        String postgresUser,
        String postgresPassword,
        String postgresDb,
        // Solr
        String solrUrl,
        String solrCore,
        int solrPingTimeoutS,
        // Redis
        String redisHost,
        int redisPort,
        // App / Seed
        String appUrl,
        String project,
        String jarDirV1,
        String jarDirV2,
        // Auth
        boolean authEnabled,
        String demoUsername,
        String demoPassword,
        // Clear-demo
        String demoProject) {

    private static final Set<String> VALID_COMMANDS =
            Set.of("seed", "clear", "clear-demo", "seed-auth-user");

    private static final Set<String> POSTGRES_REQUIRED =
            Set.of("clear", "clear-demo", "seed-auth-user");

    public static Config fromEnv() {
        String cmd = requireEnv("CMD");
        if (!VALID_COMMANDS.contains(cmd)) {
            throw new IllegalArgumentException(
                    "CMD must be one of " + VALID_COMMANDS + ", got: " + cmd);
        }

        boolean needsPg = POSTGRES_REQUIRED.contains(cmd);
        String postgresUser = needsPg ? requireEnv("POSTGRES_USER") : env("POSTGRES_USER", "");
        String postgresPassword =
                needsPg ? requireEnv("POSTGRES_PASSWORD") : env("POSTGRES_PASSWORD", "");

        boolean authEnabled = Boolean.parseBoolean(env("AUTH_ENABLED", "false"));
        String demoUsername = System.getenv("DEMO_USERNAME");
        String demoPassword = System.getenv("DEMO_PASSWORD");

        if ((authEnabled || "seed-auth-user".equals(cmd))
                && (demoUsername == null || demoPassword == null)) {
            throw new IllegalArgumentException(
                    "DEMO_USERNAME and DEMO_PASSWORD are required when "
                            + "AUTH_ENABLED=true or CMD=seed-auth-user");
        }

        return new Config(
                cmd,
                env("POSTGRES_HOST", "postgres"),
                intEnv("POSTGRES_PORT", 5432),
                postgresUser,
                postgresPassword,
                env("POSTGRES_DB", "constant_tracker"),
                env("SOLR_URL", "http://solr:8983/solr/"),
                env("SOLR_CORE", "Constants"),
                intEnv("SOLR_PING_TIMEOUT_S", 120),
                env("REDIS_HOST", "redis"),
                intEnv("REDIS_PORT", 6379),
                env("APP_URL", "http://app:8080"),
                env("PROJECT", "demo-crud-server"),
                env("JAR_DIR_V1", "/seed/jars/v1"),
                env("JAR_DIR_V2", "/seed/jars/v2"),
                authEnabled,
                demoUsername,
                demoPassword,
                env("DEMO_PROJECT", "demo-crud-server"));
    }

    /** JDBC connection URL for the PostgreSQL driver. */
    public String pgJdbcUrl() {
        return "jdbc:postgresql://" + postgresHost + ":" + postgresPort + "/" + postgresDb;
    }

    /** Base URL of the configured Solr core (no trailing slash). */
    public String solrCoreUrl() {
        String base = solrUrl.endsWith("/") ? solrUrl : solrUrl + "/";
        return base + solrCore;
    }

    // ── env helpers ───────────────────────────────────────────────────────────

    private static String requireEnv(String name) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("Required environment variable not set: " + name);
        }
        return v;
    }

    private static String env(String name, String defaultValue) {
        String v = System.getenv(name);
        return v != null ? v : defaultValue;
    }

    private static int intEnv(String name, int defaultValue) {
        String v = System.getenv(name);
        if (v == null) return defaultValue;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be an integer, got: " + v);
        }
    }
}

