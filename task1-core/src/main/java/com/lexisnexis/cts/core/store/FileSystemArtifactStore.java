package com.lexisnexis.cts.core.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexisnexis.cts.core.model.ProcessingResult;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Filesystem-based implementation of ArtifactStore.
 *
 * <p>Stores processing results as JSON files in a directory structure keyed by content_id.
 * Uses a ConcurrentHashMap as an in-memory index for fast duplicate detection.</p>
 *
 * <p>Directory layout:
 * <pre>
 *   {output.path}/
 *     {content_id}/
 *       result.json        - full ProcessingResult
 *       normalized.json    - normalized document JSON (if published)
 *       plain_text.txt     - plain text for RAG (if published)
 * </pre>
 * </p>
 */
@Component
public class FileSystemArtifactStore implements ArtifactStore {

    private static final Logger log = LoggerFactory.getLogger(FileSystemArtifactStore.class);

    private static final String RESULT_FILE = "result.json";
    private static final String NORMALIZED_FILE = "normalized.json";
    private static final String PLAIN_TEXT_FILE = "plain_text.txt";

    private final ObjectMapper objectMapper;
    private final Path outputPath;

    /**
     * In-memory index: content_id -> content_hash for fast duplicate detection.
     * Rebuilt on startup by scanning existing artifacts.
     */
    private final ConcurrentMap<String, String> hashIndex = new ConcurrentHashMap<>();

    public FileSystemArtifactStore(
            ObjectMapper objectMapper,
            @Value("${cts.output.path:./output}") String outputPath) {
        this.outputPath = Path.of(outputPath);
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(outputPath);
            rebuildIndex();
            log.info("Artifact store initialized at: {} ({} existing artifacts)",
                    outputPath.toAbsolutePath(), hashIndex.size());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize artifact store at: " + outputPath, e);
        }
    }

    @Override
    public boolean store(ProcessingResult result) {
        String contentId = result.contentId();
        String contentHash = result.contentHash();

        // Atomic check-and-set using putIfAbsent to prevent TOCTOU race condition.
        // If a hash already exists for this content_id, check if it matches.
        String existingHash = hashIndex.putIfAbsent(contentId, contentHash);
        if (existingHash != null && existingHash.equals(contentHash)) {
            log.debug("Duplicate detected for content_id={}, hash={} -- skipping", contentId, contentHash);
            return false;
        }

        // If existingHash != null but different, this is an update -- overwrite
        if (existingHash != null) {
            hashIndex.put(contentId, contentHash);
        }

        try {
            Path artifactDir = outputPath.resolve(sanitizeFileName(contentId));
            Files.createDirectories(artifactDir);

            // Write the full processing result
            objectMapper.writeValue(artifactDir.resolve(RESULT_FILE).toFile(), result);

            // Write normalized JSON and plain text if document was published
            if (result.normalizedDocument() != null) {
                objectMapper.writeValue(artifactDir.resolve(NORMALIZED_FILE).toFile(),
                        result.normalizedDocument());
            }
            if (result.plainText() != null) {
                Files.writeString(artifactDir.resolve(PLAIN_TEXT_FILE), result.plainText());
            }

            log.info("Stored artifacts for content_id={} (status={})", contentId, result.status());
            return true;
        } catch (IOException e) {
            // Roll back the index entry on write failure
            hashIndex.remove(contentId, contentHash);
            log.error("Failed to store artifacts for content_id={}", contentId, e);
            throw new StoreException("Failed to write artifacts for: " + contentId, e);
        }
    }

    @Override
    public Optional<ProcessingResult> findByContentId(String contentId) {
        Path resultFile = outputPath.resolve(sanitizeFileName(contentId)).resolve(RESULT_FILE);
        if (!Files.exists(resultFile)) {
            return Optional.empty();
        }
        try {
            ProcessingResult result = objectMapper.readValue(resultFile.toFile(), ProcessingResult.class);
            return Optional.of(result);
        } catch (IOException e) {
            log.error("Failed to read artifact for content_id={}", contentId, e);
            return Optional.empty();
        }
    }

    @Override
    public boolean exists(String contentId, String contentHash) {
        String storedHash = hashIndex.get(contentId);
        return contentHash.equals(storedHash);
    }

    /**
     * Rebuilds the in-memory hash index from existing filesystem artifacts.
     */
    private void rebuildIndex() {
        try (var dirs = Files.list(outputPath)) {
            dirs.filter(Files::isDirectory).forEach(dir -> {
                Path resultFile = dir.resolve(RESULT_FILE);
                if (Files.exists(resultFile)) {
                    try {
                        ProcessingResult result = objectMapper.readValue(
                                resultFile.toFile(), ProcessingResult.class);
                        if (result.contentId() != null && result.contentHash() != null) {
                            hashIndex.put(result.contentId(), result.contentHash());
                        }
                    } catch (IOException e) {
                        log.warn("Failed to read artifact in {}, skipping index entry", dir, e);
                    }
                }
            });
        } catch (IOException e) {
            log.warn("Could not scan output directory for index rebuild: {}", e.getMessage());
        }
    }

    /**
     * Sanitizes a content_id for use as a filesystem directory name.
     * Replaces characters that are problematic on Windows/Linux filesystems.
     */
    private String sanitizeFileName(String contentId) {
        return contentId.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Exception thrown when the artifact store encounters an IO error.
     */
    public static class StoreException extends RuntimeException {
        public StoreException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
