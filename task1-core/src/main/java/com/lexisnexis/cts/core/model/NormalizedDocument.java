package com.lexisnexis.cts.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The normalized JSON representation of a legal document after transformation.
 * This is the primary output artifact of the pipeline.
 */
public record NormalizedDocument(
        @JsonProperty("content_id") String contentId,
        @JsonProperty("title") String title,
        @JsonProperty("court") String court,
        @JsonProperty("jurisdiction") String jurisdiction,
        @JsonProperty("decision_date") String decisionDate,
        @JsonProperty("citations") List<Citation> citations,
        @JsonProperty("parties") List<Party> parties,
        @JsonProperty("paragraphs") List<Paragraph> paragraphs,
        @JsonProperty("full_text") String fullText
) {
}
