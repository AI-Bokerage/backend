package com.company.bsmsvc.api.controller;

import com.company.bsmsvc.api.dto.request.CreateMigrationPlanRequest;
import com.company.bsmsvc.api.dto.response.ApiResponse;
import com.company.bsmsvc.api.dto.response.MigrationPlanResponse;
import com.company.bsmsvc.api.dto.response.PaginationMetadata;
import com.company.bsmsvc.api.mapper.MigrationPlanApiMapper;
import com.company.bsmsvc.application.service.MigrationPlanService;
import com.company.bsmsvc.application.util.PaginationUtils;
import com.company.bsmsvc.domain.enums.MigrationPlanStatus;
import com.company.bsmsvc.domain.model.MigrationPlan;
import com.company.bsmsvc.domain.model.MigrationPlanFilter;
import com.company.bsmsvc.domain.model.PageResult;
import io.cpms.common.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bsm/migration-plans")
@RequiredArgsConstructor
@Tag(name = "Migration Plans", description = "Plan migration planning endpoints")
public class MigrationPlanController {

    private final MigrationPlanService migrationPlanService;
    private final MigrationPlanApiMapper migrationPlanApiMapper;

    @RequirePermission("billing.admin")
    @PostMapping
    @Operation(summary = "Create a migration plan with one or more migration items")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        required = true,
        content = @Content(
            schema = @Schema(implementation = CreateMigrationPlanRequest.class),
            examples = @ExampleObject(
                name = "Create Migration Plan Request",
                value = """
                    {
                      "subscriptionId": "11111111-1111-1111-1111-111111111111",
                      "tenantId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                      "targetPlanVersionId": "22222222-2222-2222-2222-222222222222",
                      "createdBy": "33333333-3333-3333-3333-333333333333",
                      "items": [
                        {
                          "resourceType": "PROJECT",
                          "resourceId": "44444444-4444-4444-4444-444444444444",
                          "action": "ARCHIVE",
                          "metadata": {
                            "reason": "Over project cap"
                          }
                        }
                      ]
                    }
                    """
            )
        )
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Migration plan created successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failure", content = @Content(
            schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)
        )),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Subscription or target plan not found", content = @Content(
            schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)
        )),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Business rule violation", content = @Content(
            schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)
        ))
    })
    public ResponseEntity<ApiResponse<MigrationPlanResponse>> createMigrationPlan(@Valid @RequestBody CreateMigrationPlanRequest request) {
        MigrationPlanResponse response = migrationPlanApiMapper.toResponse(
            migrationPlanService.createMigrationPlan(
                migrationPlanApiMapper.toDomain(request),
                migrationPlanApiMapper.toDomainItems(request.items())
            )
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Migration plan created successfully", response));
    }

    @RequirePermission("billing.read")
    @GetMapping("/{id}")
    @Operation(summary = "Get a migration plan by ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Migration plan fetched successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Migration plan not found", content = @Content(
            schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)
        ))
    })
    public ResponseEntity<ApiResponse<MigrationPlanResponse>> getMigrationPlan(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(
            "Migration plan fetched successfully",
            migrationPlanApiMapper.toResponse(migrationPlanService.getMigrationPlan(id))
        ));
    }

    @RequirePermission("billing.read")
    @GetMapping
    @Operation(summary = "List migration plans with database-level filtering, pagination, and sorting")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Migration plans fetched successfully", content = @Content(
            schema = @Schema(implementation = MigrationPlanResponse.class),
            examples = @ExampleObject(value = """
                {
                  "success": true,
                  "message": "Migration plans fetched successfully",
                  "data": [],
                  "pagination": {
                    "page": 0,
                    "size": 20,
                    "totalElements": 0,
                    "totalPages": 0,
                    "hasNext": false
                  }
                }
                """)
        )),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid filter or pagination request", content = @Content(
            schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)
        ))
    })
    public ResponseEntity<ApiResponse<List<MigrationPlanResponse>>> listMigrationPlans(
        @RequestParam(required = false) UUID tenantId,
        @RequestParam(required = false) UUID subscriptionId,
        @RequestParam(required = false) UUID targetPlanVersionId,
        @RequestParam(required = false) MigrationPlanStatus status,
        @RequestParam(required = false) Instant dateFrom,
        @RequestParam(required = false) Instant dateTo,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "createdAt") String sort,
        @RequestParam(defaultValue = "desc") String direction
    ) {
        PageResult<MigrationPlan> result = migrationPlanService.listMigrationPlans(
            new MigrationPlanFilter(tenantId, subscriptionId, targetPlanVersionId, status, dateFrom, dateTo),
            PaginationUtils.clampPage(page),
            PaginationUtils.clampSize(size),
            sort,
            direction
        );
        List<MigrationPlanResponse> data = result.content().stream().map(migrationPlanApiMapper::toResponse).toList();
        PaginationMetadata pagination = new PaginationMetadata(
            result.page(),
            result.size(),
            result.totalElements(),
            result.totalPages(),
            result.hasNext()
        );
        return ResponseEntity.ok(ApiResponse.ok("Migration plans fetched successfully", data, pagination));
    }
}
