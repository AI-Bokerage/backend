package com.example.authsvc.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gates and configures optional RabbitMQ event publishing, bound from the
 * {@code app.messaging.*} namespace. See {@code AuthEventPublisher} (only
 * registered when {@code enabled=true}) and {@code MessagingConfig} (declares
 * the exchange bean).
 */
@Data
@ConfigurationProperties(prefix = "app.messaging")
public class MessagingProperties {

    /** Off by default — no beans registered, no connection attempted. */
    private boolean enabled = false;

    /** Topic exchange name events are published to. Only read when enabled. */
    private String exchange = "auth.events";
}
