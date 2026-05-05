package org.glodean.constants.store.postgres.entity;

import java.time.OffsetDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

/**
 * R2DBC entity for the {@code auth_token_blacklist} table.
 *
 * <p>Records JTI (JWT ID) claims of access tokens that were revoked before their natural expiry
 * (e.g. via explicit logout). Rows whose {@code expiresAt} is in the past are safe to purge
 * because any matching token would already be expired on its own.
 *
 * <p>Implements {@link Persistable} so that Spring Data R2DBC always INSERTs this entity
 * (the JTI is externally generated, never auto-assigned by the DB).
 */
@Table("auth_token_blacklist")
public record AuthTokenBlacklistEntity(
    @Id String jti,
    Long userId,
    OffsetDateTime expiresAt,
    OffsetDateTime revokedAt) implements Persistable<String> {

  @Override
  public String getId() {
    return jti;
  }

  /** Always {@code true} — entries are only ever inserted, never updated. */
  @Override
  public boolean isNew() {
    return true;
  }
}
