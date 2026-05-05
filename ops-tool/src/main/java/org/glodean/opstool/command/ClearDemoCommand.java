package org.glodean.opstool.command;

import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.glodean.opstool.Config;
import org.glodean.opstool.client.SolrHttpClient;
import org.glodean.opstool.util.Log;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

/** Deletes all data for a single project from PostgreSQL, Solr, and Redis. */
public class ClearDemoCommand implements Command {

    private static final Log log = Log.getLogger(ClearDemoCommand.class);

    // Child tables first to avoid FK violations when deleting
    private static final List<String> PG_TABLES =
            List.of(
                    "solr_outbox_dead",
                    "solr_outbox",
                    "version_deletions",
                    "unit_descriptors",
                    "project_versions");

    private final Config cfg;

    public ClearDemoCommand(Config cfg) {
        this.cfg = cfg;
    }

    @Override
    public void execute() throws Exception {
        String project = cfg.demoProject();
        log.info("=== Clearing demo project: {} ===", project);

        log.info("--- PostgreSQL ---");
        try (var conn =
                DriverManager.getConnection(
                        cfg.pgJdbcUrl(), cfg.postgresUser(), cfg.postgresPassword())) {
            conn.setAutoCommit(false);
            for (String table : PG_TABLES) {
                try (PreparedStatement ps =
                        conn.prepareStatement("DELETE FROM " + table + " WHERE project = ?")) {
                    ps.setString(1, project);
                    int n = ps.executeUpdate();
                    log.debug("Deleted {} row(s) from {} for project='{}'", n, table, project);
                } catch (java.sql.SQLException ex) {
                    // SQLState 42P01 = undefined_table (Flyway not yet run)
                    if ("42P01".equals(ex.getSQLState())) {
                        log.warn("Table '{}' does not exist yet — skipping.", table);
                        conn.rollback();
                        conn.setAutoCommit(false);
                    } else {
                        throw ex;
                    }
                }
            }
            conn.commit();
        }
        log.info("PostgreSQL cleared for project '{}'", project);

        log.info("--- Solr ---");
        var solr = new SolrHttpClient(cfg.solrCoreUrl(), cfg.solrPingTimeoutS());
        solr.waitForReady();
        solr.deleteByQuery("project:\"" + project + "\"");
        log.info("Solr cleared for project '{}'", project);

        log.info("--- Redis ---");
        try (var jedis = new Jedis(cfg.redisHost(), cfg.redisPort())) {
            Set<String> keys = scanKeys(jedis, "*" + project + "*");
            if (!keys.isEmpty()) {
                jedis.del(keys.toArray(String[]::new));
                log.info("Redis: deleted {} key(s) for project '{}'", keys.size(), project);
            } else {
                log.info("Redis: no keys found for project '{}'", project);
            }
        }

        log.info("=== Clear-demo complete ===");
    }

    /** Uses SCAN (cursor-based, O(1) per call) instead of KEYS (O(N), blocking). */
    private static Set<String> scanKeys(Jedis jedis, String pattern) {
        var params = new ScanParams().match(pattern).count(200);
        var keys = new HashSet<String>();
        String cursor = ScanParams.SCAN_POINTER_START;
        do {
            ScanResult<String> result = jedis.scan(cursor, params);
            keys.addAll(result.getResult());
            cursor = result.getCursor();
        } while (!cursor.equals(ScanParams.SCAN_POINTER_START));
        return keys;
    }
}
