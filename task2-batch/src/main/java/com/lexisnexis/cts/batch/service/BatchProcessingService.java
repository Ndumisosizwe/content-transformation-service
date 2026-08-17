package com.lexisnexis.cts.batch.service;

import com.lexisnexis.cts.batch.metrics.ProcessingMetricsService;
import com.lexisnexis.cts.core.model.ProcessingResult;
import com.lexisnexis.cts.core.service.DocumentProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Batch processing service that submits multiple documents for concurrent processing.
 * Uses the configured thread pool to process documents in parallel, respecting
 * the concurrency limit defined in application configuration.
 */
@Service
public class BatchProcessingService {

    private static final Logger log = LoggerFactory.getLogger(BatchProcessingService.class);

    private final DocumentProcessingService documentProcessingService;
    private final Executor batchProcessingExecutor;
    private final ProcessingMetricsService metricsService;

    public BatchProcessingService(DocumentProcessingService documentProcessingService,
                                  @Qualifier("batchProcessingExecutor") Executor batchProcessingExecutor,
                                  ProcessingMetricsService metricsService) {
        this.documentProcessingService = documentProcessingService;
        this.batchProcessingExecutor = batchProcessingExecutor;
        this.metricsService = metricsService;
    }

    /**
     * Processes a batch of XML documents concurrently.
     *
     * <p>Each document is submitted to the thread pool for parallel processing.
     * Results are collected in submission order. If an individual document throws
     * an unexpected exception, it is caught and recorded as a transformation failure.</p>
     *
     * @param documents list of raw XML document byte arrays
     * @return list of processing results in the same order as the input
     */
    public List<ProcessingResult> processBatch(List<byte[]> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        log.info("Starting batch processing of {} document(s)", documents.size());
        long startTime = System.currentTimeMillis();

        // Submit all documents for concurrent processing
        List<CompletableFuture<ProcessingResult>> futures = new ArrayList<>(documents.size());
        for (byte[] document : documents) {
            CompletableFuture<ProcessingResult> future = CompletableFuture.supplyAsync(
                    () -> processAndRecord(document), batchProcessingExecutor);
            futures.add(future);
        }

        // Collect all results (preserving order)
        List<ProcessingResult> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Batch processing complete: {} document(s) in {}ms", documents.size(), elapsed);

        // Record batch-level metrics
        metricsService.recordBatchProcessed(results, elapsed);

        return results;
    }

    /**
     * Processes a single document, records per-document metrics, and catches
     * unexpected exceptions to ensure the batch never fails entirely due to one bad document.
     */
    private ProcessingResult processAndRecord(byte[] xmlContent) {
        long docStart = System.currentTimeMillis();
        ProcessingResult result;
        try {
            result = documentProcessingService.process(xmlContent);
        } catch (Exception e) {
            log.error("Unexpected error processing document in batch: {}", e.getMessage(), e);
            result = ProcessingResult.transformationFailed("UNKNOWN", "N/A",
                    "Unexpected processing error: " + e.getMessage());
        }
        long docElapsed = System.currentTimeMillis() - docStart;
        metricsService.recordDocumentProcessed(result, docElapsed);
        return result;
    }
}
