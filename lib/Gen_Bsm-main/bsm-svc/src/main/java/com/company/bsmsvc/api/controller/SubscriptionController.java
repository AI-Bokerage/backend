package com.company.bsmsvc.api.controller;

import com.company.bsmsvc.api.dto.request.CancelSubscriptionByIdRequest;
import com.company.bsmsvc.api.dto.request.CancelSubscriptionRequest;
import com.company.bsmsvc.api.dto.request.PauseSubscriptionRequest;
import com.company.bsmsvc.api.dto.request.ResumeSubscriptionRequest;
import com.company.bsmsvc.api.dto.request.ScheduleDowngradeRequest;
import com.company.bsmsvc.api.dto.response.ApiResponse;
import com.company.bsmsvc.api.dto.response.PaginationMetadata;
import com.company.bsmsvc.api.dto.response.SubscriptionEventResponse;
import com.company.bsmsvc.api.dto.response.SubscriptionHistoryResponse;
import com.company.bsmsvc.api.dto.response.SubscriptionResponse;
import com.company.bsmsvc.api.dto.response.SubscriptionScheduleResponse;
import com.company.bsmsvc.api.mapper.SubscriptionApiMapper;
import com.company.bsmsvc.api.mapper.SubscriptionQueryApiMapper;
import com.company.bsmsvc.application.service.SubscriptionService;
import com.company.bsmsvc.application.util.PaginationUtils;
import com.company.bsmsvc.domain.enums.SubscriptionEventType;
import com.company.bsmsvc.domain.enums.SubscriptionHistoryAction;
import com.company.bsmsvc.domain.enums.SubscriptionScheduleActionType;
import com.company.bsmsvc.domain.enums.SubscriptionScheduleStatus;
import com.company.bsmsvc.domain.model.PageResult;
import com.company.bsmsvc.domain.model.SubscriptionEvent;
import com.company.bsmsvc.domain.model.SubscriptionEventFilter;
import com.company.bsmsvc.domain.model.SubscriptionHistory;
import com.company.bsmsvc.domain.model.SubscriptionHistoryFilter;
import com.company.bsmsvc.domain.model.SubscriptionSchedule;
import com.company.bsmsvc.domain.model.SubscriptionScheduleFilter;
import io.cpms.common.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bsm/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Subscription Management", description = "Subscription lifecycle endpoints")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final SubscriptionApiMapper subscriptionApiMapper;
    private final SubscriptionQueryApiMapper subscriptionQueryApiMapper;

    @RequirePermission("billing.read")
    @GetMapping("/current")
    @Operation(summary = "Get current subscription for tenant")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Current subscription fetched successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Subscription not found", content = @Content(schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)))
    public ResponseEntity<ApiResponse<SubscriptionResponse>> getCurrentSubscription(@RequestParam UUID tenantId) {
        SubscriptionResponse response = subscriptionApiMapper.toResponse(subscriptionService.getCurrentSubscription(tenantId));
        return ResponseEntity.ok(ApiResponse.ok("Current subscription fetched successfully", response));
    }

    @RequirePermission("billing.write")
    @PostMapping("/cancel")
    @Operation(summary = "Cancel subscription by tenant (legacy API)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Subscription cancellation processed successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Subscription not found", content = @Content(schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)))
    public ResponseEntity<ApiResponse<SubscriptionResponse>> cancelSubscription(@Valid @RequestBody CancelSubscriptionRequest request) {
        SubscriptionResponse response = subscriptionApiMapper.toResponse(
            subscriptionService.cancelSubscription(
                request.tenantId(),
                request.reason(),
                request.performedBy(),
                request.cancelImmediately()
            )
        );
        return ResponseEntity.ok(ApiResponse.ok("Subscription cancellation processed successfully", response));
    }

    @RequirePermission("billing.write")
    @PostMapping("/{id}/pause")
    @Operation(summary = "Pause subscription")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Subscription paused successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Subscription not found", content = @Content(schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Tenant ownership conflict", content = @Content(schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)))
    public ResponseEntity<ApiResponse<SubscriptionResponse>> pauseSubscription(
        @PathVariable UUID id,
        @Valid @RequestBody PauseSubscriptionRequest request
    ) {
        SubscriptionResponse response = subscriptionApiMapper.toResponse(
            subscriptionService.pauseSubscription(id, request.tenantId(), request.reason(), request.performedBy())
        );
        return ResponseEntity.ok(ApiResponse.ok("Subscription paused successfully", response));
    }

    @RequirePermission("billing.write")
    @PostMapping("/{id}/resume")
    @Operation(summary = "Resume subscription")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Subscription resumed successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Subscription not found", content = @Content(schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Tenant ownership conflict", content = @Content(schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)))
    public ResponseEntity<ApiResponse<SubscriptionResponse>> resumeSubscription(
        @PathVariable UUID id,
        @Valid @RequestBody ResumeSubscriptionRequest request
    ) {
        SubscriptionResponse response = subscriptionApiMapper.toResponse(
            subscriptionService.resumeSubscription(id, request.tenantId(), request.reason(), request.performedBy())
        );
        return ResponseEntity.ok(ApiResponse.ok("Subscription resumed successfully", response));
    }

    @RequirePermission("billing.write")
    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel subscription by id")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Subscription cancellation processed successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Subscription not found", content = @Content(schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Tenant ownership or business rule conflict", content = @Content(schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)))
    public ResponseEntity<ApiResponse<SubscriptionResponse>> cancelSubscriptionById(
        @PathVariable UUID id,
        @Valid @RequestBody CancelSubscriptionByIdRequest request
    ) {
        SubscriptionResponse response = subscriptionApiMapper.toResponse(
            subscriptionService.cancelSubscriptionById(id, request.tenantId(), request.reason(), request.performedBy(), request.cancelAtPeriodEnd())
        );
        return ResponseEntity.ok(ApiResponse.ok("Subscription cancellation processed successfully", response));
    }

    @RequirePermission("billing.write")
    @PostMapping("/{id}/downgrade")
    @Operation(summary = "Schedule subscription downgrade")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Subscription downgrade scheduled successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Subscription or plan version not found", content = @Content(schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Pending downgrade already exists or tenant ownership conflict", content = @Content(schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)))
    public ResponseEntity<ApiResponse<SubscriptionScheduleResponse>> scheduleDowngrade(
        @PathVariable UUID id,
        @Valid @RequestBody ScheduleDowngradeRequest request
    ) {
        SubscriptionScheduleResponse response = subscriptionQueryApiMapper.toResponse(
            subscriptionService.scheduleDowngrade(id, request.tenantId(), request.targetPlanVersionId(), request.effectiveAt(), request.reason(), request.createdBy())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Subscription downgrade scheduled successfully", response));
    }

    @RequirePermission("billing.write")
    @DeleteMapping("/{id}/downgrade")
    @Operation(summary = "Cancel scheduled downgrade")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Subscription downgrade cancelled successfully")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Subscription not found", content = @Content(schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "No pending downgrade or tenant ownership conflict", content = @Content(schema = @Schema(implementation = com.company.bsmsvc.api.dto.response.ErrorResponse.class)))
    public ResponseEntity<ApiResponse<SubscriptionScheduleResponse>> cancelDowngrade(
        @PathVariable UUID id,
        @RequestParam UUID tenantId,
        @RequestParam String performedBy,
        @RequestParam(required = false) String reason
    ) {
        SubscriptionScheduleResponse response = subscriptionQueryApiMapper.toResponse(
            subscriptionService.cancelDowngrade(id, tenantId, reason, performedBy)
        );
        return ResponseEntity.ok(ApiResponse.ok("Subscription downgrade cancelled successfully", response));
    }

    @RequirePermission("billing.read")
    @GetMapping("/events")
    @Operation(summary = "List subscription events")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Subscription events fetched successfully")
    public ResponseEntity<ApiResponse<List<SubscriptionEventResponse>>> listEvents(
        @RequestParam(required = false) UUID tenantId,
        @RequestParam(required = false) UUID subscriptionId,
        @RequestParam(required = false) SubscriptionEventType eventType,
        @RequestParam(required = false) Instant dateFrom,
        @RequestParam(required = false) Instant dateTo,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "occurredAt") String sort,
        @RequestParam(defaultValue = "desc") String direction
    ) {
        PageResult<SubscriptionEvent> result = subscriptionService.getSubscriptionEvents(
            new SubscriptionEventFilter(tenantId, subscriptionId, eventType, dateFrom, dateTo),
            PaginationUtils.clampPage(page),
            PaginationUtils.clampSize(size),
            sort,
            direction
        );
        List<SubscriptionEventResponse> data = result.content().stream().map(subscriptionQueryApiMapper::toResponse).toList();
        PaginationMetadata pagination = new PaginationMetadata(result.page(), result.size(), result.totalElements(), result.totalPages(), result.hasNext());
        return ResponseEntity.ok(ApiResponse.ok("Subscription events fetched successfully", data, pagination));
    }

    @RequirePermission("billing.read")
    @GetMapping("/history")
    @Operation(summary = "List subscription history")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Subscription history fetched successfully")
    public ResponseEntity<ApiResponse<List<SubscriptionHistoryResponse>>> listHistory(
        @RequestParam(required = false) UUID tenantId,
        @RequestParam(required = false) UUID subscriptionId,
        @RequestParam(required = false) SubscriptionHistoryAction action,
        @RequestParam(required = false) Instant dateFrom,
        @RequestParam(required = false) Instant dateTo,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "occurredAt") String sort,
        @RequestParam(defaultValue = "desc") String direction
    ) {
        PageResult<SubscriptionHistory> result = subscriptionService.getSubscriptionHistory(
            new SubscriptionHistoryFilter(tenantId, subscriptionId, action, dateFrom, dateTo),
            PaginationUtils.clampPage(page),
            PaginationUtils.clampSize(size),
            sort,
            direction
        );
        List<SubscriptionHistoryResponse> data = result.content().stream().map(subscriptionQueryApiMapper::toResponse).toList();
        PaginationMetadata pagination = new PaginationMetadata(result.page(), result.size(), result.totalElements(), result.totalPages(), result.hasNext());
        return ResponseEntity.ok(ApiResponse.ok("Subscription history fetched successfully", data, pagination));
    }

    @RequirePermission("billing.read")
    @GetMapping("/schedules")
    @Operation(summary = "List subscription schedules")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Subscription schedules fetched successfully")
    public ResponseEntity<ApiResponse<List<SubscriptionScheduleResponse>>> listSchedules(
        @RequestParam(required = false) UUID tenantId,
        @RequestParam(required = false) UUID subscriptionId,
        @RequestParam(required = false) SubscriptionScheduleStatus status,
        @RequestParam(required = false) SubscriptionScheduleActionType actionType,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "effectiveAt") String sort,
        @RequestParam(defaultValue = "desc") String direction
    ) {
        PageResult<SubscriptionSchedule> result = subscriptionService.getSubscriptionSchedules(
            new SubscriptionScheduleFilter(tenantId, subscriptionId, status, actionType),
            PaginationUtils.clampPage(page),
            PaginationUtils.clampSize(size),
            sort,
            direction
        );
        List<SubscriptionScheduleResponse> data = result.content().stream().map(subscriptionQueryApiMapper::toResponse).toList();
        PaginationMetadata pagination = new PaginationMetadata(result.page(), result.size(), result.totalElements(), result.totalPages(), result.hasNext());
        return ResponseEntity.ok(ApiResponse.ok("Subscription schedules fetched successfully", data, pagination));
    }
}
