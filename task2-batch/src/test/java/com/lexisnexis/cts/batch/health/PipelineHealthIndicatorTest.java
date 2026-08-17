package com.lexisnexis.cts.batch.health;

import com.lexisnexis.cts.core.config.CtsProperties;
import com.lexisnexis.cts.core.service.XmlValidationService;
import com.lexisnexis.cts.core.service.XsltTransformationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PipelineHealthIndicatorTest {

    @Mock
    private XmlValidationService xmlValidationService;

    @Mock
    private XsltTransformationService xsltTransformationService;

    @TempDir
    Path tempDir;

    @Test
    void health_allComponentsReady_reportsUp() {
        when(xmlValidationService.validate(any(byte[].class))).thenReturn(List.of());

        CtsProperties props = new CtsProperties(
                new CtsProperties.Output(tempDir.toString()),
                new CtsProperties.Processing(4, "10MB"));

        PipelineHealthIndicator indicator = new PipelineHealthIndicator(
                xmlValidationService, xsltTransformationService, props);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("xsd_schema", "loaded");
        assertThat(health.getDetails()).containsEntry("xslt_stylesheet", "compiled");
        assertThat(health.getDetails()).containsEntry("artifact_store", "writable");
        assertThat(health.getDetails()).containsEntry("output_path", tempDir.toString());
    }

    @Test
    void health_xsdValidationThrows_reportsDown() {
        when(xmlValidationService.validate(any(byte[].class)))
                .thenThrow(new RuntimeException("Schema not loaded"));

        CtsProperties props = new CtsProperties(
                new CtsProperties.Output(tempDir.toString()),
                new CtsProperties.Processing(4, "10MB"));

        PipelineHealthIndicator indicator = new PipelineHealthIndicator(
                xmlValidationService, xsltTransformationService, props);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("xsd_schema", "not_loaded");
    }

    @Test
    void health_outputPathNotWritable_reportsDown() {
        when(xmlValidationService.validate(any(byte[].class))).thenReturn(List.of());

        // Use a path that cannot be created on any OS (null device / invalid chars on Windows)
        String impossiblePath = System.getProperty("os.name").toLowerCase().contains("win")
                ? "Z:\\nonexistent\\impossible\\path\\zzz\\out"
                : "/proc/0/nonexistent/impossible/path";

        CtsProperties props = new CtsProperties(
                new CtsProperties.Output(impossiblePath),
                new CtsProperties.Processing(4, "10MB"));

        PipelineHealthIndicator indicator = new PipelineHealthIndicator(
                xmlValidationService, xsltTransformationService, props);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("artifact_store", "not_writable");
    }

    @Test
    void health_xsltServiceNull_reportsDown() {
        when(xmlValidationService.validate(any(byte[].class))).thenReturn(List.of());

        CtsProperties props = new CtsProperties(
                new CtsProperties.Output(tempDir.toString()),
                new CtsProperties.Processing(4, "10MB"));

        PipelineHealthIndicator indicator = new PipelineHealthIndicator(
                xmlValidationService, null, props);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("xslt_stylesheet", "not_compiled");
    }

    @Test
    void health_detailsAlwaysContainOutputPath() {
        when(xmlValidationService.validate(any(byte[].class))).thenReturn(List.of());

        String customPath = tempDir.resolve("custom-output").toString();
        CtsProperties props = new CtsProperties(
                new CtsProperties.Output(customPath),
                new CtsProperties.Processing(4, "10MB"));

        PipelineHealthIndicator indicator = new PipelineHealthIndicator(
                xmlValidationService, xsltTransformationService, props);

        Health health = indicator.health();

        assertThat(health.getDetails()).containsEntry("output_path", customPath);
    }
}
