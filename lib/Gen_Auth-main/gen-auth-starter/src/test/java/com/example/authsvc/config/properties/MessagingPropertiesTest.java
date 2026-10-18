package com.example.authsvc.config.properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessagingPropertiesTest {

    @Test
    void defaults_disabledWithDefaultExchange() {
        MessagingProperties props = new MessagingProperties();

        assertThat(props.isEnabled()).isFalse();
        assertThat(props.getExchange()).isEqualTo("auth.events");
    }
}
