package com.company.bsmsvc.api.controller;

import com.company.bsmsvc.api.dto.response.ApiResponse;
import com.company.bsmsvc.api.dto.response.DunningAttemptResponse;
import com.company.bsmsvc.api.dto.response.DunningStatusResponse;
import com.company.bsmsvc.api.dto.response.ErrorResponse;
import com.company.bsmsvc.api.mapper.DunningApiMapper;
import com.company.bsmsvc.application.service.DunningService;
import com.company.bsmsvc.domain.model.DunningAttempt;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import io.cpms.common.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bsm/dunning")
@RequiredArgsConstructor
@Tag(name = "Dunning", description = "Dunning state and manual retry management")
public class DunningController {

    private final DunningService dunningService;
    private final DunningApiMapper mapper;
    private final SubscriptionRepositoryPort subscriptionRepository;

    @RequirePermission("billing.read")
    @GetMapping("/{subscriptionId}")
    @Operation(summary = "Get dunning status and all attempts for a subscription")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Dunning status returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Subscription not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<DunningStatusResponse>> getDunningStatus(@PathVariable UUID subscriptionId) {
        Subscription sub = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new com.company.bsmsvc.domain.exception.SubscriptionNotFoundException("Subscription not found: " + subscriptionId));
        List<DunningAttempt> attempts = dunningService.getAttempts(subscriptionId);
        return ResponseEntity.ok(ApiResponse.ok("Dunning status fetched", mapper.toDunningStatus(sub, attempts)));
    }

    @RequirePermission("billing.admin")
    @PostMapping("/{subscriptionId}/retry")
    @Operation(summary = "Manually trigger a dunning retry for a subscription")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Retry triggered"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "No retryable attempt",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<String>> manualRetry(@PathVariable UUID subscriptionId) {
        dunningService.manualRetry(subscriptionId);
        return ResponseEntity.ok(ApiResponse.ok("Manual retry triggered", "OK"));
    }

    @RequirePermission("billing.read")
    @GetMapping("/attempts")
    @Operation(summary = "List dunning attempts for a subscription with pagination")
    public ResponseEntity<ApiResponse<List<DunningAttemptResponse>>> getAttempts(
        @RequestParam UUID subscriptionId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        List<DunningAttempt> attempts = dunningService.getAttempts(subscriptionId);
        int from = Math.min(page * size, attempts.size());
        int to = Math.min(from + size, attempts.size());
        List<DunningAttemptResponse> responses = attempts.subList(from, to).stream().map(mapper::toAttemptResponse).toList();
        return ResponseEntity.ok(ApiResponse.ok("Dunning attempts fetched", responses));
    }
}
