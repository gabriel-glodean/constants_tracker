package org.glodean.constants.dto;

/**
 * Returned by {@code GET /auth/status} so that clients can discover whether
 * authentication is currently enforced before attempting any protected call.
 *
 * @param enabled {@code true} when JWT authentication is active
 */
public record AuthStatusResponse(boolean enabled) {}
