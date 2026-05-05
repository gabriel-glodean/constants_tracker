/**
 * PostgreSQL store implementation using Spring Data R2DBC (reactive).
 *
 * <p>{@link org.glodean.constants.store.postgres.PostgresService} is the primary
 * write/read service. It persists versioned unit snapshots, individual constant usages,
 * version metadata, and deletion records. Schema migrations are managed by Flyway
 * (JDBC) and run at application startup before R2DBC connections are opened.
 *
 * <p>Sub-packages:
 * <ul>
 *   <li>{@code store.postgres.entity} — R2DBC entity classes mapped to database tables.</li>
 *   <li>{@code store.postgres.repository} — Spring Data reactive repository interfaces.</li>
 * </ul>
 */
package org.glodean.constants.store.postgres;
