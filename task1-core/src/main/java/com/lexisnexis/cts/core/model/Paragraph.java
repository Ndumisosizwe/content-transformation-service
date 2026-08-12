package com.lexisnexis.cts.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A paragraph from the document body, tagged with its section type.
 */
public record Paragraph(
        @JsonProperty("id") String id,
        @JsonProperty("section") String section,
        @JsonProperty("text") String text
) {
}
