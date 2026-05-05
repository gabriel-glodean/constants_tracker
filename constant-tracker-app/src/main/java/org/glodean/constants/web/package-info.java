/**
 * WebFlux layer — controllers, CORS configuration, and the global exception handler.
 *
 * <ul>
 *   <li>{@link org.glodean.constants.web.WebCorsConfiguration} — registers a
 *       {@code CorsWebFilter} bean; allowed origins are driven by
 *       {@code constants.cors.allowed-origins} in {@code application.yaml}.</li>
 *   <li>{@link org.glodean.constants.web.GlobalExceptionHandler} — centralised
 *       {@code @RestControllerAdvice} that maps application exceptions to HTTP
 *       status codes and a consistent JSON error body.</li>
 * </ul>
 *
 * <p>HTTP endpoint controllers live in the
 * {@link org.glodean.constants.web.endpoints} sub-package.
 */
package org.glodean.constants.web;
