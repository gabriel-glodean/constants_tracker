-- ============================================================
-- auth_users: application accounts used for JWT authentication
-- ============================================================
CREATE TABLE auth_users
(
    id              BIGSERIAL    PRIMARY KEY,
    username        VARCHAR(150) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,          -- BCrypt hash
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_login_at   TIMESTAMPTZ
);

CREATE INDEX idx_auth_users_username ON auth_users (username);

-- ============================================================
-- auth_refresh_tokens: server-side record of issued refresh tokens.
-- Revoked on logout or when a new token is issued (rotation).
-- ============================================================
CREATE TABLE auth_refresh_tokens
(
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,   -- SHA-256 of the raw refresh token
    issued_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    revoked_at  TIMESTAMPTZ
);

CREATE INDEX idx_auth_refresh_tokens_user    ON auth_refresh_tokens (user_id);
CREATE INDEX idx_auth_refresh_tokens_hash    ON auth_refresh_tokens (token_hash);
CREATE INDEX idx_auth_refresh_tokens_active  ON auth_refresh_tokens (user_id, revoked)
    WHERE revoked = FALSE;

-- ============================================================
-- auth_token_blacklist: JTI (JWT ID) entries for access tokens
-- revoked before their natural expiry (e.g. via logout).
-- Rows can be purged once expires_at is in the past.
-- ============================================================
CREATE TABLE auth_token_blacklist
(
    jti        VARCHAR(36)  PRIMARY KEY,   -- UUID stored in the JWT jti claim
    user_id    BIGINT       NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ  NOT NULL,      -- mirrors the JWT exp claim; used for cleanup
    revoked_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_auth_token_blacklist_expires ON auth_token_blacklist (expires_at);

