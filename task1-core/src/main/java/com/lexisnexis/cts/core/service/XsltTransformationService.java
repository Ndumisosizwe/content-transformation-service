package com.lexisnexis.cts.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexisnexis.cts.core.model.NormalizedDocument;
import jakarta.annotation.PostConstruct;
import net.sf.saxon.TransformerFactoryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;

/**
 * Transforms validated XML documents to normalized JSON using XSLT 3.0 via Saxon-HE.
 *
 * <p>The compiled stylesheet (Templates) is cached and thread-safe.
 * A new Transformer is created per invocation (Transformer is NOT thread-safe).</p>
 */
@Service
public class XsltTransformationService {

    private static final Logger log = LoggerFactory.getLogger(XsltTransformationService.class);
    private static final String XSLT_PATH = "xslt/judgment-to-json.xsl";

    private final ObjectMapper objectMapper;
    private Templates compiledStylesheet;

    public XsltTransformationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        try {
            // Use Saxon-HE TransformerFactory explicitly
            TransformerFactoryImpl factory = new TransformerFactoryImpl();
            var xsltResource = new ClassPathResource(XSLT_PATH);
            compiledStylesheet = factory.newTemplates(new StreamSource(xsltResource.getInputStream()));
            log.info("XSLT stylesheet compiled successfully from classpath: {}", XSLT_PATH);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compile XSLT stylesheet from classpath: " + XSLT_PATH, e);
        }
    }

    /**
     * Transforms XML content to a NormalizedDocument using the pre-compiled XSLT stylesheet.
     *
     * @param xmlContent the validated XML as a byte array
     * @return the normalized document parsed from the XSLT JSON output
     * @throws TransformationException if transformation or JSON parsing fails
     */
    public NormalizedDocument transform(byte[] xmlContent) {
        String jsonOutput = transformToJson(xmlContent);
        try {
            return objectMapper.readValue(jsonOutput, NormalizedDocument.class);
        } catch (IOException e) {
            throw new TransformationException(
                    "Failed to parse XSLT output as NormalizedDocument: " + e.getMessage(), e);
        }
    }

    /**
     * Transforms XML content to raw JSON string using the XSLT stylesheet.
     *
     * @param xmlContent the validated XML as a byte array
     * @return the raw JSON string produced by the XSLT transformation
     * @throws TransformationException if the XSLT transformation fails
     */
    public String transformToJson(byte[] xmlContent) {
        try {
            Transformer transformer = compiledStylesheet.newTransformer();
            StreamSource source = new StreamSource(new ByteArrayInputStream(xmlContent));
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);

            transformer.transform(source, result);

            String jsonOutput = writer.toString();
            log.debug("XSLT transformation produced {} characters of JSON", jsonOutput.length());
            return jsonOutput;
        } catch (TransformerException e) {
            throw new TransformationException(
                    "XSLT transformation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts plain text from the XSLT-produced NormalizedDocument for RAG pipelines.
     *
     * @param normalizedDocument the transformed document
     * @return concatenated paragraph text suitable for AI/RAG ingestion
     */
    public String extractPlainText(NormalizedDocument normalizedDocument) {
        return normalizedDocument.fullText();
    }

    /**
     * Exception thrown when XSLT transformation or post-processing fails.
     */
    public static class TransformationException extends RuntimeException {
        public TransformationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
