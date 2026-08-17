package com.lexisnexis.cts.core.model;

/**
 * Shared constants for document processing.
 * Avoids scattering magic strings across the codebase.
 */
public final class ProcessingConstants {

    private ProcessingConstants() {
        // utility class
    }

    /** Fallback content_id when extraction fails or is not attempted. */
    public static final String UNKNOWN_CONTENT_ID = "UNKNOWN";

    /** Fallback content hash when hashing is not applicable. */
    public static final String NO_HASH = "N/A";
}
