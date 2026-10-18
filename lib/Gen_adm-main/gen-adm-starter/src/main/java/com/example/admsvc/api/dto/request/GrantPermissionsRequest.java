package com.example.admsvc.api.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record GrantPermissionsRequest(@NotEmpty Set<String> permissionCodes) {
}
