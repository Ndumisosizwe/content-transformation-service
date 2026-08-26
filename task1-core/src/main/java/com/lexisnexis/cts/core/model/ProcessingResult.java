package com.lexisnexis.cts.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * The result of processing a single document through the pipeline.
 * Contains status, diagnostics (if validation failed), and output artifacts (if successful).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Result of processing a single document through the pipeline")
public record ProcessingResult(
        @JsonProperty("content_id")
        @Schema(description = "Stable document identifier extracted from XML", example = "FR-2024-CA-000123")
        String contentId,

        @JsonProperty("status")
        @Schema(description = "Final processing status", example = "PUBLISHED")
        DocumentStatus status,

        @JsonProperty("diagnostics")
        @Schema(description = "Validation/error diagnostics (present only on failure)")
        List<ValidationDiagnostic> diagnostics,

        @JsonProperty("content_hash")
        @Schema(description = "SHA-256 hash of raw XML content for idempotency", example = "a1b2c3d4e5f6...")
        String contentHash,

        @JsonProperty("processed_at")
        @Schema(description = "Timestamp when processing completed", example = "2024-03-12T10:30:00Z")
        Instant processedAt,

        @JsonProperty("normalized_json")
        @Schema(description = "Normalized JSON output (present only on success)")
        NormalizedDocument normalizedDocument,

        @JsonProperty("plain_text")
        @Schema(description = "Concatenated plain text for AI/RAG pipelines (present only on success)", example = "Le litige porte sur...")
        String plainText
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
