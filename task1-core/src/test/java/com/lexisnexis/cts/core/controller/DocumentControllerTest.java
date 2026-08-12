package com.lexisnexis.cts.core.controller;

import com.lexisnexis.cts.core.config.CtsConfiguration;
import com.lexisnexis.cts.core.service.ContentIdExtractor;
import com.lexisnexis.cts.core.service.DocumentProcessingService;
import com.lexisnexis.cts.core.service.XmlValidationService;
import com.lexisnexis.cts.core.service.XsltTransformationService;
import com.lexisnexis.cts.core.store.FileSystemArtifactStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for DocumentController REST endpoints.
 * Starts a Spring context with web infrastructure and all required beans.
 */
@SpringBootTest(classes = {
        JacksonAutoConfiguration.class,
        WebMvcAutoConfiguration.class,
        CtsConfiguration.class,
        DocumentController.class,
        GlobalExceptionHandler.class,
        DocumentProcessingService.class,
        XmlValidationService.class,
        XsltTransformationService.class,
        ContentIdExtractor.class,
        FileSystemArtifactStore.class
})
@AutoConfigureMockMvc
class DocumentControllerTest {

    private static Path tempOutputDir;

    static {
        try {
            tempOutputDir = Files.createTempDirectory("cts-test-output");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("cts.output.path", () -> tempOutputDir.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST valid XML returns 201 CREATED with processing result")
    void postValidXml_returns201() throws Exception {
        byte[] xml = loadSample("valid-judgment.xml");

        mockMvc.perform(post("/api/v1/documents")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(xml))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content_id").value("FR-2024-CA-000123"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.normalized_json.title").exists())
                .andExpect(jsonPath("$.plain_text").exists());
    }

    @Test
    @DisplayName("POST same XML twice returns 200 OK (duplicate skipped)")
    void postDuplicate_returns200() throws Exception {
        byte[] xml = loadSample("valid-judgment-minimal.xml");

        // First submission
        mockMvc.perform(post("/api/v1/documents")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(xml))
                .andExpect(status().isCreated());

        // Second submission (duplicate)
        mockMvc.perform(post("/api/v1/documents")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(xml))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DUPLICATE_SKIPPED"));
    }

    @Test
    @DisplayName("POST invalid XML (bad date) returns 422 with diagnostics")
    void postInvalidXml_returns422() throws Exception {
        byte[] xml = loadSample("invalid-bad-date.xml");

        mockMvc.perform(post("/api/v1/documents")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(xml))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.diagnostics").isArray())
                .andExpect(jsonPath("$.diagnostics[0].message").exists());
    }

    @Test
    @DisplayName("POST with wrong Content-Type returns 415")
    void postWrongContentType_returns415() throws Exception {
        mockMvc.perform(post("/api/v1/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"not xml\"}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("POST with empty body returns 400")
    void postEmptyBody_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/documents")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(new byte[0]))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET existing document returns 200 with result")
    void getExistingDocument_returns200() throws Exception {
        byte[] xml = loadSample("valid-judgment.xml");

        // Submit first (may be 201 or 200 if already submitted by another test)
        mockMvc.perform(post("/api/v1/documents")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(xml));

        // Retrieve
        mockMvc.perform(get("/api/v1/documents/FR-2024-CA-000123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content_id").value("FR-2024-CA-000123"));
    }

    @Test
    @DisplayName("GET non-existent document returns 404")
    void getNonExistentDocument_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/documents/DOES-NOT-EXIST"))
                .andExpect(status().isNotFound());
    }

    private byte[] loadSample(String filename) throws IOException {
        var stream = getClass().getClassLoader().getResourceAsStream("samples/" + filename);
        if (stream == null) {
            throw new IOException("Sample file not found: " + filename);
        }
        return stream.readAllBytes();
    }
}
