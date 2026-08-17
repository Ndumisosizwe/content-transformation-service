package com.lexisnexis.cts.batch.health;

import com.lexisnexis.cts.core.config.CtsProperties;
import com.lexisnexis.cts.core.service.XmlValidationService;
import com.lexisnexis.cts.core.service.XsltTransformationService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Custom health indicator that reports the pipeline's readiness.
 *
 * <p>Checks:
 * <ul>
 *   <li>XSD schema loaded (XmlValidationService initialized without error)</li>
 *   <li>XSLT stylesheet compiled (XsltTransformationService initialized without error)</li>
 *   <li>Artifact store output directory exists and is writable</li>
 * </ul>
 *
 * <p>If all checks pass, reports UP. If any check fails, reports DOWN with details.</p>
 */
@Component
public class PipelineHealthIndicator implements HealthIndicator {

    private final XmlValidationService xmlValidationService;
    private final XsltTransformationService xsltTransformationService;
    private final CtsProperties ctsProperties;

    public PipelineHealthIndicator(XmlValidationService xmlValidationService,
                                   XsltTransformationService xsltTransformationService,
                                   CtsProperties ctsProperties) {
        this.xmlValidationService = xmlValidationService;
        this.xsltTransformationService = xsltTransformationService;
        this.ctsProperties = ctsProperties;
    }

    @Override
    public Health health() {
        Health.Builder builder = new Health.Builder();
        boolean healthy = true;

        // Check XSD schema availability
        boolean xsdReady = isXsdReady();
        builder.withDetail("xsd_schema", xsdReady ? "loaded" : "not_loaded");
        if (!xsdReady) {
            healthy = false;
        }

        // Check XSLT stylesheet availability
        boolean xsltReady = isXsltReady();
        builder.withDetail("xslt_stylesheet", xsltReady ? "compiled" : "not_compiled");
        if (!xsltReady) {
            healthy = false;
        }

        // Check output directory writable
        boolean storeReady = isStoreWritable();
        builder.withDetail("artifact_store", storeReady ? "writable" : "not_writable");
        builder.withDetail("output_path", ctsProperties.output().path());
        if (!storeReady) {
            healthy = false;
        }

        return healthy ? builder.up().build() : builder.down().build();
    }

    /**
     * Validates that the XSD schema was successfully loaded.
     * If the service initialized without throwing (via @PostConstruct), it's ready.
     */
    private boolean isXsdReady() {
        try {
            // If the service exists as a bean, its @PostConstruct succeeded
            // Perform a trivial validation to confirm schema is usable
            xmlValidationService.validate("<ping/>".getBytes());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Validates that the XSLT stylesheet was successfully compiled.
     * The presence of the bean means @PostConstruct succeeded.
     */
    private boolean isXsltReady() {
        try {
            // The bean existing means init() completed. We can't cheaply test
            // transformation without a full document, so just confirm bean is alive.
            return xsltTransformationService != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks that the configured output directory exists (or can be created) and is writable.
     */
    private boolean isStoreWritable() {
        try {
            Path outputPath = Path.of(ctsProperties.output().path());
            if (!Files.exists(outputPath)) {
                Files.createDirectories(outputPath);
            }
            return Files.isWritable(outputPath);
        } catch (Exception e) {
            return false;
        }
    }
}
