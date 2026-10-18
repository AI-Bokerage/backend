package com.company.ppmsvc.unit.service;

import com.company.ppmsvc.planentitlement.model.ResolvedEntitlementResponse;
import com.company.ppmsvc.planentitlement.usecase.PlanEntitlementApplicationServiceImpl;
import com.company.ppmsvc.planentitlement.usecase.EntitlementResolver;
import com.company.ppmsvc.entitlement.model.EntitlementType;
import com.company.ppmsvc.exception.BusinessException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlanEntitlementApplicationServiceImpl")
class PlanEntitlementApplicationServiceImplTest {

    static final UUID ACTOR_ID        = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID PLAN_ID         = UUID.fromString("00000000-0000-0000-0000-000000000010");
    static final UUID ENTITLEMENT_ID1 = UUID.fromString("00000000-0000-0000-0000-000000000020");
    static final UUID ENTITLEMENT_ID2 = UUID.fromString("00000000-0000-0000-0000-000000000021");

    @Mock PlanRepositoryPort            planRepository;
    @Mock EntitlementRepositoryPort     entitlementRepository;
    @Mock PlanEntitlementRepositoryPort planEntitlementRepository;
    @Mock EntitlementResolver           resolver;

    @InjectMocks PlanEntitlementApplicationServiceImpl service;

    // ── fixtures ──────────────────────────────────────────────────────────────

    private Plan buildPlan() {
        Instant now = Instant.now();
        return Plan.builder()
            .id(PLAN_ID).version(0L)
            .code("starter").slug("starter").name("Starter")
            .createdAt(now).updatedAt(now)
            .createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
            .build();
    }

    private Entitlement buildEntitlement(UUID id, EntitlementType type) {
        Instant now = Instant.now();
        return Entitlement.builder()
            .id(id).version(0L)
            .code("ent_" + id.toString().substring(0, 8))
            .name("Entitlement").type(type).active(true)
            .createdAt(now).updatedAt(now)
            .createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
            .build();
    }

    private PlanEntitlement buildAssignment(UUID entitlementId) {
        return PlanEntitlement.builder()
            .id(UUID.randomUUID())
            .planId(PLAN_ID).entitlementId(entitlementId)
            .value("false")
            .createdAt(Instant.now()).createdBy(ACTOR_ID)
            .build();
    }

    private ResolvedEntitlementResponse resolved(String code, String value) {
        return new ResolvedEntitlementResponse(code, "Entitlement", EntitlementType.QUOTA, value);
    }

    // ── assignEntitlements ────────────────────────────────────────────────────

    @Nested
    @DisplayName("assignEntitlements()")
    class AssignEntitlements {

        @Test
        @DisplayName("success — saves assignments and returns resolver output")
        void assign_success() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(buildPlan()));
            Entitlement e1 = buildEntitlement(ENTITLEMENT_ID1, EntitlementType.BOOLEAN);
            when(entitlementRepository.findById(ENTITLEMENT_ID1)).thenReturn(Optional.of(e1));
            when(planEntitlementRepository.exists(PLAN_ID, ENTITLEMENT_ID1)).thenReturn(false);
            when(planEntitlementRepository.saveAll(anyList())).thenReturn(List.of(buildAssignment(ENTITLEMENT_ID1)));
            when(resolver.resolveEntitlements(PLAN_ID)).thenReturn(List.of(resolved(e1.getCode(), "false")));

            List<ResolvedEntitlementResponse> result = service.assignEntitlements(
                ACTOR_ID, PLAN_ID, Set.of(ENTITLEMENT_ID1));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).code()).isEqualTo(e1.getCode());
            verify(planEntitlementRepository).saveAll(anyList());
        }

        @Test
        @DisplayName("BR-P1 — plan not found throws PLAN_NOT_FOUND")
        void assign_planMissing_throws() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.assignEntitlements(
                    ACTOR_ID, PLAN_ID, Set.of(ENTITLEMENT_ID1)))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_NOT_FOUND));

            verify(planEntitlementRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("BR-P2 — unknown entitlement throws ENTITLEMENT_NOT_FOUND")
        void assign_entitlementMissing_throws() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(buildPlan()));
            when(entitlementRepository.findById(ENTITLEMENT_ID1)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.assignEntitlements(
                    ACTOR_ID, PLAN_ID, Set.of(ENTITLEMENT_ID1)))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.ENTITLEMENT_NOT_FOUND));

            verify(planEntitlementRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("BR-P3 — duplicate assignment throws PLAN_ENTITLEMENT_ALREADY_ASSIGNED")
        void assign_duplicate_throws() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(buildPlan()));
            when(entitlementRepository.findById(ENTITLEMENT_ID1))
                .thenReturn(Optional.of(buildEntitlement(ENTITLEMENT_ID1, EntitlementType.BOOLEAN)));
            when(planEntitlementRepository.exists(PLAN_ID, ENTITLEMENT_ID1)).thenReturn(true);

            assertThatThrownBy(() -> service.assignEntitlements(
                    ACTOR_ID, PLAN_ID, Set.of(ENTITLEMENT_ID1)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_ENTITLEMENT_ALREADY_ASSIGNED));

            verify(planEntitlementRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("BR-P4 — Set collapses duplicate IDs in request to single assignment")
        void assign_idempotentRequest_singleAssignment() {
            // Set<UUID> naturally deduplicates before it reaches the service
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(buildPlan()));
            Entitlement e1 = buildEntitlement(ENTITLEMENT_ID1, EntitlementType.QUOTA);
            when(entitlementRepository.findById(ENTITLEMENT_ID1)).thenReturn(Optional.of(e1));
            when(planEntitlementRepository.exists(PLAN_ID, ENTITLEMENT_ID1)).thenReturn(false);
            when(planEntitlementRepository.saveAll(anyList())).thenReturn(List.of());
            when(resolver.resolveEntitlements(PLAN_ID)).thenReturn(List.of());

            service.assignEntitlements(ACTOR_ID, PLAN_ID, Set.of(ENTITLEMENT_ID1));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<PlanEntitlement>> captor = ArgumentCaptor.forClass(List.class);
            verify(planEntitlementRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).hasSize(1);
        }
    }

    // ── replaceEntitlements ───────────────────────────────────────────────────

    @Nested
    @DisplayName("replaceEntitlements()")
    class ReplaceEntitlements {

        @Test
        @DisplayName("success — deletes existing and saves new assignments")
        void replace_success() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(buildPlan()));
            Entitlement e1 = buildEntitlement(ENTITLEMENT_ID1, EntitlementType.BOOLEAN);
            Entitlement e2 = buildEntitlement(ENTITLEMENT_ID2, EntitlementType.QUOTA);
            when(entitlementRepository.findById(ENTITLEMENT_ID1)).thenReturn(Optional.of(e1));
            when(entitlementRepository.findById(ENTITLEMENT_ID2)).thenReturn(Optional.of(e2));
            when(planEntitlementRepository.saveAll(anyList())).thenReturn(List.of());
            when(resolver.resolveEntitlements(PLAN_ID)).thenReturn(List.of(
                resolved(e1.getCode(), "false"),
                resolved(e2.getCode(), "0")));

            List<ResolvedEntitlementResponse> result = service.replaceEntitlements(
                ACTOR_ID, PLAN_ID, Set.of(ENTITLEMENT_ID1, ENTITLEMENT_ID2));

            assertThat(result).hasSize(2);
            verify(planEntitlementRepository).deleteAllByPlanId(PLAN_ID);
            verify(planEntitlementRepository).saveAll(anyList());
        }

        @Test
        @DisplayName("abort-before-delete — invalid entitlement aborts before deleteAllByPlanId is called")
        void replace_invalidEntitlement_abortsBeforeDelete() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(buildPlan()));
            when(entitlementRepository.findById(ENTITLEMENT_ID1)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.replaceEntitlements(
                    ACTOR_ID, PLAN_ID, Set.of(ENTITLEMENT_ID1)))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.ENTITLEMENT_NOT_FOUND));

            // deleteAllByPlanId must NOT have been called — original mappings survive
            verify(planEntitlementRepository, never()).deleteAllByPlanId(any());
            verify(planEntitlementRepository, never()).saveAll(anyList());
        }
    }

    // ── getPlanEntitlements ───────────────────────────────────────────────────

    @Nested
    @DisplayName("getPlanEntitlements()")
    class GetPlanEntitlements {

        @Test
        @DisplayName("returns resolver output for a valid plan")
        void list_returnsResolvedEntitlements() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(buildPlan()));
            when(resolver.resolveEntitlements(PLAN_ID)).thenReturn(
                List.of(resolved("max_users", "100")));

            List<ResolvedEntitlementResponse> result = service.getPlanEntitlements(PLAN_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).code()).isEqualTo("max_users");
        }

        @Test
        @DisplayName("plan not found throws PLAN_NOT_FOUND")
        void list_planMissing_throws() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getPlanEntitlements(PLAN_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_NOT_FOUND));
        }
    }

    // ── removeEntitlement ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("removeEntitlement()")
    class RemoveEntitlement {

        @Test
        @DisplayName("success — only the mapping row is deleted")
        void remove_success() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(buildPlan()));
            when(planEntitlementRepository.exists(PLAN_ID, ENTITLEMENT_ID1)).thenReturn(true);

            service.removeEntitlement(PLAN_ID, ENTITLEMENT_ID1);

            verify(planEntitlementRepository).delete(PLAN_ID, ENTITLEMENT_ID1);
            verify(entitlementRepository, never()).softDelete(any(), any());
        }

        @Test
        @DisplayName("BR-P7 — mapping not found throws PLAN_ENTITLEMENT_MAPPING_NOT_FOUND")
        void remove_mappingNotFound_throws() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(buildPlan()));
            when(planEntitlementRepository.exists(PLAN_ID, ENTITLEMENT_ID1)).thenReturn(false);

            assertThatThrownBy(() -> service.removeEntitlement(PLAN_ID, ENTITLEMENT_ID1))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_ENTITLEMENT_MAPPING_NOT_FOUND));

            verify(planEntitlementRepository, never()).delete(any(), any());
        }
    }
}
