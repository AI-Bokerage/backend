package com.example.authsvc.api.dto.request;

import com.example.authsvc.common.validation.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

// Deliberately has no roleId field — public self-registration can never grant
// elevated roles. Only RegisterServiceImpl.register()'s roleId parameter,
// called from the internal-only endpoint (Task 5), can set one.
@Data
@Schema(description = "Public self-registration request")
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Schema(example = "user@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @ValidPassword
    @Schema(example = "••••••••", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @Schema(description = "Opaque tenant id; omit for single-tenant deployments (uses the platform sentinel)")
    private UUID tenantId;
}
