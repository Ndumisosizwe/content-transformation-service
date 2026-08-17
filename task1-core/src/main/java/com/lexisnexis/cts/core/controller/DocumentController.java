package com.lexisnexis.cts.core.controller;

import com.lexisnexis.cts.core.model.DocumentStatus;
import com.lexisnexis.cts.core.model.ProcessingResult;
import com.lexisnexis.cts.core.service.DocumentProcessingService;
import com.lexisnexis.cts.core.store.ArtifactStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for document ingestion and retrieval.
 */
@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "Documents", description = "Single document ingestion and retrieval")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    private final DocumentProcessingService processingService;
    private final ArtifactStore artifactStore;

    public DocumentController(DocumentProcessingService processingService,
                              ArtifactStore artifactStore) {
        this.processingService = processingService;
        this.artifactStore = artifactStore;
    }

    @Operation(summary = "Submit a single XML document",
            description = "Validates against XSD, transforms to JSON via XSLT, and publishes artifacts keyed by content_id",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Document published successfully"),
                    @ApiResponse(responseCode = "200", description = "Duplicate detected — already published"),
                    @ApiResponse(responseCode = "400", description = "Empty body or unreadable request"),
                    @ApiResponse(responseCode = "422", description = "XML validation failed"),
                    @ApiResponse(responseCode = "500", description = "Transformation or storage failure")
            })
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

    @Operation(summary = "Retrieve document status and outputs",
            description = "Returns the processing result for a previously submitted document",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Document found"),
                    @ApiResponse(responseCode = "404", description = "Content ID not found")
            })
    @GetMapping(value = "/{contentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProcessingResult> getDocument(
            @Parameter(description = "The stable document identifier (content_id)")
            @PathVariable String contentId) {
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
