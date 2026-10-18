// gen-tnt-starter/src/test/java/com/example/tnt_svc/saga/ProvisioningStepsPropertiesTest.java
package com.example.tnt_svc.saga;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProvisioningStepsPropertiesTest {

    @Test
    void bindsStepListFromFlatProperties() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("gentnt.provisioning.max-retries", "3");
        source.put("gentnt.provisioning.timeout-minutes", "10");
        source.put("gentnt.provisioning.steps[0].name", "SCHEMA_BOOTSTRAP");
        source.put("gentnt.provisioning.steps[0].url", "http://localhost:9001/step");
        source.put("gentnt.provisioning.steps[0].mode", "SYNC");
        source.put("gentnt.provisioning.steps[0].retryable", "true");
        source.put("gentnt.provisioning.steps[1].name", "AUTH_BOOTSTRAP");
        source.put("gentnt.provisioning.steps[1].url", "http://localhost:9002/step");
        source.put("gentnt.provisioning.steps[1].mode", "ASYNC");
        source.put("gentnt.provisioning.steps[1].retryable", "true");
        source.put("gentnt.provisioning.steps[1].compensate-url", "http://localhost:9002/compensate");

        ConfigurationPropertySource propertySource = new MapConfigurationPropertySource(source);
        ProvisioningStepsProperties properties = new Binder(propertySource)
            .bind("gentnt.provisioning", ProvisioningStepsProperties.class)
            .get();

        assertThat(properties.getMaxRetries()).isEqualTo(3);
        assertThat(properties.getTimeoutMinutes()).isEqualTo(10);
        assertThat(properties.getSteps()).hasSize(2);
        assertThat(properties.getSteps().get(0).name()).isEqualTo("SCHEMA_BOOTSTRAP");
        assertThat(properties.getSteps().get(0).mode()).isEqualTo(StepMode.SYNC);
        assertThat(properties.getSteps().get(1).mode()).isEqualTo(StepMode.ASYNC);
        assertThat(properties.getSteps().get(1).compensateUrl()).isEqualTo("http://localhost:9002/compensate");
    }
}
