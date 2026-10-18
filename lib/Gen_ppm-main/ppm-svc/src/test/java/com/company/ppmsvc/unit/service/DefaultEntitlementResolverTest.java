package com.company.ppmsvc.unit.service;

import com.company.ppmsvc.planentitlement.model.ResolvedEntitlementResponse;
import com.company.ppmsvc.planentitlement.usecase.DefaultEntitlementResolver;
import com.company.ppmsvc.entitlement.model.EntitlementType;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.entitlement.model.Entitlement;
import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.planentitlement.model.PlanEntitlement;
import com.company.ppmsvc.entitlement.port.EntitlementRepositoryPort;
import com.company.ppmsvc.planentitlement.port.PlanEntitlementRepositoryPort;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultEntitlementResolver")
class DefaultEntitlementResolverTest {

    static final UUID ACTOR_ID        = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID PLAN_ID         = UUID.fromString("00000000-0000-0000-0000-000000000010");
    static final UUID ENTITLEMENT_ID1 = UUID.fromString("00000000-0000-0000-0000-000000000020");
    static final UUID ENTITLEMENT_ID2 = UUID.fromString("00000000-0000-0000-0000-000000000021");

    @Mock PlanRepositoryPort            planRepository;
    @Mock PlanEntitlementRepositoryPort planEntitlementRepository;
    @Mock EntitlementRepositoryPort     entitlementRepository;

    @InjectMocks DefaultEntitlementResolver resolver;

    // ── fixtures ──────────────────────────────────────────────────────────────

    private Plan buildPlan() {
        Instant now = Instant.now();
        return Plan.builder()
            .id(PLAN_ID).version(0L)
            .code("pro").slug("pro").name("Pro")
            .createdAt(now).updatedAt(now)
            .createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
            .build();
    }

    private Entitlement buildEntitlement(UUID id, String code, EntitlementType type) {
        Instant now = Instant.now();
        return Entitlement.builder()
            .id(id).version(0L).code(code).name(code).type(type).active(true)
            .createdAt(now).updatedAt(now).createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
            .build();
    }

    private PlanEntitlement buildAssignment(UUID entitlementId, String value) {
        return PlanEntitlement.builder()
            .id(UUID.randomUUID()).planId(PLAN_ID).entitlementId(entitlementId)
            .value(value).createdAt(Instant.now()).createdBy(ACTOR_ID)
            .build();
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("resolveEntitlements()")
    class ResolveEntitlements {

        @Test
        @DisplayName("success — returns resolved list with codes and values")
        void resolve_success() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(buildPlan()));

            Entitlement e1 = buildEntitlement(ENTITLEMENT_ID1, "max_users",       EntitlementType.QUOTA);
            Entitlement e2 = buildEntitlement(ENTITLEMENT_ID2, "sso_enabled",     EntitlementType.BOOLEAN);

            when(planEntitlementRepository.findByPlanId(PLAN_ID)).thenReturn(List.of(
                buildAssignment(ENTITLEMENT_ID1, "50"),
                buildAssignment(ENTITLEMENT_ID2, "false")));

            when(entitlementRepository.findAllById(Set.of(ENTITLEMENT_ID1, ENTITLEMENT_ID2)))
                .thenReturn(List.of(e1, e2));

            List<ResolvedEntitlementResponse> result = resolver.resolveEntitlements(PLAN_ID);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(ResolvedEntitlementResponse::code)
                .containsExactlyInAnyOrder("max_users", "sso_enabled");
            assertThat(result).extracting(ResolvedEntitlementResponse::value)
                .containsExactlyInAnyOrder("50", "false");
        }

        @Test
        @DisplayName("plan not found — throws PLAN_NOT_FOUND before any further query")
        void resolve_planNotFound_throws() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> resolver.resolveEntitlements(PLAN_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_NOT_FOUND));

            verify(planEntitlementRepository, never()).findByPlanId(PLAN_ID);
        }

        @Test
        @DisplayName("empty assignment set — returns empty list without calling findAllById")
        void resolve_noAssignments_returnsEmpty() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(buildPlan()));
            when(planEntitlementRepository.findByPlanId(PLAN_ID)).thenReturn(List.of());

            List<ResolvedEntitlementResponse> result = resolver.resolveEntitlements(PLAN_ID);

            assertThat(result).isEmpty();
            verify(entitlementRepository, never()).findAllById(org.mockito.ArgumentMatchers.anySet());
        }

        @Test
        @DisplayName("batch lookup — findAllById is called with all entitlement IDs (no N+1)")
        void resolve_batchLookup_singleInQuery() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(buildPlan()));
            when(planEntitlementRepository.findByPlanId(PLAN_ID)).thenReturn(List.of(
                buildAssignment(ENTITLEMENT_ID1, "100"),
                buildAssignment(ENTITLEMENT_ID2, "true")));
            when(entitlementRepository.findAllById(org.mockito.ArgumentMatchers.anySet()))
                .thenReturn(List.of(
                    buildEntitlement(ENTITLEMENT_ID1, "seats",        EntitlementType.QUOTA),
                    buildEntitlement(ENTITLEMENT_ID2, "custom_domain", EntitlementType.BOOLEAN)));

            resolver.resolveEntitlements(PLAN_ID);

            // findAllById must be called exactly once with both IDs (batch load, no N+1)
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Set<UUID>> captor = ArgumentCaptor.forClass(Set.class);
            verify(entitlementRepository).findAllById(captor.capture());
            assertThat(captor.getValue())
                .containsExactlyInAnyOrder(ENTITLEMENT_ID1, ENTITLEMENT_ID2);
        }

        @Test
        @DisplayName("duplicate entitlement codes are impossible — each assignment maps to a distinct entitlement ID")
        void resolve_entitlementIdIsUniquePerAssignment() {
            // The ppm_plan_entitlements table has a unique constraint on (plan_id, entitlement_id),
            // so the same entitlement cannot be assigned twice to the same plan. This test confirms
            // that the resolver treats entitlement IDs as distinct keys and produces distinct code entries.
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(buildPlan()));
            when(planEntitlementRepository.findByPlanId(PLAN_ID)).thenReturn(List.of(
                buildAssignment(ENTITLEMENT_ID1, "99"),
                buildAssignment(ENTITLEMENT_ID2, "1")));

            Entitlement e1 = buildEntitlement(ENTITLEMENT_ID1, "quota_a", EntitlementType.QUOTA);
            Entitlement e2 = buildEntitlement(ENTITLEMENT_ID2, "quota_b", EntitlementType.QUOTA);
            when(entitlementRepository.findAllById(org.mockito.ArgumentMatchers.anySet()))
                .thenReturn(List.of(e1, e2));

            List<ResolvedEntitlementResponse> result = resolver.resolveEntitlements(PLAN_ID);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(ResolvedEntitlementResponse::code).doesNotHaveDuplicates();
        }
    }
}
