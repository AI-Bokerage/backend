package com.company.ppmdemo.web.dto;

import jakarta.validation.constraints.NotNull;
import com.company.ppmsvc.module.model.ModuleCode;

public record CreateModuleRequest(
    @NotNull ModuleCode code,
    String name,
    String description
) {}
