package com.example.authsvc.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response body returned after a successful JWT signing-key rotation")
public record RotateKeyResponse(
        @Schema(description = "The new key's kid, now the active signer")
        String kid,
        @Schema(description = "The new key's public key in PEM format")
        String publicKeyPem
) {}
