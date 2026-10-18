package com.company.bsmsvc.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Result of a downgrade preflight analysis")
public record DowngradePreflightResponse(

    @Schema(description = "Number of internal users that exceed the target plan limit")
    int usersOverLimit,

    @Schema(description = "Number of active projects that exceed the target plan limit")
    int projectsOverLimit,

    @Schema(description = "Storage consumed beyond the target plan quota, in bytes")
    long storageOverLimitBytes,

    @Schema(description = "Feature codes that are currently active but not available in the target plan")
    List<String> featuresLost,

    @Schema(description = "Actionable warnings generated during preflight analysis")
    List<DowngradeWarningDto> warnings
) {
}
