package com.lexisnexis.cts.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A citation reference associated with a legal document.
 */
public record Citation(
        @JsonProperty("type") String type,
        @JsonProperty("value") String value
) {
}
