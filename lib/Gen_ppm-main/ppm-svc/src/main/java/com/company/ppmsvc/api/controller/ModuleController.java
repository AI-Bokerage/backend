package com.company.ppmsvc.api.controller;

import com.company.ppmsvc.api.dto.ApiResponse;
import com.company.ppmsvc.api.dto.request.CreateModuleRequest;
import com.company.ppmsvc.api.dto.request.UpdateModuleRequest;
import com.company.ppmsvc.api.dto.response.ModuleResponse;
import com.company.ppmsvc.api.mapper.ModuleApiMapper;
import com.company.ppmsvc.application.util.SecurityUtils;
import com.company.ppmsvc.module.model.ModuleCode;
import com.company.ppmsvc.module.usecase.ModuleApplicationService;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for the PPM Module Catalog.
 *
 * <p>Manages the platform-wide catalog of capability modules (e.g. Invoicing,
 * SSO, Reporting).  All endpoints require {@code ppm.access} permission
 * enforced by {@link com.company.ppmsvc.infrastructure.security.PpmAccessAuthorizationFilter}.
 *
 * <p>Base path: {@code /api/v1/ppm/modules}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ppm/modules")
@RequiredArgsConstructor
@Tag(name = "Module Catalog", description = "Platform capability module catalog — create, update, retrieve, and soft-delete modules.")
public class ModuleController {

    private final ModuleApplicationService moduleService;
    private final ModuleApiMapper          moduleApiMapper;

    // ── POST /api/v1/ppm/modules ──────────────────────────────────────────────

    @Operation(summary = "Create a new module",
        description = "Creates a new platform capability module. The module code must be unique across all active (non-deleted) modules.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Module created successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "A module with the given code already exists"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Request body failed validation")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<ModuleResponse>> create(
            @Valid @RequestBody CreateModuleRequest request) {
        log.debug("→ POST /api/v1/ppm/modules");
        UUID actorId = SecurityUtils.requireCurrentUserId();
        var module = moduleService.createModule(actorId, request.code(), request.name(),
            request.description(), request.active());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created("Module created.", moduleApiMapper.toResponse(module)));
    }

    // ── PATCH /api/v1/ppm/modules/{id} ────────────────────────────────────────

    @Operation(summary = "Partially update a module",
        description = "Updates one or more fields of an existing module. Null fields are left unchanged. The module code is immutable.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Module updated successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Module not found or has been deleted")
    })
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<ModuleResponse>> update(
            @Parameter(description = "Module UUID", required = true) @PathVariable UUID id,
            @RequestBody UpdateModuleRequest request) {
        log.debug("→ PATCH /api/v1/ppm/modules id={}", id);
        UUID actorId = SecurityUtils.requireCurrentUserId();
        var module = moduleService.updateModule(actorId, id, request.name(), request.description(), request.active());
        return ResponseEntity.ok(
            ApiResponse.ok("Module updated.", moduleApiMapper.toResponse(module)));
    }

    // ── GET /api/v1/ppm/modules/{id} ──────────────────────────────────────────

    @Operation(summary = "Get a module by ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Module found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Module not found or has been deleted")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ModuleResponse>> getById(
            @Parameter(description = "Module UUID", required = true) @PathVariable UUID id) {
        log.debug("→ GET /api/v1/ppm/modules id={}", id);
        return ResponseEntity.ok(
            ApiResponse.ok("Module retrieved.", moduleApiMapper.toResponse(moduleService.getModule(id))));
    }

    // ── GET /api/v1/ppm/modules ───────────────────────────────────────────────

    @Operation(summary = "List modules",
        description = "Returns all non-deleted modules ordered by code ascending. "
            + "Pass ?active=true to include only active modules, or ?active=false for inactive only.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Module list returned")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<ModuleResponse>>> list(
            @Parameter(description = "Filter by active flag; omit for all modules")
            @RequestParam(required = false) Boolean active) {
        log.debug("→ GET /api/v1/ppm/modules active={}", active);
        return ResponseEntity.ok(
            ApiResponse.ok("Modules retrieved.", moduleApiMapper.toResponseList(moduleService.listModules(active))));
    }

    // ── GET /api/v1/ppm/modules/code/{code} ──────────────────────────────────

    @Operation(summary = "Get a module by its code",
        description = "Looks up a module by its stable wire code (e.g. \"reporting\"). "
            + "Module codes are immutable and serve as human-readable slugs.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Module found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No module with the given code")
    })
    @GetMapping("/code/{code}")
    public ResponseEntity<ApiResponse<ModuleResponse>> getByCode(
            @Parameter(description = "Module wire code (e.g. \"reporting\", \"invoicing\")", required = true)
            @PathVariable String code) {
        log.debug("→ GET /api/v1/ppm/modules/code code={}", code);
        ModuleCode moduleCode;
        try {
            moduleCode = ModuleCode.fromValue(code);
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException(ErrorCode.MODULE_NOT_FOUND, "No module with code: " + code);
        }
        return ResponseEntity.ok(
            ApiResponse.ok("Module retrieved.", moduleApiMapper.toResponse(moduleService.getModuleByCode(moduleCode))));
    }

    // ── DELETE /api/v1/ppm/modules/{id} ───────────────────────────────────────

    @Operation(summary = "Soft-delete a module",
        description = "Marks the module as deleted. The record is retained in the database but hidden from all catalog queries.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Module deleted successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Module not found or already deleted")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Module UUID", required = true) @PathVariable UUID id) {
        log.debug("→ DELETE /api/v1/ppm/modules id={}", id);
        UUID actorId = SecurityUtils.requireCurrentUserId();
        moduleService.deleteModule(actorId, id);
        return ResponseEntity.noContent().build();
    }
}
