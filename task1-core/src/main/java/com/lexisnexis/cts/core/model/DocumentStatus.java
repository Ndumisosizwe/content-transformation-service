package com.lexisnexis.cts.core.model;

/**
 * Represents the processing status of a document in the pipeline.
 */
public enum DocumentStatus {
    RECEIVED,
    VALIDATING,
    VALIDATION_FAILED,
    TRANSFORMING,
    TRANSFORMATION_FAILED,
    PUBLISHED,
    DUPLICATE_SKIPPED,
    /** Document was rejected before processing (IO error, capacity exceeded, unreadable). */
    REJECTED
}
