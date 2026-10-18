package com.example.authsvc.api.dto.request;

import com.example.authsvc.common.validation.ValidPassword;
import com.example.authsvc.domain.enums.UserType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class InternalCreateUserRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @ValidPassword
    private String password;

    private UUID tenantId;

    private UUID roleId;

    /**
     * Caller-supplied user id, e.g. to match an identity already assigned by an
     * upstream system (ADM/CPT-style event-driven provisioning). Null generates
     * a random id. If a user with this id already exists, creation is a no-op
     * and that user's id is returned (idempotent under event redelivery).
     */
    private UUID id;

    /** Null defaults to {@link UserType#TENANT_USER}. */
    private UserType userType;
}
