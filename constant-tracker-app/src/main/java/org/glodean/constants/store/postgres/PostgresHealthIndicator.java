package org.glodean.constants.store.postgres;

import io.r2dbc.spi.ConnectionFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Reactive health indicator for the PostgreSQL (R2DBC) connection.
 *
 * <p>Exposed at {@code GET /actuator/health/postgres} as part of the Spring Boot Actuator
 * health endpoint. Goes beyond the generic R2DBC ping by querying the server version,
 * giving operators useful context alongside the UP/DOWN status.
 *
 * <p>The auto-configured {@code r2dbc} indicator is disabled in {@code application.yaml}
 * ({@code management.health.r2dbc.enabled: false}) to avoid redundancy.
 */
@Component("postgres")
public class PostgresHealthIndicator implements ReactiveHealthIndicator {

    private static final Logger log = LogManager.getLogger(PostgresHealthIndicator.class);

    private final DatabaseClient databaseClient;

    public PostgresHealthIndicator(ConnectionFactory connectionFactory) {
        this.databaseClient = DatabaseClient.create(connectionFactory);
    }

    @Override
    public Mono<Health> health() {
        return databaseClient
                .sql("SELECT version()")
                .map(row -> row.get(0, String.class))
                .one()
                .map(version -> Health.up()
                        .withDetail("version", version)
                        .build())
                .onErrorResume(ex -> {
                    log.atError().withThrowable(ex).log("PostgreSQL health check failed: {}", ex.getMessage());
                    return Mono.just(Health.down()
                            .withDetail("error", ex.getMessage())
                            .build());
                });
    }
}
