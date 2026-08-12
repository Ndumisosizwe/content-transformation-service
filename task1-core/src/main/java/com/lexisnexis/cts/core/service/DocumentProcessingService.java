package com.lexisnexis.cts.core.service;

import com.lexisnexis.cts.core.model.NormalizedDocument;
import com.lexisnexis.cts.core.model.ProcessingResult;
import com.lexisnexis.cts.core.model.ValidationDiagnostic;
import com.lexisnexis.cts.core.store.ArtifactStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Orchestrates the full document processing pipeline:
 * Ingest → Validate → Transform → Publish.
 *
 * <p>This is the main entry point for processing a single XML document.</p>
 */
@Service
public class DocumentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);

    private final XmlValidationService validationService;
    private final XsltTransformationService transformationService;
    private final ArtifactStore artifactStore;
    private final ContentIdExtractor contentIdExtractor;

    public DocumentProcessingService(XmlValidationService validationService,
                                     XsltTransformationService transformationService,
                                     ArtifactStore artifactStore,
                                     ContentIdExtractor contentIdExtractor) {
        this.validationService = validationService;
        this.transformationService = transformationService;
        this.artifactStore = artifactStore;
        this.contentIdExtractor = contentIdExtractor;
    }

    /**
     * Processes a single XML document through the full pipeline.
     *
     * @param xmlContent the raw XML document bytes
     * @return the processing result (success, validation failure, transformation failure, or duplicate)
     */
    public ProcessingResult process(byte[] xmlContent) {
        // Compute content hash for idempotency
        String contentHash = computeHash(xmlContent);

        // Extract content_id from the XML for keying
        String contentId = contentIdExtractor.extract(xmlContent);
        if (contentId == null || contentId.isBlank()) {
            log.warn("Could not extract content_id from document");
            return ProcessingResult.validationFailed("UNKNOWN", contentHash,
                    List.of(new ValidationDiagnostic(0, 0, "ERROR",
                            "Could not extract content_id from document")));
        }

        log.info("Processing document content_id={}", contentId);

        // Check for duplicate
        if (artifactStore.exists(contentId, contentHash)) {
            log.info("Duplicate detected for content_id={}, skipping", contentId);
            return ProcessingResult.duplicateSkipped(contentId, contentHash);
        }

        // Step 1: Validate
        List<ValidationDiagnostic> diagnostics = validationService.validate(xmlContent);
        if (!diagnostics.isEmpty()) {
            log.warn("Validation failed for content_id={} with {} issue(s)", contentId, diagnostics.size());
            ProcessingResult result = ProcessingResult.validationFailed(contentId, contentHash, diagnostics);
            artifactStore.store(result);
            return result;
        }

        // Step 2: Transform
        NormalizedDocument normalizedDocument;
        String plainText;
        try {
            normalizedDocument = transformationService.transform(xmlContent);
            plainText = transformationService.extractPlainText(normalizedDocument);
        } catch (XsltTransformationService.TransformationException e) {
            log.error("Transformation failed for content_id={}: {}", contentId, e.getMessage());
            ProcessingResult result = ProcessingResult.transformationFailed(contentId, contentHash, e.getMessage());
            artifactStore.store(result);
            return result;
        }

        // Step 3: Publish
        ProcessingResult result = ProcessingResult.success(contentId, contentHash, normalizedDocument, plainText);
        artifactStore.store(result);

        log.info("Successfully processed and published content_id={}", contentId);
        return result;
    }

    /**
     * Computes SHA-256 hash of the XML content for idempotency checks.
     */
    private String computeHash(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
