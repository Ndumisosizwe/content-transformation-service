package com.lexisnexis.cts.batch.service;

import com.lexisnexis.cts.batch.metrics.ProcessingMetricsService;
import com.lexisnexis.cts.core.model.DocumentStatus;
import com.lexisnexis.cts.core.model.ProcessingResult;
import com.lexisnexis.cts.core.service.DocumentProcessingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchProcessingServiceTest {

    @Mock
    private DocumentProcessingService documentProcessingService;

    @Mock
    private ProcessingMetricsService metricsService;

    private BatchProcessingService batchProcessingService;
    private ThreadPoolTaskExecutor executor;

    @BeforeEach
    void setUp() {
        // Create a real executor for testing concurrency
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("test-batch-");
        executor.initialize();

        batchProcessingService = new BatchProcessingService(
                documentProcessingService, executor, metricsService);
    }

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    @Test
    void processBatch_emptyList_returnsEmptyResults() {
        List<ProcessingResult> results = batchProcessingService.processBatch(List.of());
        assertThat(results).isEmpty();
        verifyNoInteractions(metricsService);
    }

    @Test
    void processBatch_nullList_returnsEmptyResults() {
        List<ProcessingResult> results = batchProcessingService.processBatch(null);
        assertThat(results).isEmpty();
    }

    @Test
    void processBatch_singleDocument_processesAndReturnsResult() {
        byte[] xml = "<doc/>".getBytes(StandardCharsets.UTF_8);
        ProcessingResult expected = ProcessingResult.success("ID-001", "hash1", null, "text");
        when(documentProcessingService.process(xml)).thenReturn(expected);

        List<ProcessingResult> results = batchProcessingService.processBatch(List.of(xml));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).contentId()).isEqualTo("ID-001");
        assertThat(results.get(0).status()).isEqualTo(DocumentStatus.PUBLISHED);
        verify(metricsService).recordDocumentProcessed(eq(expected), anyLong());
        verify(metricsService).recordBatchProcessed(eq(results), anyLong());
    }

    @Test
    void processBatch_multipleDocuments_processesAllConcurrently() {
        byte[] xml1 = "<doc1/>".getBytes(StandardCharsets.UTF_8);
        byte[] xml2 = "<doc2/>".getBytes(StandardCharsets.UTF_8);
        byte[] xml3 = "<doc3/>".getBytes(StandardCharsets.UTF_8);

        when(documentProcessingService.process(xml1))
                .thenReturn(ProcessingResult.success("ID-001", "h1", null, "t1"));
        when(documentProcessingService.process(xml2))
                .thenReturn(ProcessingResult.validationFailed("ID-002", "h2", List.of()));
        when(documentProcessingService.process(xml3))
                .thenReturn(ProcessingResult.duplicateSkipped("ID-003", "h3"));

        List<ProcessingResult> results = batchProcessingService.processBatch(List.of(xml1, xml2, xml3));

        assertThat(results).hasSize(3);
        assertThat(results.get(0).status()).isEqualTo(DocumentStatus.PUBLISHED);
        assertThat(results.get(1).status()).isEqualTo(DocumentStatus.VALIDATION_FAILED);
        assertThat(results.get(2).status()).isEqualTo(DocumentStatus.DUPLICATE_SKIPPED);

        verify(documentProcessingService, times(3)).process(any(byte[].class));
        verify(metricsService, times(3)).recordDocumentProcessed(any(), anyLong());
        verify(metricsService).recordBatchProcessed(eq(results), anyLong());
    }

    @Test
    void processBatch_documentThrowsException_catchesAndReturnsFailure() {
        byte[] xml1 = "<good/>".getBytes(StandardCharsets.UTF_8);
        byte[] xml2 = "<bad/>".getBytes(StandardCharsets.UTF_8);

        when(documentProcessingService.process(xml1))
                .thenReturn(ProcessingResult.success("ID-001", "h1", null, "t1"));
        when(documentProcessingService.process(xml2))
                .thenThrow(new RuntimeException("Unexpected error"));

        List<ProcessingResult> results = batchProcessingService.processBatch(List.of(xml1, xml2));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).status()).isEqualTo(DocumentStatus.PUBLISHED);
        assertThat(results.get(1).status()).isEqualTo(DocumentStatus.TRANSFORMATION_FAILED);
        assertThat(results.get(1).contentId()).isEqualTo("UNKNOWN");
    }

    @Test
    void processBatch_allDocumentsFail_returnsAllFailures() {
        byte[] xml1 = "<bad1/>".getBytes(StandardCharsets.UTF_8);
        byte[] xml2 = "<bad2/>".getBytes(StandardCharsets.UTF_8);

        when(documentProcessingService.process(any()))
                .thenThrow(new RuntimeException("Boom"));

        List<ProcessingResult> results = batchProcessingService.processBatch(List.of(xml1, xml2));

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(r -> r.status() == DocumentStatus.TRANSFORMATION_FAILED);
    }

    @Test
    void processBatch_recordsMetricsForEachDocument() {
        byte[] xml = "<doc/>".getBytes(StandardCharsets.UTF_8);
        ProcessingResult expected = ProcessingResult.success("ID-001", "hash1", null, "text");
        when(documentProcessingService.process(xml)).thenReturn(expected);

        batchProcessingService.processBatch(List.of(xml));

        verify(metricsService).recordDocumentProcessed(eq(expected), anyLong());
    }
}
