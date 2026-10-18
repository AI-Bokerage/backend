// gen-tnt-starter/src/main/java/com/example/tnt_svc/saga/ProvisioningStepDefinition.java
package com.example.tnt_svc.saga;

public record ProvisioningStepDefinition(
    String name,
    String url,
    StepMode mode,
    boolean retryable,
    String compensateUrl
) {
}
