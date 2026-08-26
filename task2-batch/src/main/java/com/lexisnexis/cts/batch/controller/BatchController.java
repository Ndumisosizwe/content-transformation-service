package com.lexisnexis.cts.batch.controller;

import com.lexisnexis.cts.batch.service.BatchProcessingService;
import com.lexisnexis.cts.core.model.DocumentStatus;
import com.lexisnexis.cts.core.model.ProcessingConstants;
import com.lexisnexis.cts.core.model.ProcessingResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * REST controller for batch document submission.
 */
@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "Batch", description = "Batch document submission with concurrent processing")
public class BatchController {

    private static final Logger log = LoggerFactory.getLogger(BatchController.class);

    private final BatchProcessingService batchProcessingService;

    public BatchController(BatchProcessingService batchProcessingService) {
        this.batchProcessingService = batchProcessingService;
    }

    @Operation(
            summary = "Submit multiple XML documents for batch processing",
            description = """
                    Upload one or more XML files as multipart/form-data. Each file is processed \
                    concurrently using the configured thread pool (default: 4 threads). \
                    Results are returned in submission order.
                    
                    **Example curl:**
                    ```
                    curl -X POST http://localhost:8080/api/v1/documents/batch \\
                      -F "files=@samples/valid-judgment.xml" \\
                      -F "files=@samples/valid-judgment-minimal.xml" \\
                      -F "files=@samples/invalid-bad-date.xml"
                    ```
                    """,
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Batch processed successfully -- results returned for each document",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = BatchResponse.class),
                                    examples = @ExampleObject(
                                            name = "Mixed batch result",
                                            summary = "2 published, 1 validation failure",
                                            value = """
                                                    {
                                                      "total": 3,
                                                      "successful": 2,
                                                      "failed": 1,
                                                      "results": [
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
                                                        },
                                                        {
                                                          "content_id": "FR-2024-MIN-001",
                                                          "status": "PUBLISHED",
                                                          "content_hash": "b2c3d4e5f6a1...",
                                                          "processed_at": "2024-03-12T10:30:00Z"
                                                        },
                                                        {
                                                          "content_id": "FR-2024-BAD-DATE",
                                                          "status": "VALIDATION_FAILED",
                                                          "content_hash": "c3d4e5f6a1b2...",
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
                                                      ]
                                                    }
                                                    """
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "No files provided, empty files, or unreadable file",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = BatchResponse.class),
                                    examples = @ExampleObject(
                                            name = "Empty submission",
                                            summary = "No valid files in request",
                                            value = """
                                                    {
                                                      "total": 0,
                                                      "successful": 0,
                                                      "failed": 0,
                                                      "results": []
                                                    }
                                                    """
                                    )
                            )
                    )
            }
    )
    @PostMapping(value = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BatchResponse> submitBatch(
            @RequestParam("files") List<MultipartFile> files) {

        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new BatchResponse(0, 0, 0, List.of()));
        }

        log.info("Received batch submission with {} file(s)", files.size());

        // Read all file contents upfront to detect I/O errors early
        List<byte[]> documents = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            try {
                byte[] content = file.getBytes();
                if (content.length == 0) {
                    log.warn("Skipping empty file: {}", file.getOriginalFilename());
                    continue;
                }
                documents.add(content);
            } catch (IOException e) {
                log.error("Failed to read uploaded file {}: {}", file.getOriginalFilename(), e.getMessage());
                return ResponseEntity.badRequest()
                        .body(new BatchResponse(files.size(), 0, 0,
                                List.of(ProcessingResult.rejected(
                                        ProcessingConstants.UNKNOWN_CONTENT_ID,
                                        "Failed to read file: " + file.getOriginalFilename()))));
            }
        }

        if (documents.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new BatchResponse(files.size(), 0, 0, List.of()));
        }

        // Process all documents concurrently
        List<ProcessingResult> results = batchProcessingService.processBatch(documents);

        // Compute summary counts
        long successCount = results.stream()
                .filter(r -> r.status() == DocumentStatus.PUBLISHED
                        || r.status() == DocumentStatus.DUPLICATE_SKIPPED)
                .count();
        long failedCount = results.size() - successCount;

        BatchResponse response = new BatchResponse(
                results.size(),
                (int) successCount,
                (int) failedCount,
                results
        );

        log.info("Batch processing complete: {} submitted, {} succeeded, {} failed",
                results.size(), successCount, failedCount);

        return ResponseEntity.ok(response);
    }

    /**
     * Response wrapper for batch processing results, providing summary counts
     * alongside individual document results.
     */
    @Schema(description = "Batch processing response with summary counts and per-document results")
    public record BatchResponse(
            @Schema(description = "Total number of documents processed", example = "3")
            int total,
            @Schema(description = "Number of documents successfully published or detected as duplicates", example = "2")
            int successful,
            @Schema(description = "Number of documents that failed validation or transformation", example = "1")
            int failed,
            @Schema(description = "Individual processing results in submission order")
            List<ProcessingResult> results
    ) {}
}
