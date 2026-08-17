package com.lexisnexis.cts.batch.metrics;

import com.lexisnexis.cts.core.model.DocumentStatus;
import com.lexisnexis.cts.core.model.ProcessingResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Provides Micrometer-based metrics for document processing.
 *
 * <p>Metrics exposed:
 * <ul>
 *   <li>{@code cts.documents.processed} — counter, total documents processed (tagged by status)</li>
 *   <li>{@code cts.documents.processing.duration} — timer, per-document processing time</li>
 *   <li>{@code cts.batch.processed} — counter, total batch requests processed</li>
 *   <li>{@code cts.batch.duration} — timer, end-to-end batch processing time</li>
 * </ul>
 * </p>
 */
@Service
public class ProcessingMetricsService {

    private final MeterRegistry meterRegistry;

    private final Counter publishedCounter;
    private final Counter validationFailedCounter;
    private final Counter transformationFailedCounter;
    private final Counter duplicateCounter;
    private final Counter batchCounter;
    private final Timer documentTimer;
    private final Timer batchTimer;

    public ProcessingMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.publishedCounter = Counter.builder("cts.documents.processed")
                .tag("status", "published")
                .description("Documents successfully published")
                .register(meterRegistry);

        this.validationFailedCounter = Counter.builder("cts.documents.processed")
                .tag("status", "validation_failed")
                .description("Documents that failed validation")
                .register(meterRegistry);

        this.transformationFailedCounter = Counter.builder("cts.documents.processed")
                .tag("status", "transformation_failed")
                .description("Documents that failed transformation")
                .register(meterRegistry);

        this.duplicateCounter = Counter.builder("cts.documents.processed")
                .tag("status", "duplicate_skipped")
                .description("Documents skipped as duplicates")
                .register(meterRegistry);

        this.batchCounter = Counter.builder("cts.batch.processed")
                .description("Total batch requests processed")
                .register(meterRegistry);

        this.documentTimer = Timer.builder("cts.documents.processing.duration")
                .description("Time to process a single document")
                .register(meterRegistry);

        this.batchTimer = Timer.builder("cts.batch.duration")
                .description("End-to-end batch processing time")
                .register(meterRegistry);
    }

    /**
     * Records metrics for a single document processing result.
     *
     * @param result the processing result
     * @param durationMs processing duration in milliseconds
     */
    public void recordDocumentProcessed(ProcessingResult result, long durationMs) {
        incrementStatusCounter(result.status());
        documentTimer.record(Duration.ofMillis(durationMs));
    }

    /**
     * Records metrics for a completed batch processing run.
     *
     * @param results all results from the batch
     * @param durationMs total batch processing duration in milliseconds
     */
    public void recordBatchProcessed(List<ProcessingResult> results, long durationMs) {
        batchCounter.increment();
        batchTimer.record(Duration.ofMillis(durationMs));

        // Increment per-status counters for each document in the batch
        for (ProcessingResult result : results) {
            incrementStatusCounter(result.status());
        }
    }

    private void incrementStatusCounter(DocumentStatus status) {
        switch (status) {
            case PUBLISHED -> publishedCounter.increment();
            case VALIDATION_FAILED -> validationFailedCounter.increment();
            case TRANSFORMATION_FAILED -> transformationFailedCounter.increment();
            case DUPLICATE_SKIPPED -> duplicateCounter.increment();
            default -> { /* RECEIVED, VALIDATING, TRANSFORMING — transient states, not counted */ }
        }
    }
}
