package com.company.ppmsvc.unit.service;

import com.company.ppmsvc.plan.usecase.PlanApplicationServiceImpl;
import com.company.ppmsvc.plan.model.PlanVisibility;
import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlanApplicationService")
class PlanApplicationServiceImplTest {

    static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID PLAN_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock PlanRepositoryPort planRepository;

    @InjectMocks PlanApplicationServiceImpl service;

    // ── helpers ───────────────────────────────────────────────────────────────

    private Plan buildPlan(String code, String slug, String name) {
        Instant now = Instant.now();
        return Plan.builder()
            .id(PLAN_ID)
            .version(0L)
            .code(code)
            .slug(slug)
            .name(name)
            .visibility(PlanVisibility.PUBLIC)
            .trialDays(0)
            .active(true)
            .createdAt(now)
            .updatedAt(now)
            .createdBy(ACTOR_ID)
            .updatedBy(ACTOR_ID)
            .build();
    }

    // ── createPlan ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createPlan()")
    class CreatePlan {

        @Test
        @DisplayName("success — slug generated as PLN-0001, saved with correct fields")
        void createPlan_success() {
            Plan saved = buildPlan("STARTER", "PLN-0001", "Starter");
            when(planRepository.existsByCode("STARTER")).thenReturn(false);
            when(planRepository.nextSlugSequenceValue()).thenReturn(1L);
            when(planRepository.save(any())).thenReturn(saved);

            Plan result = service.createPlan(ACTOR_ID, "STARTER", "Starter", "Best value", "desc",
                PlanVisibility.PUBLIC, 14, true, null);

            assertThat(result.getCode()).isEqualTo("STARTER");
            assertThat(result.getSlug()).isEqualTo("PLN-0001");

            ArgumentCaptor<Plan> captor = ArgumentCaptor.forClass(Plan.class);
            verify(planRepository).save(captor.capture());
            Plan captured = captor.getValue();
            assertThat(captured.getCode()).isEqualTo("STARTER");
            assertThat(captured.getSlug()).isEqualTo("PLN-0001");
            assertThat(captured.getVisibility()).isEqualTo(PlanVisibility.PUBLIC);
            assertThat(captured.getTrialDays()).isEqualTo(14);
            assertThat(captured.isActive()).isTrue();
            assertThat(captured.getCreatedBy()).isEqualTo(ACTOR_ID);
            assertThat(captured.getUpdatedBy()).isEqualTo(ACTOR_ID);
        }

        @Test
        @DisplayName("slug format — sequence value 42 produces PLN-0042")
        void createPlan_slugFormat_zerosPadded() {
            Plan saved = buildPlan("GROWTH", "PLN-0042", "Growth");
            when(planRepository.existsByCode("GROWTH")).thenReturn(false);
            when(planRepository.nextSlugSequenceValue()).thenReturn(42L);
            when(planRepository.save(any())).thenReturn(saved);

            service.createPlan(ACTOR_ID, "GROWTH", "Growth", null, null,
                PlanVisibility.PUBLIC, null, null, null);

            ArgumentCaptor<Plan> captor = ArgumentCaptor.forClass(Plan.class);
            verify(planRepository).save(captor.capture());
            assertThat(captor.getValue().getSlug()).isEqualTo("PLN-0042");
        }

        @Test
        @DisplayName("BR-3: trialDays defaults to 0 when not supplied")
        void createPlan_trialDaysDefaultsToZero() {
            Plan saved = buildPlan("TRIAL", "PLN-0001", "Trial");
            when(planRepository.existsByCode("TRIAL")).thenReturn(false);
            when(planRepository.nextSlugSequenceValue()).thenReturn(1L);
            when(planRepository.save(any())).thenReturn(saved);

            service.createPlan(ACTOR_ID, "TRIAL", "Trial", null, null,
                PlanVisibility.PUBLIC, null, null, null);

            ArgumentCaptor<Plan> captor = ArgumentCaptor.forClass(Plan.class);
            verify(planRepository).save(captor.capture());
            assertThat(captor.getValue().getTrialDays()).isEqualTo(0);
            assertThat(captor.getValue().isActive()).isTrue();
        }

        @Test
        @DisplayName("BR-1: duplicate code throws PLAN_CODE_ALREADY_EXISTS before sequence is called")
        void createPlan_duplicateCode_throws() {
            when(planRepository.existsByCode("STARTER")).thenReturn(true);

            assertThatThrownBy(() -> service.createPlan(ACTOR_ID, "STARTER", "Starter 2", null, null,
                PlanVisibility.PUBLIC, null, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_CODE_ALREADY_EXISTS));

            verify(planRepository, never()).nextSlugSequenceValue();
            verify(planRepository, never()).save(any());
        }
    }

    // ── updatePlan ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updatePlan()")
    class UpdatePlan {

        @Test
        @DisplayName("success — editable fields updated, code and slug immutable")
        void updatePlan_success() {
            Plan existing = buildPlan("STARTER", "PLN-0001", "Starter");
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(existing));

            Plan saved = Plan.builder()
                .id(PLAN_ID).version(1L)
                .code("STARTER").slug("PLN-0001").name("Starter Pro")
                .visibility(PlanVisibility.PRIVATE).trialDays(7).active(false)
                .createdAt(existing.getCreatedAt()).updatedAt(Instant.now())
                .createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
                .build();
            when(planRepository.save(any())).thenReturn(saved);

            service.updatePlan(ACTOR_ID, PLAN_ID, "Starter Pro", null, null,
                PlanVisibility.PRIVATE, 7, false, null);

            ArgumentCaptor<Plan> captor = ArgumentCaptor.forClass(Plan.class);
            verify(planRepository).save(captor.capture());
            Plan captured = captor.getValue();
            assertThat(captured.getCode()).isEqualTo("STARTER");       // BR-4: immutable
            assertThat(captured.getSlug()).isEqualTo("PLN-0001");      // BR-5: immutable
            assertThat(captured.getName()).isEqualTo("Starter Pro");
            assertThat(captured.getVisibility()).isEqualTo(PlanVisibility.PRIVATE);
            assertThat(captured.getTrialDays()).isEqualTo(7);
            assertThat(captured.isActive()).isFalse();
            assertThat(captured.getUpdatedBy()).isEqualTo(ACTOR_ID);
        }

        @Test
        @DisplayName("null fields are left unchanged")
        void updatePlan_nullFields_preserveExisting() {
            Plan existing = buildPlan("GROWTH", "PLN-0002", "Growth");
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(existing));
            when(planRepository.save(any())).thenReturn(existing);

            service.updatePlan(ACTOR_ID, PLAN_ID, null, null, null, null, null, null, null);

            ArgumentCaptor<Plan> captor = ArgumentCaptor.forClass(Plan.class);
            verify(planRepository).save(captor.capture());
            Plan captured = captor.getValue();
            assertThat(captured.getName()).isEqualTo("Growth");
            assertThat(captured.getVisibility()).isEqualTo(PlanVisibility.PUBLIC);
            assertThat(captured.getTrialDays()).isEqualTo(0);
            assertThat(captured.isActive()).isTrue();
        }

        @Test
        @DisplayName("BR-1: plan not found throws PLAN_NOT_FOUND")
        void updatePlan_notFound_throws() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                service.updatePlan(ACTOR_ID, PLAN_ID, "X", null, null, null, null, null, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_NOT_FOUND));
        }

        @Test
        @DisplayName("BR-3: blank name supplied throws VALIDATION_ERROR")
        void updatePlan_blankName_throws() {
            Plan existing = buildPlan("SCALE", "PLN-0003", "Scale");
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() ->
                service.updatePlan(ACTOR_ID, PLAN_ID, "   ", null, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR));

            verify(planRepository, never()).save(any());
        }
    }

    // ── getPlan ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getPlan()")
    class GetPlan {

        @Test
        @DisplayName("success — returns domain Plan")
        void getPlan_success() {
            Plan plan = buildPlan("ENTERPRISE", "PLN-0003", "Enterprise");
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));

            Plan result = service.getPlan(PLAN_ID);

            assertThat(result.getCode()).isEqualTo("ENTERPRISE");
            assertThat(result.getSlug()).isEqualTo("PLN-0003");
        }

        @Test
        @DisplayName("not found — throws PLAN_NOT_FOUND")
        void getPlan_notFound_throws() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getPlan(PLAN_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_NOT_FOUND));
        }
    }

    // ── getPlanBySlug ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getPlanBySlug()")
    class GetPlanBySlug {

        @Test
        @DisplayName("success — returns domain Plan for known PLN-XXXX slug")
        void getPlanBySlug_success() {
            Plan plan = buildPlan("TRIAL", "PLN-0001", "Trial");
            when(planRepository.findBySlug("PLN-0001")).thenReturn(Optional.of(plan));

            Plan result = service.getPlanBySlug("PLN-0001");

            assertThat(result.getSlug()).isEqualTo("PLN-0001");
        }

        @Test
        @DisplayName("slug not found — throws PLAN_NOT_FOUND")
        void getPlanBySlug_notFound_throws() {
            when(planRepository.findBySlug("PLN-9999")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getPlanBySlug("PLN-9999"))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_NOT_FOUND));
        }
    }

    // ── listPlans ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("listPlans()")
    class ListPlans {

        @Test
        @DisplayName("no criteria — returns all plans")
        void listPlans_noCriteria_returnsAll() {
            List<Plan> plans = List.of(
                buildPlan("ENTERPRISE", "PLN-0003", "Enterprise"),
                buildPlan("STARTER",    "PLN-0001", "Starter"),
                buildPlan("TRIAL",      "PLN-0002", "Trial"));

            when(planRepository.findAll()).thenReturn(plans);

            assertThat(service.listPlans(null, null)).hasSize(3);
        }

        @Test
        @DisplayName("active=true — filters inactive plans in-memory")
        void listPlans_activeFilter_filtersInactive() {
            Instant now = Instant.now();
            Plan active1 = Plan.builder().id(UUID.randomUUID()).version(0L)
                .code("STARTER").slug("PLN-0001").name("Starter")
                .visibility(PlanVisibility.PUBLIC).trialDays(0).active(true)
                .createdAt(now).updatedAt(now).createdBy(ACTOR_ID).updatedBy(ACTOR_ID).build();
            Plan inactive = Plan.builder().id(UUID.randomUUID()).version(0L)
                .code("TRIAL").slug("PLN-0002").name("Trial")
                .visibility(PlanVisibility.PUBLIC).trialDays(14).active(false)
                .createdAt(now).updatedAt(now).createdBy(ACTOR_ID).updatedBy(ACTOR_ID).build();
            Plan active2 = Plan.builder().id(UUID.randomUUID()).version(0L)
                .code("GROWTH").slug("PLN-0003").name("Growth")
                .visibility(PlanVisibility.PUBLIC).trialDays(0).active(true)
                .createdAt(now).updatedAt(now).createdBy(ACTOR_ID).updatedBy(ACTOR_ID).build();

            when(planRepository.findAll()).thenReturn(List.of(active1, inactive, active2));

            List<Plan> result = service.listPlans(true, null);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(Plan::isActive).containsOnly(true);
        }

        @Test
        @DisplayName("visibility=PRIVATE — filters non-private plans")
        void listPlans_visibilityFilter_filtersOthers() {
            Instant now = Instant.now();
            Plan pub = Plan.builder().id(UUID.randomUUID()).version(0L)
                .code("STARTER").slug("PLN-0001").name("Starter")
                .visibility(PlanVisibility.PUBLIC).trialDays(0).active(true)
                .createdAt(now).updatedAt(now).createdBy(ACTOR_ID).updatedBy(ACTOR_ID).build();
            Plan priv = Plan.builder().id(UUID.randomUUID()).version(0L)
                .code("ENTERPRISE").slug("PLN-0002").name("Enterprise")
                .visibility(PlanVisibility.PRIVATE).trialDays(0).active(true)
                .createdAt(now).updatedAt(now).createdBy(ACTOR_ID).updatedBy(ACTOR_ID).build();

            when(planRepository.findAll()).thenReturn(List.of(pub, priv));

            List<Plan> result = service.listPlans(null, PlanVisibility.PRIVATE);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getVisibility()).isEqualTo(PlanVisibility.PRIVATE);
        }

        @Test
        @DisplayName("empty catalog — returns empty list")
        void listPlans_empty_returnsEmptyList() {
            when(planRepository.findAll()).thenReturn(List.of());

            assertThat(service.listPlans(null, null)).isEmpty();
        }
    }

    // ── deletePlan ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deletePlan()")
    class DeletePlan {

        @Test
        @DisplayName("success — delegates to repository.softDelete with given actor")
        void deletePlan_success_delegatesToRepository() {
            doNothing().when(planRepository).softDelete(PLAN_ID, ACTOR_ID);

            service.deletePlan(ACTOR_ID, PLAN_ID);

            verify(planRepository).softDelete(PLAN_ID, ACTOR_ID);
        }

        @Test
        @DisplayName("unknown plan — repository throws ResourceNotFoundException, propagated to caller")
        void deletePlan_unknownPlan_throwsNotFound() {
            doThrow(new ResourceNotFoundException(ErrorCode.PLAN_NOT_FOUND, "Plan not found: " + PLAN_ID))
                .when(planRepository).softDelete(PLAN_ID, ACTOR_ID);

            assertThatThrownBy(() -> service.deletePlan(ACTOR_ID, PLAN_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_NOT_FOUND));
        }
    }
}
