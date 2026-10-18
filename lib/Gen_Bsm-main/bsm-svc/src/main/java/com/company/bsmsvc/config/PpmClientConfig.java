package com.company.bsmsvc.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Configures the shared RestClient bean used by PPM-SVC HTTP clients.
 *
 * <p>Uses {@link java.net.http.HttpClient} (JDK 11+) as the underlying transport.
 * The JDK HttpClient maintains a persistent connection pool with HTTP keep-alive by
 * default, satisfying the Phase C1 latency requirement of reusing HTTP connections.
 *
 * <p>Timeouts are driven by {@link PpmProperties} so they can be tuned per environment
 * without a code change.
 */
@Configuration
@EnableConfigurationProperties(PpmProperties.class)
public class PpmClientConfig {

    @Bean("ppmRestClient")
    public RestClient ppmRestClient(PpmProperties props) {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(props.connectTimeoutMs()))
            .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(props.readTimeoutMs()));

        return RestClient.builder()
            .requestFactory(factory)
            .baseUrl(props.baseUrl())
            .build();
    }
}
