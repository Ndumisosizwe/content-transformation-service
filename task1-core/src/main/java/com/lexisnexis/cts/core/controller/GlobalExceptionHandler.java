package com.lexisnexis.cts.core.controller;

import com.lexisnexis.cts.core.service.XsltTransformationService;
import com.lexisnexis.cts.core.store.FileSystemArtifactStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Global exception handler for the REST API.
 * Translates exceptions into structured error responses with appropriate HTTP status codes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        log.warn("Unsupported media type: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(new ErrorResponse(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
                        "UNSUPPORTED_MEDIA_TYPE",
                        "Content-Type must be application/xml",
                        Instant.now()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Request body not readable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        "BAD_REQUEST",
                        "Request body is missing or malformed",
                        Instant.now()));
    }

    @ExceptionHandler(XsltTransformationService.TransformationException.class)
    public ResponseEntity<ErrorResponse> handleTransformationException(
            XsltTransformationService.TransformationException ex) {
        log.error("Unhandled transformation error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "TRANSFORMATION_ERROR",
                        "An internal error occurred during document transformation",
                        Instant.now()));
    }

    @ExceptionHandler(FileSystemArtifactStore.StoreException.class)
    public ResponseEntity<ErrorResponse> handleStoreException(FileSystemArtifactStore.StoreException ex) {
        log.error("Artifact store error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "STORE_ERROR",
                        "An internal error occurred while storing artifacts",
                        Instant.now()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "INTERNAL_ERROR",
                        "An unexpected error occurred",
                        Instant.now()));
    }

    /**
     * Structured error response returned by the API.
     */
    public record ErrorResponse(
            int status,
            String error,
            String message,
            Instant timestamp
    ) {
    }
}
