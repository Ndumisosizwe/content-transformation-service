package com.lexisnexis.cts.batch.controller;

import com.lexisnexis.cts.batch.service.BatchProcessingService;
import com.lexisnexis.cts.core.model.DocumentStatus;
import com.lexisnexis.cts.core.model.ProcessingConstants;
import com.lexisnexis.cts.core.model.ProcessingResult;
import io.swagger.v3.oas.annotations.Operation;
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

    @Operation(summary = "Submit multiple XML documents for batch processing",
            description = "Each file is processed concurrently using the configured thread pool. Results are returned in submission order.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Batch processed — results returned"),
                    @ApiResponse(responseCode = "400", description = "No files provided or unreadable file")
            })
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
    public record BatchResponse(
            int total,
            int successful,
            int failed,
            List<ProcessingResult> results
    ) {}
}
