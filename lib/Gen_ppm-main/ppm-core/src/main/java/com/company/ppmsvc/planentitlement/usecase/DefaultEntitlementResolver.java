package com.company.ppmsvc.planentitlement.usecase;

import com.company.ppmsvc.planentitlement.model.ResolvedEntitlementResponse;
import com.company.ppmsvc.planentitlement.usecase.EntitlementResolver;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.entitlement.model.Entitlement;
import com.company.ppmsvc.planentitlement.model.PlanEntitlement;
import com.company.ppmsvc.entitlement.port.EntitlementRepositoryPort;
import com.company.ppmsvc.planentitlement.port.PlanEntitlementRepositoryPort;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link EntitlementResolver}.
 *
 * <p>Resolution algorithm (two queries total — no N+1):
 * <ol>
 *   <li>Verify the plan exists.</li>
 *   <li>Load all {@code PlanEntitlement} rows for the plan in one query.</li>
 *   <li>Collect the distinct entitlement IDs.</li>
 *   <li>Batch-load all referenced {@link Entitlement} definitions in a single
 *       {@code WHERE id IN (...)} query via {@link EntitlementRepositoryPort#findAllById}.</li>
 *   <li>Index entitlements by ID and zip with assignment values.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultEntitlementResolver implements EntitlementResolver {

    private final PlanRepositoryPort        planRepository;
    private final PlanEntitlementRepositoryPort planEntitlementRepository;
    private final EntitlementRepositoryPort entitlementRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ResolvedEntitlementResponse> resolveEntitlements(UUID planId) {
        // Step 1: verify plan exists
        planRepository.findById(planId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.PLAN_NOT_FOUND, "Plan not found: " + planId));

        // Step 2: load all assignments for the plan — single query
        List<PlanEntitlement> assignments = planEntitlementRepository.findByPlanId(planId);

        if (assignments.isEmpty()) {
            return List.of();
        }

        // Step 3: collect distinct entitlement IDs — Set prevents duplicate lookups
        Set<UUID> entitlementIds = assignments.stream()
            .map(PlanEntitlement::getEntitlementId)
            .collect(Collectors.toSet());

        // Step 4: batch-load all entitlement definitions — single IN query, no N+1
        Map<UUID, Entitlement> entitlementById = entitlementRepository.findAllById(entitlementIds)
            .stream()
            .collect(Collectors.toMap(Entitlement::getId, Function.identity()));

        // Step 5: zip assignment values with entitlement codes
        return assignments.stream()
            .map(assignment -> {
                Entitlement ent = entitlementById.get(assignment.getEntitlementId());
                if (ent == null) {
                    log.warn("Entitlement {} referenced by plan {} not found during resolution — skipped",
                        assignment.getEntitlementId(), planId);
                    return null;
                }
                return new ResolvedEntitlementResponse(
                    ent.getCode(), ent.getName(), ent.getType(), assignment.getValue());
            })
            .filter(r -> r != null)
            .toList();
    }
}
