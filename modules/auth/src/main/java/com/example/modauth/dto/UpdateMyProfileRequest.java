package com.example.modauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Fields to change on your own profile; omit a field to leave it as is")
public record UpdateMyProfileRequest(
        @Size(min = 1, max = 255) @Schema(example = "Jamie Rivera") String name,
        @Size(max = 50) @Pattern(regexp = "^[0-9+()\\-. ]*$", message = "Phone may contain digits, spaces and + ( ) - . only")
        @Schema(example = "+1 416 555 0100") String phone
) {
}
