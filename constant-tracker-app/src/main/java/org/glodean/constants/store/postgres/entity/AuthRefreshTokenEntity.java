package org.glodean.constants.store.postgres.entity;

import java.time.OffsetDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * R2DBC entity for the {@code auth_refresh_tokens} table.
 *
 * <p>Holds a SHA-256 hash of the raw refresh token string. The raw token is only returned to the
 * client at issuance time and is never stored. Tokens are invalidated by flipping {@code revoked}
 * to {@code true} (rotation strategy: every refresh issues a new token and revokes the old one).
 */
@Table("auth_refresh_tokens")
public record AuthRefreshTokenEntity(
    @Id Long id,
    Long userId,
    String tokenHash,
    OffsetDateTime issuedAt,
    OffsetDateTime expiresAt,
    boolean revoked,
    OffsetDateTime revokedAt) {}
