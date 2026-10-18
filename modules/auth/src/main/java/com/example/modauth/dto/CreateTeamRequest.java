package com.example.modauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

@Schema(description = "Name a team, pick its Team Lead (must already be an active TEAM_LEAD in this brokerage, "
        + "not yet leading another team), and optionally add existing Brokers to it")
public record CreateTeamRequest(
        @NotBlank
        @Schema(example = "North Region", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @NotNull
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        UUID teamLeadUserId,

        @Schema(description = "Existing Brokers (same brokerage) to add to the team immediately")
        List<UUID> brokerUserIds
) {
    public List<UUID> brokerUserIdsOrEmpty() {
        return brokerUserIds == null ? List.of() : brokerUserIds;
    }
}
