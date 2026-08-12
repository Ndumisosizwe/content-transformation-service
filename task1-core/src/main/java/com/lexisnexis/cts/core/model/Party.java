package com.lexisnexis.cts.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A party involved in the legal case.
 */
public record Party(
        @JsonProperty("role") String role,
        @JsonProperty("name") String name
) {
}
