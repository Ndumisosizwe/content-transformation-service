package com.lexisnexis.cts.core.store;

import com.lexisnexis.cts.core.model.ProcessingResult;

import java.util.Optional;

/**
 * Interface for storing and retrieving document processing artifacts.
 * Implementations must be keyed by content_id and handle idempotency
 * (same content_id + same content hash = no duplicate storage).
 */
public interface ArtifactStore {

    /**
     * Stores a processing result. If an artifact with the same content_id
     * and content_hash already exists, the store should skip the write
     * and return false (duplicate).
     *
     * @param result the processing result to store
     * @return true if stored (new or updated), false if duplicate (skipped)
     */
    boolean store(ProcessingResult result);

    /**
     * Retrieves the processing result for a given content_id.
     *
     * @param contentId the stable document identifier
     * @return the stored result, or empty if not found
     */
    Optional<ProcessingResult> findByContentId(String contentId);

    /**
     * Checks whether a document with the given content_id and content_hash
     * already exists in the store.
     *
     * @param contentId   the stable document identifier
     * @param contentHash the SHA-256 hash of the document content
     * @return true if an identical artifact already exists
     */
    boolean exists(String contentId, String contentHash);
}
