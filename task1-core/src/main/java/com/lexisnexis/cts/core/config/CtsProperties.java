package com.lexisnexis.cts.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration properties for the Content Transformation Service.
 * Bound from the "cts" prefix in application.yml or environment variables.
 *
 * <p>Environment variable mapping examples:
 * <ul>
 *   <li>CTS_OUTPUT_PATH → cts.output.path</li>
 *   <li>CTS_PROCESSING_CONCURRENCY → cts.processing.concurrency</li>
 *   <li>CTS_PROCESSING_MAX_FILE_SIZE → cts.processing.max-file-size</li>
 * </ul>
 * </p>
 */
@ConfigurationProperties(prefix = "cts")
public record CtsProperties(
        Output output,
        Processing processing
) {

    public CtsProperties {
        if (output == null) {
            output = new Output("./output");
        }
        if (processing == null) {
            processing = new Processing(4, "10MB");
        }
    }

    /**
     * Output/artifact storage configuration.
     */
    public record Output(
            /** Filesystem path where published artifacts are stored. */
            String path
    ) {
        public Output {
            if (path == null || path.isBlank()) {
                path = "./output";
            }
        }
    }

    /**
     * Processing configuration for concurrency and resource limits.
     */
    public record Processing(
            /** Number of concurrent threads for batch processing. */
            int concurrency,
            /** Maximum allowed XML file size (e.g., "10MB"). */
            String maxFileSize
    ) {
        public Processing {
            if (concurrency <= 0) {
                concurrency = 4;
            }
            if (maxFileSize == null || maxFileSize.isBlank()) {
                maxFileSize = "10MB";
            }
        }
    }
}
