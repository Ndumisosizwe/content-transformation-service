package com.lexisnexis.cts.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Central configuration class for the CTS core module.
 * Enables configuration property binding and provides shared beans.
 */
@Configuration
@EnableConfigurationProperties(CtsProperties.class)
public class CtsConfiguration {

    /**
     * Configures the Jackson ObjectMapper with Java time support
     * and ISO-8601 date formatting.
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
