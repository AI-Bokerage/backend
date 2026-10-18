package com.example.authsvc.api.controller;

import com.example.authsvc.api.dto.response.ErrorResponse;
import com.example.authsvc.api.dto.response.KeyListResponse;
import com.example.authsvc.api.dto.response.RotateKeyResponse;
import com.example.authsvc.infrastructure.security.jwt.jwks.JwtKeyEntry;
import com.example.authsvc.infrastructure.security.jwt.jwks.JwtKeyRegistry;
import com.example.authsvc.infrastructure.security.jwt.jwks.JwtKeyRotationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Internal endpoints for runtime JWT signing-key rotation ({@code jwt.signing-mode=local} only).
 *
 * <p>Protected by {@link com.example.authsvc.infrastructure.security.filter.InternalTokenAuthFilter} —
 * callers must include {@code X-Internal-Secret: <INTERNAL_SERVICE_SECRET>} header.
 */
@Slf4j
@RestController
@RequestMapping("/internal/auth/keys")
@RequiredArgsConstructor
@Tag(name = "JWKS Admin (internal)", description =
        "Runtime JWT signing-key rotation for jwt.signing-mode=local deployments — no restart required. " +
        "Gated by X-Internal-Secret, same as every other /internal/** endpoint.")
@SecurityRequirement(name = "internalSecret")
public class JwksAdminController {

    private final JwtKeyRotationService rotationService;
    private final JwtKeyRegistry        registry;

    @Operation(
            summary = "Rotate the active signing key",
            description = "Generates a new RSA keypair, persists it (AES-256-GCM encrypted) to Postgres, and " +
                    "promotes it to active immediately. Only the public key ever leaves this endpoint. " +
                    "Throws in jwt.signing-mode=kms — creating a KMS key is an AWS-side action, out of scope here."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "New key generated and promoted active",
                    content = @Content(schema = @Schema(implementation = RotateKeyResponse.class))),
            @ApiResponse(responseCode = "500", description = "jwt.signing-mode is not local",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/rotate")
    public ResponseEntity<RotateKeyResponse> rotate() {
        JwtKeyRotationService.RotatedKey result = rotationService.rotate();
        log.info("internal.jwks.rotated kid={}", result.kid());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RotateKeyResponse(result.kid(), result.publicKeyPem()));
    }

    @Operation(
            summary = "Retire a rotated signing key",
            description = "Removes a previously-rotated key from Postgres and the live registry. " +
                    "YAML-sourced keys aren't managed here — those still follow the restart-based procedure."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Key retired"),
            @ApiResponse(responseCode = "404", description = "No rotated key found for this kid",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Cannot retire the currently active signing key — rotate first",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{kid}/retire")
    public ResponseEntity<Void> retire(
            @Parameter(description = "The kid to retire, as returned by /rotate") @PathVariable String kid) {
        rotationService.retire(kid);
        log.info("internal.jwks.retired kid={}", kid);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "List signing keys in the live registry",
            description = "Includes both YAML-configured and DB-rotated keys, and which kid is currently active."
    )
    @ApiResponse(responseCode = "200", description = "Current registry state",
            content = @Content(schema = @Schema(implementation = KeyListResponse.class)))
    @GetMapping
    public ResponseEntity<KeyListResponse> list() {
        List<String> kids = registry.allEntries().stream()
                .map(JwtKeyEntry::kid)
                .toList();
        return ResponseEntity.ok(new KeyListResponse(registry.getActiveKid(), kids));
    }
}
