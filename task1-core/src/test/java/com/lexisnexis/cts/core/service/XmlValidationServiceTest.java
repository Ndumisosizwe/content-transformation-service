package com.lexisnexis.cts.core.service;

import com.lexisnexis.cts.core.model.ValidationDiagnostic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for XmlValidationService.
 * Covers valid documents, schema violations, malformed XML, and edge cases.
 */
class XmlValidationServiceTest {

    private XmlValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new XmlValidationService();
        validationService.init();
    }

    @Test
    @DisplayName("Valid full judgment XML passes validation with no diagnostics")
    void validFullJudgment_passesValidation() throws IOException {
        byte[] xml = loadSample("valid-judgment.xml");

        List<ValidationDiagnostic> diagnostics = validationService.validate(xml);

        assertThat(diagnostics).isEmpty();
    }

    @Test
    @DisplayName("Valid minimal judgment (no citations/parties) passes validation")
    void validMinimalJudgment_passesValidation() throws IOException {
        byte[] xml = loadSample("valid-judgment-minimal.xml");

        List<ValidationDiagnostic> diagnostics = validationService.validate(xml);

        assertThat(diagnostics).isEmpty();
    }

    @Test
    @DisplayName("Invalid XML with bad date format fails validation")
    void invalidBadDate_failsValidation() throws IOException {
        byte[] xml = loadSample("invalid-bad-date.xml");

        List<ValidationDiagnostic> diagnostics = validationService.validate(xml);

        assertThat(diagnostics).isNotEmpty();
        assertThat(diagnostics.get(0).severity()).isIn("ERROR", "FATAL");
    }

    @Test
    @DisplayName("Invalid XML missing required content_id element fails validation")
    void invalidMissingContentId_failsValidation() throws IOException {
        byte[] xml = loadSample("invalid-missing-content-id.xml");

        List<ValidationDiagnostic> diagnostics = validationService.validate(xml);

        assertThat(diagnostics).isNotEmpty();
        assertThat(diagnostics.get(0).severity()).isIn("ERROR", "FATAL");
    }

    @Test
    @DisplayName("Malformed XML (unclosed tag) fails validation")
    void malformedXml_failsValidation() throws IOException {
        byte[] xml = loadSample("invalid-malformed.xml");

        List<ValidationDiagnostic> diagnostics = validationService.validate(xml);

        assertThat(diagnostics).isNotEmpty();
        assertThat(diagnostics.get(0).severity()).isEqualTo("FATAL");
    }

    @Test
    @DisplayName("XML with wrong namespace fails validation")
    void wrongNamespace_failsValidation() throws IOException {
        byte[] xml = loadSample("invalid-wrong-namespace.xml");

        List<ValidationDiagnostic> diagnostics = validationService.validate(xml);

        assertThat(diagnostics).isNotEmpty();
    }

    @Test
    @DisplayName("Empty XML content fails validation")
    void emptyContent_failsValidation() {
        byte[] xml = new byte[0];

        List<ValidationDiagnostic> diagnostics = validationService.validate(xml);

        assertThat(diagnostics).isNotEmpty();
    }

    @Test
    @DisplayName("Validate accepts String input")
    void validateString_passesValidation() throws IOException {
        String xml = new String(loadSample("valid-judgment.xml"), StandardCharsets.UTF_8);

        List<ValidationDiagnostic> diagnostics = validationService.validate(xml);

        assertThat(diagnostics).isEmpty();
    }

    @Test
    @DisplayName("Diagnostics contain line and column information")
    void diagnostics_containPositionInfo() throws IOException {
        byte[] xml = loadSample("invalid-bad-date.xml");

        List<ValidationDiagnostic> diagnostics = validationService.validate(xml);

        assertThat(diagnostics).isNotEmpty();
        ValidationDiagnostic first = diagnostics.get(0);
        assertThat(first.line()).isGreaterThan(0);
        assertThat(first.message()).isNotBlank();
    }

    private byte[] loadSample(String filename) throws IOException {
        var stream = getClass().getClassLoader().getResourceAsStream("samples/" + filename);
        if (stream == null) {
            throw new IOException("Sample file not found: " + filename);
        }
        return stream.readAllBytes();
    }
}
