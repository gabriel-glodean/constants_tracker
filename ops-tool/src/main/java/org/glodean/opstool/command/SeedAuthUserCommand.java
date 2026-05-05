package org.glodean.opstool.command;

import at.favre.lib.crypto.bcrypt.BCrypt;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import org.glodean.opstool.Config;
import org.glodean.opstool.util.Log;

/**
 * Upserts the demo user into {@code auth_users} with a BCrypt-hashed password.
 *
 * <p>Uses {@code at.favre.lib:bcrypt} (pure Java) — no pgcrypto extension required. The {@code
 * $2a$} prefix produced by this library is accepted by Spring Security's {@code
 * BCryptPasswordEncoder}.
 */
public class SeedAuthUserCommand implements Command {

    private static final Log log = Log.getLogger(SeedAuthUserCommand.class);
    private static final int BCRYPT_COST = 12;

    private final Config cfg;

    public SeedAuthUserCommand(Config cfg) {
        this.cfg = cfg;
    }

    @Override
    public void execute() throws Exception {
        log.info("=== Seeding demo auth user: '{}' ===", cfg.demoUsername());

        String passwordHash =
                BCrypt.withDefaults().hashToString(BCRYPT_COST, cfg.demoPassword().toCharArray());

        try (var conn =
                        DriverManager.getConnection(
                                cfg.pgJdbcUrl(), cfg.postgresUser(), cfg.postgresPassword());
                PreparedStatement ps =
                        conn.prepareStatement(
                                """
                                INSERT INTO auth_users (username, password_hash)
                                VALUES (?, ?)
                                ON CONFLICT (username)
                                DO UPDATE SET password_hash = EXCLUDED.password_hash
                                """)) {
            ps.setString(1, cfg.demoUsername());
            ps.setString(2, passwordHash);
            ps.executeUpdate();
        }

        log.info("Demo user '{}' ready", cfg.demoUsername());
    }
}
