package org.glodean.constants.store.postgres.entity;

import java.time.OffsetDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * R2DBC entity for the {@code auth_users} table.
 *
 * <p>The {@code passwordHash} field is a {@code char[]} rather than a {@link String} so that
 * callers can zero it out (via {@link java.util.Arrays#fill}) immediately after the BCrypt check,
 * preventing the hash from lingering on the heap in a heap-dump-readable form. Strings are
 * immutable and cannot be cleared; {@code char[]} can.
 *
 * <p>Note: the field is intentionally <em>not</em> exposed through a repository — it is only
 * populated by {@link org.glodean.constants.auth.AuthUserService} via a plain SQL query, where the
 * driver-returned {@link String} is converted to {@code char[]} immediately in the row mapper.
 */
@Table("auth_users")
public record AuthUserEntity(
    @Id Long id,
    String username,
    char[] passwordHash,
    boolean enabled,
    OffsetDateTime createdAt,
    OffsetDateTime lastLoginAt) {}
