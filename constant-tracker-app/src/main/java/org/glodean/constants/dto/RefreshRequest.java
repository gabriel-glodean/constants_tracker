package org.glodean.constants.dto;

/**
 * Request body for {@code POST /auth/refresh}.
 *
 * @param refreshToken the opaque refresh token issued at login time
 */
public record RefreshRequest(String refreshToken) {}
