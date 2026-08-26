package com.lexisnexis.cts.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A single validation error or warning encountered during XSD validation.
 */
@Schema(description = "Diagnostic detail for a validation or processing error")
public record ValidationDiagnostic(
        @JsonProperty("line")
        @Schema(description = "Line number where the error occurred", example = "7")
        int line,

        @JsonProperty("column")
        @Schema(description = "Column number where the error occurred", example = "45")
        int column,

        @JsonProperty("severity")
        @Schema(description = "Severity level: ERROR, WARNING, or FATAL", example = "ERROR")
        String severity,

        @JsonProperty("message")
        @Schema(description = "Human-readable error message", example = "Value 'not-a-date' is not a valid xs:date")
        String message
) {}
