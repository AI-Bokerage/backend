package com.company.bsmsvc.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a com.fasterxml.jackson.databind.ObjectMapper (Jackson 2.x) bean.
 *
 * Spring Boot 4 auto-configures tools.jackson.databind.ObjectMapper (Jackson 3.x),
 * which is a different class from a different package. Components in this service
 * that were written against Jackson 2.x (e.g. AdmSvcClient) cannot receive the
 * Jackson 3.x bean via constructor injection. This explicit bean bridges the gap.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
