package com.lexisnexis.cts.core.service;

import com.lexisnexis.cts.core.model.ValidationDiagnostic;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates XML documents against the judgment XSD schema.
 * Thread-safe: creates a new Validator per invocation (Schema is thread-safe, Validator is not).
 */
@Service
public class XmlValidationService {

    private static final Logger log = LoggerFactory.getLogger(XmlValidationService.class);
    private static final String XSD_PATH = "schemas/judgment.xsd";

    private Schema schema;

    @PostConstruct
    void init() {
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            // Disable external entity processing for security
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var xsdResource = new ClassPathResource(XSD_PATH);
            schema = factory.newSchema(new StreamSource(xsdResource.getInputStream()));
            log.info("XSD schema loaded successfully from classpath: {}", XSD_PATH);
        } catch (SAXException | IOException e) {
            throw new IllegalStateException("Failed to load XSD schema from classpath: " + XSD_PATH, e);
        }
    }

    /**
     * Validates XML content against the judgment XSD.
     *
     * @param xmlContent the raw XML as a byte array
     * @return list of validation diagnostics; empty list means valid
     */
    public List<ValidationDiagnostic> validate(byte[] xmlContent) {
        List<ValidationDiagnostic> diagnostics = new ArrayList<>();

        Validator validator = schema.newValidator();
        // Disable external entities for security
        try {
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (SAXException e) {
            log.warn("Could not set external access properties on validator", e);
        }

        validator.setErrorHandler(new CollectingErrorHandler(diagnostics));

        try {
            validator.validate(new StreamSource(new ByteArrayInputStream(xmlContent)));
        } catch (SAXException e) {
            // Fatal error already captured by error handler, but add if missing
            if (diagnostics.isEmpty()) {
                diagnostics.add(new ValidationDiagnostic(0, 0, "FATAL", e.getMessage()));
            }
        } catch (IOException e) {
            diagnostics.add(new ValidationDiagnostic(0, 0, "FATAL",
                    "IO error during validation: " + e.getMessage()));
        }

        if (!diagnostics.isEmpty()) {
            log.debug("Validation found {} issue(s)", diagnostics.size());
        }

        return diagnostics;
    }

    /**
     * Convenience method accepting XML as a String.
     */
    public List<ValidationDiagnostic> validate(String xmlContent) {
        return validate(xmlContent.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * SAX ErrorHandler that collects all errors/warnings into a list of diagnostics.
     */
    private static class CollectingErrorHandler implements ErrorHandler {

        private final List<ValidationDiagnostic> diagnostics;

        CollectingErrorHandler(List<ValidationDiagnostic> diagnostics) {
            this.diagnostics = diagnostics;
        }

        @Override
        public void warning(SAXParseException e) {
            diagnostics.add(toDiagnostic("WARNING", e));
        }

        @Override
        public void error(SAXParseException e) {
            diagnostics.add(toDiagnostic("ERROR", e));
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            diagnostics.add(toDiagnostic("FATAL", e));
            throw e; // Stop processing on fatal
        }

        private static ValidationDiagnostic toDiagnostic(String severity, SAXParseException e) {
            return new ValidationDiagnostic(
                    e.getLineNumber(),
                    e.getColumnNumber(),
                    severity,
                    e.getMessage()
            );
        }
    }
}
