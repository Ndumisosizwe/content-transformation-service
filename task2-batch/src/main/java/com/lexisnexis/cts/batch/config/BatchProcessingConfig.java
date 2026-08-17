package com.lexisnexis.cts.batch.config;

import com.lexisnexis.cts.core.config.CtsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configures the thread pool used for batch document processing.
 * Pool size is driven by {@link CtsProperties#processing()} concurrency setting.
 */
@Configuration
public class BatchProcessingConfig {

    private static final Logger log = LoggerFactory.getLogger(BatchProcessingConfig.class);

    private final CtsProperties ctsProperties;

    public BatchProcessingConfig(CtsProperties ctsProperties) {
        this.ctsProperties = ctsProperties;
    }

    /**
     * Creates a ThreadPoolTaskExecutor for concurrent batch processing.
     * Core and max pool size are both set to the configured concurrency value
     * to provide a fixed-size pool with predictable resource usage.
     */
    @Bean(name = "batchProcessingExecutor")
    public Executor batchProcessingExecutor() {
        int concurrency = ctsProperties.processing().concurrency();
        log.info("Configuring batch processing thread pool with concurrency={}", concurrency);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("cts-batch-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        return executor;
    }
}
