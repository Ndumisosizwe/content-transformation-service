package com.lexisnexis.cts.batch.controller;

import com.lexisnexis.cts.batch.service.BatchProcessingService;
import com.lexisnexis.cts.core.model.ProcessingResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BatchController.class)
class BatchControllerTest {

    /**
     * Minimal Spring Boot configuration for the slice test.
     * task2-batch doesn't have its own @SpringBootApplication class,
     * so we provide one here for @WebMvcTest to bootstrap from.
     */
    @SpringBootApplication
    static class TestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BatchProcessingService batchProcessingService;

    @Test
    void submitBatch_withValidFiles_returns200WithResults() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile(
                "files", "doc1.xml", "application/xml",
                "<doc>1</doc>".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile file2 = new MockMultipartFile(
                "files", "doc2.xml", "application/xml",
                "<doc>2</doc>".getBytes(StandardCharsets.UTF_8));

        List<ProcessingResult> results = List.of(
                ProcessingResult.success("ID-001", "h1", null, "text1"),
                ProcessingResult.success("ID-002", "h2", null, "text2"));

        when(batchProcessingService.processBatch(anyList())).thenReturn(results);

        mockMvc.perform(multipart("/api/v1/documents/batch")
                        .file(file1)
                        .file(file2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.successful").value(2))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[0].content_id").value("ID-001"))
                .andExpect(jsonPath("$.results[1].content_id").value("ID-002"));

        verify(batchProcessingService).processBatch(anyList());
    }

    @Test
    void submitBatch_withMixedResults_returnsCorrectCounts() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile(
                "files", "valid.xml", "application/xml",
                "<doc>valid</doc>".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile file2 = new MockMultipartFile(
                "files", "invalid.xml", "application/xml",
                "<doc>invalid</doc>".getBytes(StandardCharsets.UTF_8));

        List<ProcessingResult> results = List.of(
                ProcessingResult.success("ID-001", "h1", null, "text"),
                ProcessingResult.validationFailed("ID-002", "h2", List.of()));

        when(batchProcessingService.processBatch(anyList())).thenReturn(results);

        mockMvc.perform(multipart("/api/v1/documents/batch")
                        .file(file1)
                        .file(file2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.successful").value(1))
                .andExpect(jsonPath("$.failed").value(1));
    }

    @Test
    void submitBatch_withNoFiles_returns400() throws Exception {
        mockMvc.perform(multipart("/api/v1/documents/batch"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitBatch_withEmptyFile_skipsEmpty() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "files", "empty.xml", "application/xml", new byte[0]);

        mockMvc.perform(multipart("/api/v1/documents/batch")
                        .file(emptyFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.successful").value(0))
                .andExpect(jsonPath("$.failed").value(0));

        verifyNoInteractions(batchProcessingService);
    }

    @Test
    void submitBatch_withDuplicateResults_countsAsSuccessful() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files", "dup.xml", "application/xml",
                "<doc>dup</doc>".getBytes(StandardCharsets.UTF_8));

        List<ProcessingResult> results = List.of(
                ProcessingResult.duplicateSkipped("ID-001", "h1"));

        when(batchProcessingService.processBatch(anyList())).thenReturn(results);

        mockMvc.perform(multipart("/api/v1/documents/batch")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.successful").value(1))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.results[0].status").value("DUPLICATE_SKIPPED"));
    }

    @Test
    void submitBatch_withMultipleFiles_preservesOrder() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile(
                "files", "a.xml", "application/xml", "<a/>".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile file2 = new MockMultipartFile(
                "files", "b.xml", "application/xml", "<b/>".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile file3 = new MockMultipartFile(
                "files", "c.xml", "application/xml", "<c/>".getBytes(StandardCharsets.UTF_8));

        List<ProcessingResult> results = List.of(
                ProcessingResult.success("A", "ha", null, "ta"),
                ProcessingResult.validationFailed("B", "hb", List.of()),
                ProcessingResult.success("C", "hc", null, "tc"));

        when(batchProcessingService.processBatch(anyList())).thenReturn(results);

        mockMvc.perform(multipart("/api/v1/documents/batch")
                        .file(file1)
                        .file(file2)
                        .file(file3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].content_id").value("A"))
                .andExpect(jsonPath("$.results[1].content_id").value("B"))
                .andExpect(jsonPath("$.results[2].content_id").value("C"));
    }
}
