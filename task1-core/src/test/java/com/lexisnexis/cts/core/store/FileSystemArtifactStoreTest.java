package com.lexisnexis.cts.core.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lexisnexis.cts.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for FileSystemArtifactStore.
 * Uses @TempDir for isolated filesystem operations.
 */
class FileSystemArtifactStoreTest {

    @TempDir
    Path tempDir;

    private FileSystemArtifactStore store;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = createObjectMapper();
        store = new FileSystemArtifactStore(objectMapper, tempDir.toString());
        store.init();
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }

    @Test
    @DisplayName("Store a successful processing result writes files and returns true")
    void store_successfulResult_returnsTrue() {
        ProcessingResult result = createSuccessResult("DOC-001", "hash-001");

        boolean stored = store.store(result);

        assertThat(stored).isTrue();
    }

    @Test
    @DisplayName("Stored result is retrievable by content_id")
    void findByContentId_afterStore_returnsResult() {
        ProcessingResult result = createSuccessResult("DOC-002", "hash-002");
        store.store(result);

        Optional<ProcessingResult> found = store.findByContentId("DOC-002");

        assertThat(found).isPresent();
        assertThat(found.get().contentId()).isEqualTo("DOC-002");
        assertThat(found.get().status()).isEqualTo(DocumentStatus.PUBLISHED);
    }

    @Test
    @DisplayName("findByContentId returns empty for unknown content_id")
    void findByContentId_unknown_returnsEmpty() {
        Optional<ProcessingResult> found = store.findByContentId("NONEXISTENT");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Duplicate with same content_id and hash returns false (skipped)")
    void store_duplicate_returnsFalse() {
        ProcessingResult result = createSuccessResult("DOC-003", "hash-003");
        store.store(result);

        boolean stored = store.store(result);

        assertThat(stored).isFalse();
    }

    @Test
    @DisplayName("Same content_id with different hash overwrites (returns true)")
    void store_sameIdDifferentHash_returnsTrue() {
        ProcessingResult result1 = createSuccessResult("DOC-004", "hash-004a");
        ProcessingResult result2 = createSuccessResult("DOC-004", "hash-004b");

        store.store(result1);
        boolean stored = store.store(result2);

        assertThat(stored).isTrue();
    }

    @Test
    @DisplayName("exists returns true for stored content_id with matching hash")
    void exists_matchingHash_returnsTrue() {
        ProcessingResult result = createSuccessResult("DOC-005", "hash-005");
        store.store(result);

        assertThat(store.exists("DOC-005", "hash-005")).isTrue();
    }

    @Test
    @DisplayName("exists returns false for stored content_id with different hash")
    void exists_differentHash_returnsFalse() {
        ProcessingResult result = createSuccessResult("DOC-006", "hash-006");
        store.store(result);

        assertThat(store.exists("DOC-006", "different-hash")).isFalse();
    }

    @Test
    @DisplayName("exists returns false for unknown content_id")
    void exists_unknownContentId_returnsFalse() {
        assertThat(store.exists("UNKNOWN", "any-hash")).isFalse();
    }

    @Test
    @DisplayName("Store validation failure result (no normalized doc or plain text)")
    void store_validationFailure_storesResult() {
        ProcessingResult result = ProcessingResult.validationFailed(
                "DOC-007", "hash-007",
                List.of(new ValidationDiagnostic(5, 10, "ERROR", "Bad date format")));
        store.store(result);

        Optional<ProcessingResult> found = store.findByContentId("DOC-007");

        assertThat(found).isPresent();
        assertThat(found.get().status()).isEqualTo(DocumentStatus.VALIDATION_FAILED);
        assertThat(found.get().diagnostics()).hasSize(1);
        assertThat(found.get().normalizedDocument()).isNull();
    }

    @Test
    @DisplayName("Index is rebuilt on init from existing files")
    void indexRebuild_afterRestart_findsExistingArtifacts() {
        // Store a result
        ProcessingResult result = createSuccessResult("DOC-008", "hash-008");
        store.store(result);

        // Create a new store instance pointing to the same directory (simulates restart)
        FileSystemArtifactStore newStore = new FileSystemArtifactStore(createObjectMapper(), tempDir.toString());
        newStore.init();

        // Should find the previously stored result via rebuilt index
        assertThat(newStore.exists("DOC-008", "hash-008")).isTrue();
        Optional<ProcessingResult> found = newStore.findByContentId("DOC-008");
        assertThat(found).isPresent();
    }

    @Test
    @DisplayName("Content IDs with special characters are sanitized for filesystem")
    void store_specialCharsInContentId_sanitized() {
        ProcessingResult result = createSuccessResult("FR/2024:CA#000", "hash-special");

        boolean stored = store.store(result);

        assertThat(stored).isTrue();
        Optional<ProcessingResult> found = store.findByContentId("FR/2024:CA#000");
        assertThat(found).isPresent();
    }

    private ProcessingResult createSuccessResult(String contentId, String hash) {
        NormalizedDocument doc = new NormalizedDocument(
                contentId, "Test Title", "Test Court", "FR", "2024-01-01",
                List.of(new Citation("ECLI", "ECLI:FR:TEST")),
                List.of(new Party("appellant", "Test Party")),
                List.of(new Paragraph("p1", "facts", "Test paragraph text.")),
                "Test paragraph text."
        );
        return ProcessingResult.success(contentId, hash, doc, "Test paragraph text.");
    }
}
