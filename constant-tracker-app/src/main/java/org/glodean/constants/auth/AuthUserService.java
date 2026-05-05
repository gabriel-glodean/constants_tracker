package org.glodean.constants.auth;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glodean.constants.store.postgres.entity.AuthUserEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Service for {@code auth_users} lookups.
 *
 * <p>Uses {@link DatabaseClient} directly with a plain SQL query — no repository needed because
 * the only required operation is a single {@code SELECT} by username.
 */
@Service
public record AuthUserService(@Autowired DatabaseClient db) {

  private static final Logger log = LogManager.getLogger(AuthUserService.class);

  private static final String FIND_BY_USERNAME =
      """
      SELECT id, username, password_hash, enabled, created_at, last_login_at
        FROM auth_users
       WHERE username = :username
         AND enabled  = TRUE
      """;

  private static final String FIND_BY_ID =
      """
      SELECT id, username, password_hash, enabled, created_at, last_login_at
        FROM auth_users
       WHERE id      = :id
         AND enabled = TRUE
      """;

  private static final String UPDATE_LAST_LOGIN =
      """
      UPDATE auth_users
         SET last_login_at = :lastLoginAt
       WHERE id = :id
      """;

  /**
   * Finds an enabled user by their unique username.
   *
   * @param username the account identifier
   * @return a {@link Mono} emitting the {@link AuthUserEntity}, or empty if not found or disabled
   */
  public Mono<AuthUserEntity> findByUsername(String username) {
    log.atDebug().log("Looking up user: {}", username);
    return db.sql(FIND_BY_USERNAME)
        .bind("username", username)
        .map((row, _) -> new AuthUserEntity(
            row.get("id", Long.class),
            row.get("username", String.class),
            // Convert to char[] immediately so the String is never captured and the
            // caller can zero the array (Arrays.fill(hash, '\0')) after BCrypt verification.
            Optional.ofNullable(row.get("password_hash", String.class))
                .map(String::toCharArray)
                .orElse(new char[0]),
            Boolean.TRUE.equals(row.get("enabled", Boolean.class)),
            row.get("created_at", OffsetDateTime.class),
            row.get("last_login_at", OffsetDateTime.class)))
        .first()
        .doOnNext(u -> log.atDebug().log("Found enabled user id={}", u.id()))
        .switchIfEmpty(Mono.defer(() -> {
          log.atDebug().log("No enabled user found for username: {}", username);
          return Mono.empty();
        }));
  }

  /**
   * Finds an enabled user by their primary key. Used during refresh-token rotation to resolve
   * the user without requiring a username lookup.
   *
   * @param id the user's primary key
   * @return a {@link Mono} emitting the {@link AuthUserEntity}, or empty if not found or disabled
   */
  public Mono<AuthUserEntity> findById(Long id) {
    log.atDebug().log("Looking up user by id: {}", id);
    return db.sql(FIND_BY_ID)
        .bind("id", id)
        .map((row, _) -> new AuthUserEntity(
            row.get("id", Long.class),
            row.get("username", String.class),
            Optional.ofNullable(row.get("password_hash", String.class))
                .map(String::toCharArray)
                .orElse(new char[0]),
            Boolean.TRUE.equals(row.get("enabled", Boolean.class)),
            row.get("created_at", OffsetDateTime.class),
            row.get("last_login_at", OffsetDateTime.class)))
        .first()
        .doOnNext(u -> log.atDebug().log("Found enabled user id={}", u.id()))
        .switchIfEmpty(Mono.defer(() -> {
          log.atDebug().log("No enabled user found for id: {}", id);
          return Mono.empty();
        }));
  }

  /**
   * Stamps {@code last_login_at} to the current instant for the given user id.
   * Should be called immediately after a successful credential check.
   *
   * @param userId the user's primary key
   * @return a {@link Mono} that completes when the row is updated
   */
  public Mono<Void> updateLastLogin(Long userId) {
    OffsetDateTime now = OffsetDateTime.now();
    log.atDebug().log("Updating last_login_at for userId={}", userId);
    return db.sql(UPDATE_LAST_LOGIN)
        .bind("lastLoginAt", now)
        .bind("id", userId)
        .fetch()
        .rowsUpdated()
        .then();
  }
}
