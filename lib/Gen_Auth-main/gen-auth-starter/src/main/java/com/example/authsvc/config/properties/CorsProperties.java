package com.example.authsvc.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
// need to change them from here 
@Data
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    private List<String> allowedOrigins = List.of(
            "http://localhost:3000",
            "http://localhost:5173",
            "http://127.0.0.1:3000",
            "http://127.0.0.1:5173"
    );

    private List<String> allowedMethods = List.of(
            "GET",
            "POST",
            "PUT",
            "PATCH",
            "DELETE",
            "OPTIONS"
    );

    private List<String> allowedHeaders = List.of(
            "Authorization",
            "Content-Type",
            "Accept",
            "Origin",
            "Cache-Control",
            "X-Requested-With",
            "X-Tenant-ID",
            "X-Trace-ID"
    );

    private boolean allowCredentials = true;

    private long maxAge = 3600L;
}