package org.glodean.constants.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /auth/login}.
 *
 * @param username the principal identifier — must not be blank
 * @param password the plain-text credential (must be transmitted over HTTPS) — must not be blank
 */
public record LoginRequest(
    @NotBlank(message = "username must not be blank") String username,
    @NotBlank(message = "password must not be blank") String password) {}
