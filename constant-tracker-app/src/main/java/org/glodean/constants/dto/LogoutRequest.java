package org.glodean.constants.dto;

/**
 * Optional request body for {@code POST /auth/logout}.
 *
 * <p>When present, the provided refresh token is revoked in addition to the access token being
 * blacklisted. Callers that do not hold a refresh token (e.g. the legacy
 * {@code /auth/token/renew} flow) may omit this body.
 *
 * @param refreshToken the opaque refresh token to revoke; may be {@code null}
 */
public record LogoutRequest(String refreshToken) {}
