package com.lexisnexis.cts.core.service;

import com.lexisnexis.cts.core.model.DocumentStatus;
import com.lexisnexis.cts.core.model.ProcessingResult;
import com.lexisnexis.cts.core.store.FileSystemArtifactStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DocumentProcessingService.
 * Tests the full pipeline through all execution paths.
 */
class DocumentProcessingServiceTest {

    @TempDir
    Path tempDir;

    private DocumentProcessingService processingService;
    private FileSystemArtifactStore artifactStore;

    @BeforeEach
    void setUp() {
        XmlValidationService validationService = new XmlValidationService();
        validationService.init();

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        XsltTransformationService transformationService = new XsltTransformationService(objectMapper);
        transformationService.init();

        artifactStore = new FileSystemArtifactStore(objectMapper, tempDir.toString());
        artifactStore.init();

        ContentIdExtractor contentIdExtractor = new ContentIdExtractor();

        processingService = new DocumentProcessingService(
                validationService, transformationService, artifactStore, contentIdExtractor);
    }

    @Test
    @DisplayName("Process valid full judgment succeeds with PUBLISHED status")
    void processValidJudgment_succeeds() throws IOException {
        byte[] xml = loadSample("valid-judgment.xml");

        ProcessingResult result = processingService.process(xml);

        assertThat(result.status()).isEqualTo(DocumentStatus.PUBLISHED);
        assertThat(result.contentId()).isEqualTo("FR-2024-CA-000123");
        assertThat(result.normalizedDocument()).isNotNull();
        assertThat(result.normalizedDocument().contentId()).isEqualTo("FR-2024-CA-000123");
        assertThat(result.plainText()).isNotBlank();
        assertThat(result.contentHash()).isNotBlank();
        assertThat(result.processedAt()).isNotNull();
    }

    @Test
    @DisplayName("Process valid minimal judgment succeeds")
    void processMinimalJudgment_succeeds() throws IOException {
        byte[] xml = loadSample("valid-judgment-minimal.xml");

        ProcessingResult result = processingService.process(xml);

        assertThat(result.status()).isEqualTo(DocumentStatus.PUBLISHED);
        assertThat(result.contentId()).isEqualTo("FR-2024-TGI-000456");
        assertThat(result.normalizedDocument().paragraphs()).hasSize(1);
    }

    @Test
    @DisplayName("Process invalid XML (bad date) results in VALIDATION_FAILED")
    void processInvalidXml_validationFails() throws IOException {
        byte[] xml = loadSample("invalid-bad-date.xml");

        ProcessingResult result = processingService.process(xml);

        assertThat(result.status()).isEqualTo(DocumentStatus.VALIDATION_FAILED);
        assertThat(result.contentId()).isEqualTo("FR-2024-BAD-DATE");
        assertThat(result.diagnostics()).isNotEmpty();
        assertThat(result.normalizedDocument()).isNull();
        assertThat(result.plainText()).isNull();
    }

    @Test
    @DisplayName("Process same document twice results in DUPLICATE_SKIPPED on second call")
    void processDuplicate_skipped() throws IOException {
        byte[] xml = loadSample("valid-judgment.xml");

        ProcessingResult first = processingService.process(xml);
        ProcessingResult second = processingService.process(xml);

        assertThat(first.status()).isEqualTo(DocumentStatus.PUBLISHED);
        assertThat(second.status()).isEqualTo(DocumentStatus.DUPLICATE_SKIPPED);
        assertThat(second.contentId()).isEqualTo("FR-2024-CA-000123");
    }

    @Test
    @DisplayName("Process document with unextractable content_id results in VALIDATION_FAILED")
    void processNoContentId_validationFails() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <root xmlns="urn:other:namespace">
                    <data>No content_id in correct namespace</data>
                </root>
                """;

        ProcessingResult result = processingService.process(xml.getBytes(StandardCharsets.UTF_8));

        assertThat(result.status()).isEqualTo(DocumentStatus.VALIDATION_FAILED);
        assertThat(result.contentId()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("Process stores result in artifact store for later retrieval")
    void processedDocument_isStoredInArtifactStore() throws IOException {
        byte[] xml = loadSample("valid-judgment.xml");

        processingService.process(xml);

        assertThat(artifactStore.findByContentId("FR-2024-CA-000123")).isPresent();
    }

    @Test
    @DisplayName("Process validation failure is also stored in artifact store")
    void validationFailure_isStoredInArtifactStore() throws IOException {
        byte[] xml = loadSample("invalid-bad-date.xml");

        processingService.process(xml);

        assertThat(artifactStore.findByContentId("FR-2024-BAD-DATE")).isPresent();
        assertThat(artifactStore.findByContentId("FR-2024-BAD-DATE").get().status())
                .isEqualTo(DocumentStatus.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("Content hash is deterministic for same input")
    void contentHash_isDeterministic() throws IOException {
        byte[] xml = loadSample("valid-judgment.xml");

        ProcessingResult result1 = processingService.process(xml);

        // Reset the store to allow re-processing
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        artifactStore = new FileSystemArtifactStore(mapper, tempDir.resolve("second").toString());
        artifactStore.init();
        ContentIdExtractor extractor = new ContentIdExtractor();
        XmlValidationService validator = new XmlValidationService();
        validator.init();
        XsltTransformationService transformer = new XsltTransformationService(mapper);
        transformer.init();
        DocumentProcessingService service2 = new DocumentProcessingService(
                validator, transformer, artifactStore, extractor);

        ProcessingResult result2 = service2.process(xml);

        assertThat(result1.contentHash()).isEqualTo(result2.contentHash());
    }

    @Test
    @DisplayName("Different documents produce different content hashes")
    void differentDocuments_differentHashes() throws IOException {
        byte[] xml1 = loadSample("valid-judgment.xml");
        byte[] xml2 = loadSample("valid-judgment-minimal.xml");

        ProcessingResult result1 = processingService.process(xml1);
        ProcessingResult result2 = processingService.process(xml2);

        assertThat(result1.contentHash()).isNotEqualTo(result2.contentHash());
    }

    private byte[] loadSample(String filename) throws IOException {
        var stream = getClass().getClassLoader().getResourceAsStream("samples/" + filename);
        if (stream == null) {
            throw new IOException("Sample file not found: " + filename);
        }
        return stream.readAllBytes();
    }
}
