// gen-tnt-starter/src/main/java/com/example/tnt_svc/web/ProvisioningController.java
package com.example.tnt_svc.web;

import com.example.tnt_svc.saga.ProvisioningSagaOrchestrator;
import com.example.tnt_svc.service.ProvisioningService;
import com.example.tnt_svc.web.dto.ErrorResponse;
import com.example.tnt_svc.web.dto.ProvisioningJobResponse;
import com.example.tnt_svc.web.dto.ProvisioningStepResponse;
import com.example.tnt_svc.web.dto.StepCallbackRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Provisioning", description = "Job/step introspection, manual retry, and the async-step " +
        "callback endpoint step targets call back into. All gated by X-Internal-Secret.")
@SecurityRequirement(name = "internalSecret")
@RestController
public class ProvisioningController {

    private final ProvisioningService provisioningService;
    private final ProvisioningSagaOrchestrator orchestrator;

    public ProvisioningController(ProvisioningService provisioningService, ProvisioningSagaOrchestrator orchestrator) {
        this.provisioningService = provisioningService;
        this.orchestrator = orchestrator;
    }

    @Operation(summary = "Get a provisioning job by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job found",
                    content = @Content(schema = @Schema(implementation = ProvisioningJobResponse.class))),
            @ApiResponse(responseCode = "404", description = "No job with this id",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/api/v1/provisioning/jobs/{id}")
    public ProvisioningJobResponse getJob(@PathVariable UUID id) {
        return ProvisioningJobResponse.from(provisioningService.getJob(id));
    }

    @Operation(summary = "List a job's steps in pipeline order")
    @ApiResponse(responseCode = "200", description = "Steps for the job",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ProvisioningStepResponse.class))))
    @GetMapping("/api/v1/provisioning/jobs/{id}/steps")
    public List<ProvisioningStepResponse> getSteps(@PathVariable UUID id) {
        return provisioningService.getSteps(id).stream().map(ProvisioningStepResponse::from).toList();
    }

    @Operation(summary = "Manually retry a failed job",
            description = "Only legal when the job is FAILED — the scheduler's own automatic retry pre-filters " +
                    "on that status, and this endpoint enforces the same rule for direct HTTP callers.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Retry accepted, job re-driven from its failed step"),
            @ApiResponse(responseCode = "409", description = "Job is not in FAILED status",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/api/v1/provisioning/jobs/{id}/retry")
    public ResponseEntity<Void> retry(@PathVariable UUID id) {
        provisioningService.manualRetry(id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @Operation(summary = "List all FAILED provisioning jobs", description = "Operator dashboard feed for jobs needing manual attention.")
    @ApiResponse(responseCode = "200", description = "Failed jobs",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ProvisioningJobResponse.class))))
    @GetMapping("/api/v1/provisioning/failed")
    public List<ProvisioningJobResponse> listFailed() {
        return provisioningService.listFailed().stream().map(ProvisioningJobResponse::from).toList();
    }

    @Operation(summary = "Async step completion callback",
            description = "Called by a step target after finishing async work, not by end users. token must match " +
                    "the job's callbackToken handed to the step in its original call payload. Rejected silently " +
                    "(no-op) if the job isn't IN_PROGRESS, the token mismatches, or the step isn't IN_PROGRESS — " +
                    "guards against late/duplicate at-least-once deliveries. Returns 409 on lock contention so the " +
                    "caller retries instead of losing the callback.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Callback processed (or safely ignored as stale/duplicate)"),
            @ApiResponse(responseCode = "409", description = "Lock contention — retry the callback",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/internal/provisioning/jobs/{jobId}/steps/{stepName}/callback")
    public ResponseEntity<Void> callback(
        @PathVariable UUID jobId,
        @PathVariable String stepName,
        @Valid @RequestBody StepCallbackRequest request
    ) {
        orchestrator.handleCallback(jobId, stepName, request.token(), request.success(), request.context(), request.error());
        return ResponseEntity.ok().build();
    }
}
