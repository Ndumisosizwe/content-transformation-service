package com.lexisnexis.cts.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata configuration for Swagger UI.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ctsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Content Transformation Service API")
                        .description("XML-to-JSON transformation service for French legal documents. "
                                + "Ingests court judgments in XML, validates against XSD, transforms to "
                                + "normalized JSON via XSLT 3.0 (Saxon-HE), and publishes artifacts "
                                + "suitable for downstream search and AI/RAG pipelines.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("LexisNexis CTS Team")));
    }
}
