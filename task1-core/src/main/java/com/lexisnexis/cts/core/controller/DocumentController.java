package com.lexisnexis.cts.core.controller;

import com.lexisnexis.cts.core.config.CtsProperties;
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
import org.springframework.util.unit.DataSize;
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
    private final long maxFileSizeBytes;

    public DocumentController(DocumentProcessingService processingService,
                              ArtifactStore artifactStore,
                              CtsProperties ctsProperties) {
        this.processingService = processingService;
        this.artifactStore = artifactStore;
        this.maxFileSizeBytes = DataSize.parse(ctsProperties.processing().maxFileSize()).toBytes();
    }

    @Operation(summary = "Submit a single XML document",
            description = """
                    Validates against XSD, transforms to JSON via XSLT, and publishes artifacts keyed by content_id.
                    
                    **Example curl:**
                    ```
                    curl -X POST http://localhost:8080/api/v1/documents \\
                      -H "Content-Type: application/xml" \\
                      -d @samples/valid-judgment.xml
                    ```
                    """,
            responses = {
                    @ApiResponse(
                            responseCode = "201",
                            description = "Document published successfully",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ProcessingResult.class),
                                    examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                                            name = "Published",
                                            summary = "Successful transformation",
                                            value = """
                                                    {
                                                      "content_id": "FR-2024-CA-000123",
                                                      "status": "PUBLISHED",
                                                      "content_hash": "a1b2c3d4e5f6...",
                                                      "processed_at": "2024-03-12T10:30:00Z",
                                                      "normalized_json": {
                                                        "content_id": "FR-2024-CA-000123",
                                                        "title": "Cour d'appel de Paris, 12 mars 2024",
                                                        "court": "Cour d'appel de Paris",
                                                        "jurisdiction": "FR",
                                                        "decision_date": "2024-03-12"
                                                      },
                                                      "plain_text": "Le litige porte sur..."
                                                    }
                                                    """
                                    )
                            )
                    ),
                    @ApiResponse(responseCode = "200", description = "Duplicate detected -- already published"),
                    @ApiResponse(responseCode = "400", description = "Empty body or unreadable request"),
                    @ApiResponse(responseCode = "413", description = "Document exceeds maximum allowed size"),
                    @ApiResponse(
                            responseCode = "422",
                            description = "XML validation failed",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                                            name = "Validation failed",
                                            summary = "Invalid date in document",
                                            value = """
                                                    {
                                                      "content_id": "FR-2024-BAD-DATE",
                                                      "status": "VALIDATION_FAILED",
                                                      "content_hash": "c3d4e5f6...",
                                                      "processed_at": "2024-03-12T10:30:00Z",
                                                      "diagnostics": [
                                                        {
                                                          "line": 7,
                                                          "column": 45,
                                                          "severity": "ERROR",
                                                          "message": "Value 'not-a-date' is not a valid xs:date"
                                                        }
                                                      ]
                                                    }
                                                    """
                                    )
                            )
                    ),
                    @ApiResponse(responseCode = "500", description = "Transformation or storage failure")
            })
    @PostMapping(consumes = MediaType.APPLICATION_XML_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProcessingResult> submitDocument(@RequestBody byte[] xmlContent) {
        if (xmlContent == null || xmlContent.length == 0) {
            return ResponseEntity.badRequest().build();
        }

        if (xmlContent.length > maxFileSizeBytes) {
            log.warn("Document rejected: size {} bytes exceeds max {} bytes", xmlContent.length, maxFileSizeBytes);
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }

        log.info("Received document submission ({} bytes)", xmlContent.length);

        ProcessingResult result = processingService.process(xmlContent);

        HttpStatus status = mapStatusToHttp(result.status());
        return ResponseEntity.status(status).body(result);
    }

    @Operation(summary = "Retrieve document status and outputs",
            description = """
                    Returns the full processing result for a previously submitted document, \
                    including status, normalized JSON, plain text, and any diagnostics.
                    
                    **Example:** `GET /api/v1/documents/FR-2024-CA-000123`
                    """,
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Document found",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ProcessingResult.class),
                                    examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                                            name = "Published document",
                                            summary = "Successfully processed judgment",
                                            value = """
                                                    {
                                                      "content_id": "FR-2024-CA-000123",
                                                      "status": "PUBLISHED",
                                                      "content_hash": "a1b2c3d4e5f67890abcdef1234567890abcdef1234567890abcdef1234567890",
                                                      "processed_at": "2024-03-12T10:30:00Z",
                                                      "normalized_json": {
                                                        "content_id": "FR-2024-CA-000123",
                                                        "title": "Cour d'appel de Paris, 12 mars 2024, n 20/01234",
                                                        "court": "Cour d'appel de Paris",
                                                        "jurisdiction": "FR",
                                                        "decision_date": "2024-03-12",
                                                        "citations": [
                                                          {"type": "ECLI", "value": "ECLI:FR:CA12345"}
                                                        ],
                                                        "parties": [
                                                          {"role": "appellant", "name": "Societe ABC"},
                                                          {"role": "respondent", "name": "M. Dupont"}
                                                        ],
                                                        "paragraphs": [
                                                          {"id": "p1", "section": "facts", "text": "Le litige porte sur..."},
                                                          {"id": "p2", "section": "reasons", "text": "Considerant que..."},
                                                          {"id": "p3", "section": "disposition", "text": "Par ces motifs..."}
                                                        ],
                                                        "full_text": "Le litige porte sur... Considerant que... Par ces motifs..."
                                                      },
                                                      "plain_text": "Le litige porte sur... Considerant que... Par ces motifs..."
                                                    }
                                                    """
                                    )
                            )
                    ),
                    @ApiResponse(responseCode = "404", description = "Content ID not found")
            })
    @GetMapping(value = "/{contentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProcessingResult> getDocument(
            @Parameter(
                    description = "The stable document identifier (content_id)",
                    example = "FR-2024-CA-000123"
            )
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
            case REJECTED -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.ACCEPTED;
        };
    }
}
