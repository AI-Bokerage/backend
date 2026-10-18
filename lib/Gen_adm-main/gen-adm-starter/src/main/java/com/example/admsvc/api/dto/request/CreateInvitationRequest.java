package com.example.admsvc.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record CreateInvitationRequest(
        @NotBlank @Email String email,
        @NotEmpty List<UUID> roleIds) {
}
