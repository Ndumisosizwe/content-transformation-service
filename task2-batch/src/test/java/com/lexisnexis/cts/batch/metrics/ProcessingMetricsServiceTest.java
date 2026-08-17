package com.lexisnexis.cts.batch.metrics;

import com.lexisnexis.cts.core.model.DocumentStatus;
import com.lexisnexis.cts.core.model.ProcessingResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessingMetricsServiceTest {

    private MeterRegistry meterRegistry;
    private ProcessingMetricsService metricsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsService = new ProcessingMetricsService(meterRegistry);
    }

    @Test
    void recordDocumentProcessed_published_incrementsPublishedCounter() {
        ProcessingResult result = ProcessingResult.success("ID-001", "hash", null, "text");

        metricsService.recordDocumentProcessed(result, 100L);

        Counter counter = meterRegistry.find("cts.documents.processed")
                .tag("status", "published").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void recordDocumentProcessed_validationFailed_incrementsValidationCounter() {
        ProcessingResult result = ProcessingResult.validationFailed("ID-002", "hash", List.of());

        metricsService.recordDocumentProcessed(result, 50L);

        Counter counter = meterRegistry.find("cts.documents.processed")
                .tag("status", "validation_failed").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void recordDocumentProcessed_transformationFailed_incrementsTransformationCounter() {
        ProcessingResult result = ProcessingResult.transformationFailed("ID-003", "hash", "error");

        metricsService.recordDocumentProcessed(result, 75L);

        Counter counter = meterRegistry.find("cts.documents.processed")
                .tag("status", "transformation_failed").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void recordDocumentProcessed_duplicateSkipped_incrementsDuplicateCounter() {
        ProcessingResult result = ProcessingResult.duplicateSkipped("ID-004", "hash");

        metricsService.recordDocumentProcessed(result, 10L);

        Counter counter = meterRegistry.find("cts.documents.processed")
                .tag("status", "duplicate_skipped").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void recordDocumentProcessed_recordsDuration() {
        ProcessingResult result = ProcessingResult.success("ID-001", "hash", null, "text");

        metricsService.recordDocumentProcessed(result, 150L);

        Timer timer = meterRegistry.find("cts.documents.processing.duration").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(150);
    }

    @Test
    void recordBatchProcessed_incrementsBatchCounter() {
        List<ProcessingResult> results = List.of(
                ProcessingResult.success("ID-001", "h1", null, "t1"),
                ProcessingResult.validationFailed("ID-002", "h2", List.of()));

        metricsService.recordBatchProcessed(results, 500L);

        Counter batchCounter = meterRegistry.find("cts.batch.processed").counter();
        assertThat(batchCounter).isNotNull();
        assertThat(batchCounter.count()).isEqualTo(1.0);
    }

    @Test
    void recordBatchProcessed_recordsBatchDuration() {
        List<ProcessingResult> results = List.of(
                ProcessingResult.success("ID-001", "h1", null, "t1"));

        metricsService.recordBatchProcessed(results, 300L);

        Timer timer = meterRegistry.find("cts.batch.duration").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(300);
    }

    @Test
    void recordBatchProcessed_doesNotIncrementPerDocumentCounters() {
        List<ProcessingResult> results = List.of(
                ProcessingResult.success("ID-001", "h1", null, "t1"),
                ProcessingResult.success("ID-002", "h2", null, "t2"),
                ProcessingResult.validationFailed("ID-003", "h3", List.of()));

        metricsService.recordBatchProcessed(results, 200L);

        // Per-document counters should NOT be incremented by recordBatchProcessed
        // (they are handled by recordDocumentProcessed called per-document)
        Counter published = meterRegistry.find("cts.documents.processed")
                .tag("status", "published").counter();
        Counter failed = meterRegistry.find("cts.documents.processed")
                .tag("status", "validation_failed").counter();

        assertThat(published.count()).isEqualTo(0.0);
        assertThat(failed.count()).isEqualTo(0.0);
    }

    @Test
    void multipleRecordCalls_accumulateCorrectly() {
        ProcessingResult r1 = ProcessingResult.success("ID-001", "h1", null, "t1");
        ProcessingResult r2 = ProcessingResult.success("ID-002", "h2", null, "t2");

        metricsService.recordDocumentProcessed(r1, 100L);
        metricsService.recordDocumentProcessed(r2, 200L);

        Counter counter = meterRegistry.find("cts.documents.processed")
                .tag("status", "published").counter();
        assertThat(counter.count()).isEqualTo(2.0);

        Timer timer = meterRegistry.find("cts.documents.processing.duration").timer();
        assertThat(timer.count()).isEqualTo(2);
    }
}
