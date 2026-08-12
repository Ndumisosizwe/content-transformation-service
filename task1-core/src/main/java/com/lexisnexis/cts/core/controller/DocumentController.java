package com.lexisnexis.cts.core.controller;

import com.lexisnexis.cts.core.model.DocumentStatus;
import com.lexisnexis.cts.core.model.ProcessingResult;
import com.lexisnexis.cts.core.service.DocumentProcessingService;
import com.lexisnexis.cts.core.store.ArtifactStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for document ingestion and retrieval.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/v1/documents — Submit a single XML document for processing</li>
 *   <li>GET /api/v1/documents/{contentId} — Retrieve processing status and outputs</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    private final DocumentProcessingService processingService;
    private final ArtifactStore artifactStore;

    public DocumentController(DocumentProcessingService processingService,
                              ArtifactStore artifactStore) {
        this.processingService = processingService;
        this.artifactStore = artifactStore;
    }

    /**
     * Submit a single XML document for processing.
     * The document is validated, transformed, and published synchronously.
     *
     * @param xmlContent the raw XML document body
     * @return the processing result with appropriate HTTP status
     */
    @PostMapping(consumes = MediaType.APPLICATION_XML_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProcessingResult> submitDocument(@RequestBody byte[] xmlContent) {
        if (xmlContent == null || xmlContent.length == 0) {
            return ResponseEntity.badRequest().build();
        }

        log.info("Received document submission ({} bytes)", xmlContent.length);

        ProcessingResult result = processingService.process(xmlContent);

        HttpStatus status = mapStatusToHttp(result.status());
        return ResponseEntity.status(status).body(result);
    }

    /**
     * Retrieve the processing status and outputs for a given content_id.
     *
     * @param contentId the stable document identifier
     * @return the stored processing result, or 404 if not found
     */
    @GetMapping(value = "/{contentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProcessingResult> getDocument(@PathVariable String contentId) {
        return artifactStore.findByContentId(contentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Maps document processing status to an appropriate HTTP status code.
     */
    private HttpStatus mapStatusToHttp(DocumentStatus status) {
        return switch (status) {
            case PUBLISHED -> HttpStatus.CREATED;
            case DUPLICATE_SKIPPED -> HttpStatus.OK;
            case VALIDATION_FAILED -> HttpStatus.UNPROCESSABLE_ENTITY;
            case TRANSFORMATION_FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.ACCEPTED;
        };
    }
}
