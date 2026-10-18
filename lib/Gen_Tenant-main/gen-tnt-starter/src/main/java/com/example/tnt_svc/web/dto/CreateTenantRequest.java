// gen-tnt-starter/src/main/java/com/example/tnt_svc/web/dto/CreateTenantRequest.java
package com.example.tnt_svc.web.dto;

import com.example.tnt_svc.validation.ValidSlug;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateTenantRequest(
    @NotBlank String name,
    @ValidSlug String slug,
    String region,
    @NotNull UUID primaryOwnerUserId,
    String idempotencyKey
) {
}
