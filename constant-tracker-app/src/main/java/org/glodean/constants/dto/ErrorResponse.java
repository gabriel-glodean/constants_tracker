package org.glodean.constants.dto;

/**
 * Uniform error body returned by {@link org.glodean.constants.web.GlobalExceptionHandler}.
 *
 * @param status  the HTTP status code (mirrors the response status for easy client parsing)
 * @param error   a short, machine-readable error label (e.g. {@code "Unauthorized"})
 * @param message a human-readable description of what went wrong
 */
public record ErrorResponse(int status, String error, String message) {}
