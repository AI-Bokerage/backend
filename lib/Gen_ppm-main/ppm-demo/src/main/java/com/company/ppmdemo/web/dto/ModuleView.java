package com.company.ppmdemo.web.dto;

import com.company.ppmsvc.module.model.ModuleCode;
import java.util.UUID;

public record ModuleView(
    UUID id,
    ModuleCode code,
    String name,
    boolean active
) {}
