// gen-tnt-starter/src/main/java/com/example/tnt_svc/web/dto/StepCallbackRequest.java
package com.example.tnt_svc.web.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record StepCallbackRequest(
    @NotBlank String token,
    boolean success,
    Map<String, Object> context,
    String error
) {
}
