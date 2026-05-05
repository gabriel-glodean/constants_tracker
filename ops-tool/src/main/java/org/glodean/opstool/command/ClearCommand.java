package org.glodean.opstool.command;

import java.sql.DriverManager;
import java.sql.Statement;
import org.glodean.opstool.Config;
import org.glodean.opstool.client.SolrHttpClient;
import org.glodean.opstool.util.Log;
import redis.clients.jedis.Jedis;

/** Truncates all app tables and wipes Solr + Redis entirely. */
public class ClearCommand implements Command {

    private static final Log log = Log.getLogger(ClearCommand.class);

    /**
     * Wrapped in a DO block so the command is resilient when Flyway has not yet
     * initialised the schema (e.g. running clear before the app starts for the
     * first time).  EXCEPTION WHEN undefined_table silently skips missing tables.
     * auth_users is intentionally excluded -- it is managed by seed-auth-user.
     */
    private static final String TRUNCATE_SQL =
            "DO $$ BEGIN"
                    + " TRUNCATE unit_descriptors, project_versions, version_deletions,"
                    + " solr_outbox, solr_outbox_dead,"
                    + " auth_refresh_tokens, auth_token_blacklist CASCADE;"
                    + " EXCEPTION WHEN undefined_table THEN"
                    + " RAISE WARNING 'Some tables do not exist yet — skipped.';"
                    + " END $$";

    private final Config cfg;

    public ClearCommand(Config cfg) {
        this.cfg = cfg;
    }

    @Override
    public void execute() throws Exception {
        log.info("=== Clearing PostgreSQL ===");
        try (var conn =
                        DriverManager.getConnection(
                                cfg.pgJdbcUrl(), cfg.postgresUser(), cfg.postgresPassword());
                Statement stmt = conn.createStatement()) {
            stmt.execute(TRUNCATE_SQL);
        }
        log.info("PostgreSQL cleared");

        log.info("=== Clearing Solr ===");
        var solr = new SolrHttpClient(cfg.solrCoreUrl(), cfg.solrPingTimeoutS());
        solr.waitForReady();
        solr.deleteByQuery("*:*");
        log.info("Solr cleared");

        log.info("=== Clearing Redis ===");
        try (var jedis = new Jedis(cfg.redisHost(), cfg.redisPort())) {
            jedis.flushAll();
        }
        log.info("Redis cleared");

        log.info("=== Clear complete ===");
    }
}
