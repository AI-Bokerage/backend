// gen-tnt-starter/src/main/java/com/example/tnt_svc/saga/ProvisioningStepsProperties.java
package com.example.tnt_svc.saga;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "gentnt.provisioning")
public class ProvisioningStepsProperties {

    private List<ProvisioningStepDefinition> steps = List.of();
    private int maxRetries = 3;
    private int timeoutMinutes = 10;
    private long retrySchedulerIntervalMs = 120000;
    private long timeoutSchedulerIntervalMs = 60000;

    public List<ProvisioningStepDefinition> getSteps() {
        return steps;
    }

    public void setSteps(List<ProvisioningStepDefinition> steps) {
        this.steps = steps;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public int getTimeoutMinutes() {
        return timeoutMinutes;
    }

    public void setTimeoutMinutes(int timeoutMinutes) {
        this.timeoutMinutes = timeoutMinutes;
    }

    public long getRetrySchedulerIntervalMs() {
        return retrySchedulerIntervalMs;
    }

    public void setRetrySchedulerIntervalMs(long retrySchedulerIntervalMs) {
        this.retrySchedulerIntervalMs = retrySchedulerIntervalMs;
    }

    public long getTimeoutSchedulerIntervalMs() {
        return timeoutSchedulerIntervalMs;
    }

    public void setTimeoutSchedulerIntervalMs(long timeoutSchedulerIntervalMs) {
        this.timeoutSchedulerIntervalMs = timeoutSchedulerIntervalMs;
    }
}
