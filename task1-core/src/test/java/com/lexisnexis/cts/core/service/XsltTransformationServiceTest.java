package com.lexisnexis.cts.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lexisnexis.cts.core.model.NormalizedDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for XsltTransformationService.
 * Covers successful transformation, JSON structure, plain text extraction, and error cases.
 */
class XsltTransformationServiceTest {

    private XsltTransformationService transformationService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        transformationService = new XsltTransformationService(objectMapper);
        transformationService.init();
    }

    @Test
    @DisplayName("Transform valid full judgment XML produces correct NormalizedDocument")
    void transformFullJudgment_producesCorrectDocument() throws IOException {
        byte[] xml = loadSample("valid-judgment.xml");

        NormalizedDocument doc = transformationService.transform(xml);

        assertThat(doc).isNotNull();
        assertThat(doc.contentId()).isEqualTo("FR-2024-CA-000123");
        assertThat(doc.title()).contains("Cour d'appel de Paris");
        assertThat(doc.court()).isEqualTo("Cour d'appel de Paris");
        assertThat(doc.jurisdiction()).isEqualTo("FR");
        assertThat(doc.decisionDate()).isEqualTo("2024-03-12");
    }

    @Test
    @DisplayName("Transform produces correct citations array")
    void transformFullJudgment_producesCorrectCitations() throws IOException {
        byte[] xml = loadSample("valid-judgment.xml");

        NormalizedDocument doc = transformationService.transform(xml);

        assertThat(doc.citations()).hasSize(2);
        assertThat(doc.citations().get(0).type()).isEqualTo("ECLI");
        assertThat(doc.citations().get(0).value()).isEqualTo("ECLI:FR:CA12345");
        assertThat(doc.citations().get(1).type()).isEqualTo("NOR");
        assertThat(doc.citations().get(1).value()).isEqualTo("NOR:ABCD1234567");
    }

    @Test
    @DisplayName("Transform produces correct parties array")
    void transformFullJudgment_producesCorrectParties() throws IOException {
        byte[] xml = loadSample("valid-judgment.xml");

        NormalizedDocument doc = transformationService.transform(xml);

        assertThat(doc.parties()).hasSize(2);
        assertThat(doc.parties().get(0).role()).isEqualTo("appellant");
        assertThat(doc.parties().get(0).name()).contains("ABC");
        assertThat(doc.parties().get(1).role()).isEqualTo("respondent");
        assertThat(doc.parties().get(1).name()).contains("Dupont");
    }

    @Test
    @DisplayName("Transform produces correct paragraphs with section types")
    void transformFullJudgment_producesCorrectParagraphs() throws IOException {
        byte[] xml = loadSample("valid-judgment.xml");

        NormalizedDocument doc = transformationService.transform(xml);

        assertThat(doc.paragraphs()).hasSize(4);
        assertThat(doc.paragraphs().get(0).id()).isEqualTo("p1");
        assertThat(doc.paragraphs().get(0).section()).isEqualTo("facts");
        assertThat(doc.paragraphs().get(1).section()).isEqualTo("reasons");
        assertThat(doc.paragraphs().get(2).section()).isEqualTo("reasons");
        assertThat(doc.paragraphs().get(3).section()).isEqualTo("disposition");
    }

    @Test
    @DisplayName("Transform produces full_text as concatenated paragraphs")
    void transformFullJudgment_producesFullText() throws IOException {
        byte[] xml = loadSample("valid-judgment.xml");

        NormalizedDocument doc = transformationService.transform(xml);

        assertThat(doc.fullText()).isNotBlank();
        assertThat(doc.fullText()).contains("Le litige porte sur");
        assertThat(doc.fullText()).contains("Par ces motifs");
    }

    @Test
    @DisplayName("Transform minimal judgment (no citations, no parties) produces empty arrays")
    void transformMinimalJudgment_producesEmptyArrays() throws IOException {
        byte[] xml = loadSample("valid-judgment-minimal.xml");

        NormalizedDocument doc = transformationService.transform(xml);

        assertThat(doc).isNotNull();
        assertThat(doc.contentId()).isEqualTo("FR-2024-TGI-000456");
        assertThat(doc.citations()).isEmpty();
        assertThat(doc.parties()).isEmpty();
        assertThat(doc.paragraphs()).hasSize(1);
    }

    @Test
    @DisplayName("transformToJson returns valid JSON string")
    void transformToJson_returnsValidJson() throws IOException {
        byte[] xml = loadSample("valid-judgment.xml");

        String json = transformationService.transformToJson(xml);

        assertThat(json).isNotBlank();
        assertThat(json).startsWith("{");
        assertThat(json).endsWith("}");
        assertThat(json).contains("\"content_id\"");
        assertThat(json).contains("\"full_text\"");
    }

    @Test
    @DisplayName("extractPlainText returns full_text from NormalizedDocument")
    void extractPlainText_returnsFullText() throws IOException {
        byte[] xml = loadSample("valid-judgment.xml");
        NormalizedDocument doc = transformationService.transform(xml);

        String plainText = transformationService.extractPlainText(doc);

        assertThat(plainText).isEqualTo(doc.fullText());
    }

    @Test
    @DisplayName("Transform with invalid XML throws TransformationException")
    void transformInvalidXml_throwsException() {
        byte[] invalidXml = "not xml at all".getBytes();

        assertThatThrownBy(() -> transformationService.transform(invalidXml))
                .isInstanceOf(XsltTransformationService.TransformationException.class);
    }

    @Test
    @DisplayName("Transform handles special characters in text (JSON escaping)")
    void transformWithSpecialChars_escapesCorrectly() throws IOException {
        byte[] xml = loadSample("valid-judgment.xml");

        String json = transformationService.transformToJson(xml);

        // The title contains an apostrophe (') and ° character - should not break JSON
        ObjectMapper mapper = new ObjectMapper();
        // If this doesn't throw, the JSON is valid
        mapper.readTree(json);
    }

    private byte[] loadSample(String filename) throws IOException {
        var stream = getClass().getClassLoader().getResourceAsStream("samples/" + filename);
        if (stream == null) {
            throw new IOException("Sample file not found: " + filename);
        }
        return stream.readAllBytes();
    }
}
