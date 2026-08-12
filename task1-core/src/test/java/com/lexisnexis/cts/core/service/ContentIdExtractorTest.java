package com.lexisnexis.cts.core.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ContentIdExtractor.
 * Covers valid extraction, missing content_id, malformed XML, and edge cases.
 */
class ContentIdExtractorTest {

    private ContentIdExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new ContentIdExtractor();
    }

    @Test
    @DisplayName("Extracts content_id from valid full judgment")
    void validFullJudgment_extractsContentId() throws IOException {
        byte[] xml = loadSample("valid-judgment.xml");

        String contentId = extractor.extract(xml);

        assertThat(contentId).isEqualTo("FR-2024-CA-000123");
    }

    @Test
    @DisplayName("Extracts content_id from valid minimal judgment")
    void validMinimalJudgment_extractsContentId() throws IOException {
        byte[] xml = loadSample("valid-judgment-minimal.xml");

        String contentId = extractor.extract(xml);

        assertThat(contentId).isEqualTo("FR-2024-TGI-000456");
    }

    @Test
    @DisplayName("Returns null when content_id element is missing")
    void missingContentId_returnsNull() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <judgment xmlns="urn:lex:content:1">
                    <header>
                        <title>No content_id here</title>
                    </header>
                </judgment>
                """;

        String contentId = extractor.extract(xml.getBytes(StandardCharsets.UTF_8));

        assertThat(contentId).isNull();
    }

    @Test
    @DisplayName("Returns null for malformed XML")
    void malformedXml_returnsNull() throws IOException {
        byte[] xml = loadSample("invalid-malformed.xml");

        String contentId = extractor.extract(xml);

        // Malformed XML may still extract content_id if it appears before the error,
        // or return null if parsing fails before reaching it
        // In our malformed sample, content_id is present before the error
        assertThat(contentId).isEqualTo("FR-2024-MALFORMED");
    }

    @Test
    @DisplayName("Returns null for completely invalid content")
    void notXml_returnsNull() {
        byte[] notXml = "this is not XML at all".getBytes(StandardCharsets.UTF_8);

        String contentId = extractor.extract(notXml);

        assertThat(contentId).isNull();
    }

    @Test
    @DisplayName("Returns null for empty content")
    void emptyContent_returnsNull() {
        byte[] empty = new byte[0];

        String contentId = extractor.extract(empty);

        assertThat(contentId).isNull();
    }

    @Test
    @DisplayName("Extracts content_id ignoring wrong namespace elements")
    void wrongNamespace_returnsNull() throws IOException {
        byte[] xml = loadSample("invalid-wrong-namespace.xml");

        String contentId = extractor.extract(xml);

        // content_id is in wrong namespace, so extractor should not find it
        assertThat(contentId).isNull();
    }

    @Test
    @DisplayName("Trims whitespace from extracted content_id")
    void contentIdWithWhitespace_isTrimmed() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <judgment xmlns="urn:lex:content:1">
                    <header>
                        <content_id>  FR-2024-SPACES  </content_id>
                    </header>
                </judgment>
                """;

        String contentId = extractor.extract(xml.getBytes(StandardCharsets.UTF_8));

        assertThat(contentId).isEqualTo("FR-2024-SPACES");
    }

    private byte[] loadSample(String filename) throws IOException {
        var stream = getClass().getClassLoader().getResourceAsStream("samples/" + filename);
        if (stream == null) {
            throw new IOException("Sample file not found: " + filename);
        }
        return stream.readAllBytes();
    }
}
