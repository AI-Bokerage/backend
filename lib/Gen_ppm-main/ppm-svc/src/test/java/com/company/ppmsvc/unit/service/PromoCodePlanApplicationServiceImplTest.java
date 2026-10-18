package com.company.ppmsvc.unit.service;

import com.company.ppmsvc.promocodeplan.usecase.PromoCodePlanApplicationServiceImpl;
import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.promocode.model.PromoCode;
import com.company.ppmsvc.promocodeplan.model.PromoCodePlan;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.promocodeplan.port.PromoCodePlanRepositoryPort;
import com.company.ppmsvc.promocode.port.PromoCodeRepositoryPort;
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
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PromoCodePlanApplicationServiceImpl")
class PromoCodePlanApplicationServiceImplTest {

    static final UUID ACTOR_ID      = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID PROMO_CODE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID PLAN_ID_1     = UUID.fromString("00000000-0000-0000-0000-000000000003");
    static final UUID PLAN_ID_2     = UUID.fromString("00000000-0000-0000-0000-000000000004");

    @Mock PromoCodeRepositoryPort     promoCodeRepository;
    @Mock PlanRepositoryPort          planRepository;
    @Mock PromoCodePlanRepositoryPort promoCodePlanRepository;

    @InjectMocks PromoCodePlanApplicationServiceImpl service;

    // ── fixtures ──────────────────────────────────────────────────────────────

    private void stubPromoCodeExists() {
        when(promoCodeRepository.findById(PROMO_CODE_ID))
            .thenReturn(Optional.of(Mockito.mock(PromoCode.class)));
    }

    private void stubPlanExists(UUID planId) {
        when(planRepository.findById(planId))
            .thenReturn(Optional.of(Mockito.mock(Plan.class)));
    }

    private PromoCodePlan buildMapping(UUID promoCodeId, UUID planId) {
        return PromoCodePlan.builder()
            .id(UUID.randomUUID())
            .promoCodeId(promoCodeId)
            .planId(planId)
            .createdAt(java.time.Instant.now())
            .createdBy(ACTOR_ID)
            .build();
    }

    // ── assignPlans ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("assignPlans()")
    class AssignPlans {

        @Test
        @DisplayName("success — assigns one plan and returns saved mappings")
        void assign_success() {
            stubPromoCodeExists();
            stubPlanExists(PLAN_ID_1);
            when(promoCodePlanRepository.exists(PROMO_CODE_ID, PLAN_ID_1)).thenReturn(false);
            PromoCodePlan mapping = buildMapping(PROMO_CODE_ID, PLAN_ID_1);
            when(promoCodePlanRepository.saveAll(anyList())).thenReturn(List.of(mapping));

            List<PromoCodePlan> result = service.assignPlans(ACTOR_ID, PROMO_CODE_ID, Set.of(PLAN_ID_1));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPlanId()).isEqualTo(PLAN_ID_1);
        }

        @Test
        @DisplayName("BR-P1 — unknown plan throws PLAN_NOT_FOUND")
        void assign_unknownPlan_throws() {
            stubPromoCodeExists();
            when(planRepository.findById(PLAN_ID_1)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.assignPlans(ACTOR_ID, PROMO_CODE_ID, Set.of(PLAN_ID_1)))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_NOT_FOUND));

            verify(promoCodePlanRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("BR-P2 — duplicate assignment throws PROMO_CODE_PLAN_ALREADY_ASSIGNED")
        void assign_duplicateAssignment_throws() {
            stubPromoCodeExists();
            stubPlanExists(PLAN_ID_1);
            when(promoCodePlanRepository.exists(PROMO_CODE_ID, PLAN_ID_1)).thenReturn(true);

            assertThatThrownBy(() -> service.assignPlans(ACTOR_ID, PROMO_CODE_ID, Set.of(PLAN_ID_1)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PROMO_CODE_PLAN_ALREADY_ASSIGNED));

            verify(promoCodePlanRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("BR-P3 — Set collapses duplicate plan IDs to single assignment")
        void assign_setCollapsedDuplicates() {
            stubPromoCodeExists();
            stubPlanExists(PLAN_ID_1);
            when(promoCodePlanRepository.exists(PROMO_CODE_ID, PLAN_ID_1)).thenReturn(false);
            when(promoCodePlanRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<List<PromoCodePlan>> captor = ArgumentCaptor.captor();
            service.assignPlans(ACTOR_ID, PROMO_CODE_ID, Set.of(PLAN_ID_1));  // Set already deduped by JVM

            verify(promoCodePlanRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).hasSize(1);
        }
    }

    // ── replacePlans ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("replacePlans()")
    class ReplacePlans {

        @Test
        @DisplayName("success — deletes existing restrictions and saves new ones")
        void replace_success() {
            stubPromoCodeExists();
            stubPlanExists(PLAN_ID_1);
            stubPlanExists(PLAN_ID_2);
            PromoCodePlan m1 = buildMapping(PROMO_CODE_ID, PLAN_ID_1);
            PromoCodePlan m2 = buildMapping(PROMO_CODE_ID, PLAN_ID_2);
            when(promoCodePlanRepository.saveAll(anyList())).thenReturn(List.of(m1, m2));

            List<PromoCodePlan> result = service.replacePlans(ACTOR_ID, PROMO_CODE_ID,
                Set.of(PLAN_ID_1, PLAN_ID_2));

            verify(promoCodePlanRepository).deleteAllByPromoCodeId(PROMO_CODE_ID);
            verify(promoCodePlanRepository).saveAll(anyList());
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("BR-P4 — invalid plan aborts before delete (abort-before-delete)")
        void replace_invalidPlanAbortsBeforeDelete() {
            stubPromoCodeExists();
            when(planRepository.findById(PLAN_ID_1)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.replacePlans(ACTOR_ID, PROMO_CODE_ID, Set.of(PLAN_ID_1)))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_NOT_FOUND));

            // Critically: delete must NOT have been called
            verify(promoCodePlanRepository, never()).deleteAllByPromoCodeId(any());
            verify(promoCodePlanRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("BR-P4 — atomicity: delete and saveAll execute in same transaction scope")
        void replace_atomicDeleteThenInsert() {
            stubPromoCodeExists();
            stubPlanExists(PLAN_ID_1);
            when(promoCodePlanRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            service.replacePlans(ACTOR_ID, PROMO_CODE_ID, Set.of(PLAN_ID_1));

            // Verify delete was called before saveAll (order matters in @Transactional)
            var inOrder = Mockito.inOrder(promoCodePlanRepository);
            inOrder.verify(promoCodePlanRepository).deleteAllByPromoCodeId(PROMO_CODE_ID);
            inOrder.verify(promoCodePlanRepository).saveAll(anyList());
        }
    }

    // ── getRestrictedPlans ────────────────────────────────────────────────────

    @Nested
    @DisplayName("getRestrictedPlans()")
    class GetRestrictedPlans {

        @Test
        @DisplayName("returns all mappings for the given promo code")
        void getRestrictedPlans_returnsMappings() {
            PromoCodePlan m1 = buildMapping(PROMO_CODE_ID, PLAN_ID_1);
            PromoCodePlan m2 = buildMapping(PROMO_CODE_ID, PLAN_ID_2);
            when(promoCodePlanRepository.findByPromoCodeId(PROMO_CODE_ID))
                .thenReturn(List.of(m1, m2));

            List<PromoCodePlan> result = service.getRestrictedPlans(PROMO_CODE_ID);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(PromoCodePlan::getPlanId)
                .containsExactlyInAnyOrder(PLAN_ID_1, PLAN_ID_2);
        }
    }

    // ── removePlan ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("removePlan()")
    class RemovePlan {

        @Test
        @DisplayName("success — deletes the mapping when it exists")
        void remove_success() {
            when(promoCodePlanRepository.exists(PROMO_CODE_ID, PLAN_ID_1)).thenReturn(true);

            service.removePlan(PROMO_CODE_ID, PLAN_ID_1);

            verify(promoCodePlanRepository).delete(PROMO_CODE_ID, PLAN_ID_1);
        }

        @Test
        @DisplayName("BR-P7 — mapping not found throws PROMO_CODE_PLAN_MAPPING_NOT_FOUND")
        void remove_mappingNotFound_throws() {
            when(promoCodePlanRepository.exists(PROMO_CODE_ID, PLAN_ID_1)).thenReturn(false);

            assertThatThrownBy(() -> service.removePlan(PROMO_CODE_ID, PLAN_ID_1))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PROMO_CODE_PLAN_MAPPING_NOT_FOUND));

            verify(promoCodePlanRepository, never()).delete(any(), any());
        }
    }
}
