package com.lexisnexis.cts.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * The result of processing a single document through the pipeline.
 * Contains status, diagnostics (if validation failed), and output artifacts (if successful).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProcessingResult(
        @JsonProperty("content_id") String contentId,
        @JsonProperty("status") DocumentStatus status,
        @JsonProperty("diagnostics") List<ValidationDiagnostic> diagnostics,
        @JsonProperty("content_hash") String contentHash,
        @JsonProperty("processed_at") Instant processedAt,
        @JsonProperty("normalized_json") NormalizedDocument normalizedDocument,
        @JsonProperty("plain_text") String plainText
) {

    /**
     * Factory method for a successful processing result.
     */
    public static ProcessingResult success(String contentId, String contentHash,
                                           NormalizedDocument normalizedDocument, String plainText) {
        return new ProcessingResult(contentId, DocumentStatus.PUBLISHED, null,
                contentHash, Instant.now(), normalizedDocument, plainText);
    }

    /**
     * Factory method for a validation failure result.
     */
    public static ProcessingResult validationFailed(String contentId, String contentHash,
                                                    List<ValidationDiagnostic> diagnostics) {
        return new ProcessingResult(contentId, DocumentStatus.VALIDATION_FAILED, diagnostics,
                contentHash, Instant.now(), null, null);
    }

    /**
     * Factory method for a transformation failure result.
     */
    public static ProcessingResult transformationFailed(String contentId, String contentHash,
                                                        String errorMessage) {
        var diagnostic = new ValidationDiagnostic(0, 0, "ERROR", errorMessage);
        return new ProcessingResult(contentId, DocumentStatus.TRANSFORMATION_FAILED,
                List.of(diagnostic), contentHash, Instant.now(), null, null);
    }

    /**
     * Factory method for a duplicate (skipped) result.
     */
    public static ProcessingResult duplicateSkipped(String contentId, String contentHash) {
        return new ProcessingResult(contentId, DocumentStatus.DUPLICATE_SKIPPED, null,
                contentHash, Instant.now(), null, null);
    }

    /**
     * Factory method for a rejected document (IO error, capacity exceeded, unreadable).
     */
    public static ProcessingResult rejected(String contentId, String reason) {
        var diagnostic = new ValidationDiagnostic(0, 0, "ERROR", reason);
        return new ProcessingResult(contentId, DocumentStatus.REJECTED,
                List.of(diagnostic), null, Instant.now(), null, null);
    }
}
