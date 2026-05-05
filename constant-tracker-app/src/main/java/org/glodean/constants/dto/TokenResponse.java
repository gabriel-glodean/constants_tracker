package org.glodean.constants.dto;

/**
 * Returned by the login, token-renewal, and token-refresh endpoints.
 *
 * @param accessToken  the signed JWT to include as {@code Authorization: Bearer <token>}
 * @param refreshToken opaque token used to obtain a new access token via {@code /auth/refresh};
 *                     always non-null — every issuance path (login, refresh, renew) now rotates
 *                     the refresh token
 * @param expiresInMs  access-token lifetime in milliseconds, mirroring
 *                     {@code constants.auth.jwt.expiration-ms}
 */
public record TokenResponse(String accessToken, String refreshToken, long expiresInMs) {}
