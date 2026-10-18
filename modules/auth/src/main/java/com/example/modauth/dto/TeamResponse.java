package com.example.modauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "A team: its lead and its current Broker members")
public record TeamResponse(
        UUID id,
        String name,
        PersonResponse teamLead,
        List<PersonResponse> members
) {
}
