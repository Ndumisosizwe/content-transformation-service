package com.lexisnexis.cts.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single validation error or warning encountered during XSD validation.
 */
public record ValidationDiagnostic(
        @JsonProperty("line") int line,
        @JsonProperty("column") int column,
        @JsonProperty("severity") String severity,
        @JsonProperty("message") String message
) {
}
