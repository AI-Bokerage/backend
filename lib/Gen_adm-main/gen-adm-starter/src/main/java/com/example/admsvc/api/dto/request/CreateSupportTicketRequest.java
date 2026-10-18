package com.example.admsvc.api.dto.request;

import com.example.admsvc.domain.enums.SupportTicketPriority;
import com.example.admsvc.domain.enums.SupportTicketType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSupportTicketRequest(
        @NotNull SupportTicketType type,
        @NotBlank @Size(max = 255) String subject,
        @NotBlank @Size(max = 5000) String description,
        @NotNull SupportTicketPriority priority) {
}
