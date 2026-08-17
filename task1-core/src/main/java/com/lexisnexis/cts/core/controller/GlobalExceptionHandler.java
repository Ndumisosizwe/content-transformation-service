package com.lexisnexis.cts.core.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.time.Instant;

/**
 * Global exception handler for the REST API.
 * Translates exceptions into structured error responses with appropriate HTTP status codes.
 *
 * <p>Handles exceptions at the general level (not tied to concrete implementations)
 * so that swapping ArtifactStore or transformation implementations doesn't break error handling.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        log.warn("Unsupported media type: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_MEDIA_TYPE",
                "Content-Type must be application/xml");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Request body not readable: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST,
                "BAD_REQUEST",
                "Request body is missing or malformed");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        log.warn("Missing request parameter: {}", ex.getParameterName());
        return buildResponse(HttpStatus.BAD_REQUEST,
                "MISSING_PARAMETER",
                "Required parameter '" + ex.getParameterName() + "' is missing");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("Upload size exceeded: {}", ex.getMessage());
        return buildResponse(HttpStatus.PAYLOAD_TOO_LARGE,
                "PAYLOAD_TOO_LARGE",
                "Uploaded file(s) exceed the maximum allowed size");
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipartException(MultipartException ex) {
        log.warn("Multipart request error: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST,
                "MULTIPART_ERROR",
                "Invalid multipart request: " + getSafeMessage(ex));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        log.error("Illegal state: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Service is in an unexpected state");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Invalid argument: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST,
                "INVALID_ARGUMENT",
                getSafeMessage(ex));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex) {
        log.error("Runtime error: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An internal error occurred during processing");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred");
    }

    /**
     * Builds a structured error response with null safety.
     */
    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(
                        status.value(),
                        error != null ? error : "UNKNOWN_ERROR",
                        message != null ? message : "No details available",
                        Instant.now()));
    }

    /**
     * Safely extracts a message from an exception, guarding against null getMessage().
     */
    private String getSafeMessage(Exception ex) {
        if (ex == null) {
            return "Unknown error";
        }
        String msg = ex.getMessage();
        return msg != null ? msg : ex.getClass().getSimpleName();
    }

    /**
     * Structured error response returned by the API.
     */
    public record ErrorResponse(
            int status,
            String error,
            String message,
            Instant timestamp
    ) {}
}
