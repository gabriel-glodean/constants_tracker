package org.glodean.constants.web;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glodean.constants.dto.ErrorResponse;
import org.glodean.constants.extractor.ModelExtractor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

/**
 * Centralised exception-to-HTTP mapping for all WebFlux controllers.
 *
 * <p>Every handler logs at an appropriate level and returns a consistent {@link ErrorResponse}
 * body. Controllers should let exceptions propagate rather than handling them locally.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LogManager.getLogger(GlobalExceptionHandler.class);

  /** 401 — wrong username / password. Message is intentionally vague to avoid enumeration. */
  @ExceptionHandler(BadCredentialsException.class)
  public Mono<ResponseEntity<ErrorResponse>> handleBadCredentials(BadCredentialsException ex) {
    log.atWarn().log("Authentication failure: {}", ex.getMessage());
    return Mono.just(ResponseEntity
        .status(HttpStatus.UNAUTHORIZED)
        .body(new ErrorResponse(401, "Unauthorized", "Invalid credentials")));
  }

  /** 400 — request body failed Bean Validation (e.g. blank username/password on login). */
  @ExceptionHandler(WebExchangeBindException.class)
  public Mono<ResponseEntity<ErrorResponse>> handleValidation(WebExchangeBindException ex) {
    String details = ex.getBindingResult().getFieldErrors().stream()
        .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
        .collect(Collectors.joining("; "));
    log.atWarn().log("Validation failed: {}", details);
    return Mono.just(ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse(400, "Bad Request", details)));
  }

  /** 422 — uploaded file/bytecode could not be parsed or extracted. */
  @ExceptionHandler(ModelExtractor.ExtractionException.class)
  public Mono<ResponseEntity<ErrorResponse>> handleExtraction(ModelExtractor.ExtractionException ex) {
    log.atWarn().log("Extraction failed: {}", ex.getMessage());
    return Mono.just(ResponseEntity
        .status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(new ErrorResponse(422, "Unprocessable Entity", ex.getMessage())));
  }

  /** 409 — operation conflicts with current state (e.g. version already finalized). */
  @ExceptionHandler(IllegalStateException.class)
  public Mono<ResponseEntity<ErrorResponse>> handleIllegalState(IllegalStateException ex) {
    log.atWarn().log("Conflict: {}", ex.getMessage());
    return Mono.just(ResponseEntity
        .status(HttpStatus.CONFLICT)
        .body(new ErrorResponse(409, "Conflict", ex.getMessage())));
  }

  /** 400 — caller supplied a malformed or logically invalid argument. */
  @ExceptionHandler(IllegalArgumentException.class)
  public Mono<ResponseEntity<ErrorResponse>> handleIllegalArgument(IllegalArgumentException ex) {
    log.atWarn().log("Bad request: {}", ex.getMessage());
    return Mono.just(ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse(400, "Bad Request", ex.getMessage())));
  }

  /** Forward {@link ResponseStatusException} using its own embedded HTTP status code. */
  @ExceptionHandler(ResponseStatusException.class)
  public Mono<ResponseEntity<ErrorResponse>> handleResponseStatus(ResponseStatusException ex) {
    HttpStatusCode status = ex.getStatusCode();
    log.atWarn().log("Response status exception: {} {}", status.value(), ex.getReason());
    return Mono.just(ResponseEntity
        .status(status)
        .body(new ErrorResponse(status.value(), ex.getReason(), ex.getMessage())));
  }

  /** 500 — catch-all for any unhandled exception. */
  @ExceptionHandler(Exception.class)
  public Mono<ResponseEntity<ErrorResponse>> handleGeneric(Exception ex) {
    log.atError().withThrowable(ex).log("Unhandled exception: {}", ex.getMessage());
    return Mono.just(ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorResponse(500, "Internal Server Error", "An unexpected error occurred")));
  }
}
