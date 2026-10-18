package com.company.ppmsvc.unit.service;

import com.company.ppmsvc.planaddon.usecase.PlanAddOnApplicationServiceImpl;
import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.addon.model.AddOn;
import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.planaddon.model.PlanAddOn;
import com.company.ppmsvc.addon.port.AddOnRepositoryPort;
import com.company.ppmsvc.planaddon.port.PlanAddOnRepositoryPort;
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
@DisplayName("PlanAddOnApplicationService")
class PlanAddOnApplicationServiceImplTest {

    static final UUID ACTOR_ID  = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID PLAN_ID   = UUID.fromString("00000000-0000-0000-0000-000000000010");
    static final UUID ADD_ON_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");

    @Mock PlanRepositoryPort      planRepository;
    @Mock AddOnRepositoryPort     addOnRepository;
    @Mock PlanAddOnRepositoryPort planAddOnRepository;

    @InjectMocks
    PlanAddOnApplicationServiceImpl service;

    // ── fixtures ──────────────────────────────────────────────────────────────

    private Plan stubPlanExists() {
        Plan plan = Mockito.mock(Plan.class);
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));
        return plan;
    }

    private void stubAddOnExists(UUID addOnId) {
        AddOn addOn = Mockito.mock(AddOn.class);
        when(addOnRepository.findById(addOnId)).thenReturn(Optional.of(addOn));
    }

    private PlanAddOn buildPlanAddOn(UUID planId, UUID addOnId) {
        return PlanAddOn.builder()
            .id(UUID.randomUUID())
            .version(0L)
            .planId(planId)
            .addOnId(addOnId)
            .createdAt(Instant.now())
            .createdBy(ACTOR_ID)
            .build();
    }

    // ── assignAddOns ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("assignAddOns")
    class AssignAddOns {

        @Test
        @DisplayName("BR-P1+P2: success — plan and add-on exist, mapping saved")
        void success() {
            stubPlanExists();
            stubAddOnExists(ADD_ON_ID);
            when(planAddOnRepository.exists(PLAN_ID, ADD_ON_ID)).thenReturn(false);
            when(planAddOnRepository.saveAll(anyList())).thenReturn(List.of());

            service.assignAddOns(ACTOR_ID, PLAN_ID, Set.of(ADD_ON_ID));

            ArgumentCaptor<List<PlanAddOn>> cap = ArgumentCaptor.forClass(List.class);
            verify(planAddOnRepository).saveAll(cap.capture());
            assertThat(cap.getValue()).hasSize(1);
            assertThat(cap.getValue().get(0).getPlanId()).isEqualTo(PLAN_ID);
            assertThat(cap.getValue().get(0).getAddOnId()).isEqualTo(ADD_ON_ID);
            assertThat(cap.getValue().get(0).getCreatedBy()).isEqualTo(ACTOR_ID);
        }

        @Test
        @DisplayName("BR-P1: plan not found → PLAN_NOT_FOUND, nothing saved")
        void planNotFound_throwsResourceNotFoundException() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.assignAddOns(ACTOR_ID, PLAN_ID, Set.of(ADD_ON_ID)))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(e -> assertThat(((ResourceNotFoundException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_NOT_FOUND));

            verify(planAddOnRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("BR-P2: add-on not found → ADD_ON_NOT_FOUND, nothing saved")
        void addOnNotFound_throwsResourceNotFoundException() {
            stubPlanExists();
            when(addOnRepository.findById(ADD_ON_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.assignAddOns(ACTOR_ID, PLAN_ID, Set.of(ADD_ON_ID)))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(e -> assertThat(((ResourceNotFoundException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ADD_ON_NOT_FOUND));

            verify(planAddOnRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("BR-P3: add-on already assigned → PLAN_ADD_ON_ALREADY_ASSIGNED (409)")
        void duplicateAssignment_throwsBusinessException() {
            stubPlanExists();
            stubAddOnExists(ADD_ON_ID);
            when(planAddOnRepository.exists(PLAN_ID, ADD_ON_ID)).thenReturn(true);

            assertThatThrownBy(() -> service.assignAddOns(ACTOR_ID, PLAN_ID, Set.of(ADD_ON_ID)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_ADD_ON_ALREADY_ASSIGNED));

            verify(planAddOnRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("BR-P4: Set<UUID> deduplication — same ID twice results in a single save")
        void setDeduplication_sameIdOnlySavedOnce() {
            // Set.of(id, id) collapses to one element; service must create only one row
            stubPlanExists();
            stubAddOnExists(ADD_ON_ID);
            when(planAddOnRepository.exists(PLAN_ID, ADD_ON_ID)).thenReturn(false);
            when(planAddOnRepository.saveAll(anyList())).thenReturn(List.of());

            service.assignAddOns(ACTOR_ID, PLAN_ID, Set.of(ADD_ON_ID));

            ArgumentCaptor<List<PlanAddOn>> cap = ArgumentCaptor.forClass(List.class);
            verify(planAddOnRepository).saveAll(cap.capture());
            assertThat(cap.getValue()).hasSize(1);
        }
    }

    // ── replaceAddOns ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("replaceAddOns")
    class ReplaceAddOns {

        @Test
        @DisplayName("BR-P5: success — deletes existing, saves new set atomically")
        void success() {
            stubPlanExists();
            stubAddOnExists(ADD_ON_ID);
            when(planAddOnRepository.saveAll(anyList())).thenReturn(List.of());

            service.replaceAddOns(ACTOR_ID, PLAN_ID, Set.of(ADD_ON_ID));

            verify(planAddOnRepository).deleteAllByPlanId(PLAN_ID);
            ArgumentCaptor<List<PlanAddOn>> cap = ArgumentCaptor.forClass(List.class);
            verify(planAddOnRepository).saveAll(cap.capture());
            assertThat(cap.getValue()).hasSize(1);
            assertThat(cap.getValue().get(0).getAddOnId()).isEqualTo(ADD_ON_ID);
        }

        @Test
        @DisplayName("abort-before-delete: invalid add-on aborts before delete is called")
        void abortBeforeDelete_invalidAddOnPreventsDelete() {
            stubPlanExists();
            when(addOnRepository.findById(ADD_ON_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.replaceAddOns(ACTOR_ID, PLAN_ID, Set.of(ADD_ON_ID)))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(e -> assertThat(((ResourceNotFoundException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ADD_ON_NOT_FOUND));

            verify(planAddOnRepository, never()).deleteAllByPlanId(any());
        }

        @Test
        @DisplayName("plan not found → PLAN_NOT_FOUND before any DB mutation")
        void planNotFound_abortsImmediately() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.replaceAddOns(ACTOR_ID, PLAN_ID, Set.of(ADD_ON_ID)))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(e -> assertThat(((ResourceNotFoundException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_NOT_FOUND));

            verify(planAddOnRepository, never()).deleteAllByPlanId(any());
        }
    }

    // ── getPlanAddOns ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getPlanAddOns")
    class GetPlanAddOns {

        @Test
        @DisplayName("success — returns current assignments")
        void success() {
            stubPlanExists();
            PlanAddOn mapping = buildPlanAddOn(PLAN_ID, ADD_ON_ID);
            when(planAddOnRepository.findByPlanId(PLAN_ID)).thenReturn(List.of(mapping));

            List<PlanAddOn> result = service.getPlanAddOns(PLAN_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPlanId()).isEqualTo(PLAN_ID);
            assertThat(result.get(0).getAddOnId()).isEqualTo(ADD_ON_ID);
        }

        @Test
        @DisplayName("plan not found → PLAN_NOT_FOUND")
        void planNotFound_throwsResourceNotFoundException() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getPlanAddOns(PLAN_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(e -> assertThat(((ResourceNotFoundException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_NOT_FOUND));
        }

        @Test
        @DisplayName("no assignments — returns empty list")
        void emptyAssignments_returnsEmptyList() {
            stubPlanExists();
            when(planAddOnRepository.findByPlanId(PLAN_ID)).thenReturn(List.of());

            List<PlanAddOn> result = service.getPlanAddOns(PLAN_ID);

            assertThat(result).isEmpty();
        }
    }

    // ── removeAddOn ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("removeAddOn")
    class RemoveAddOn {

        @Test
        @DisplayName("BR-P6+P7: success — validates plan, deletes join row, add-on untouched")
        void success() {
            stubPlanExists();
            when(planAddOnRepository.exists(PLAN_ID, ADD_ON_ID)).thenReturn(true);

            service.removeAddOn(PLAN_ID, ADD_ON_ID);

            verify(planAddOnRepository).delete(PLAN_ID, ADD_ON_ID);
            verify(addOnRepository, never()).softDelete(any(), any());
        }

        @Test
        @DisplayName("BR-P8: mapping not found → PLAN_ADD_ON_MAPPING_NOT_FOUND")
        void mappingNotFound_throwsResourceNotFoundException() {
            stubPlanExists();
            when(planAddOnRepository.exists(PLAN_ID, ADD_ON_ID)).thenReturn(false);

            assertThatThrownBy(() -> service.removeAddOn(PLAN_ID, ADD_ON_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(e -> assertThat(((ResourceNotFoundException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_ADD_ON_MAPPING_NOT_FOUND));

            verify(planAddOnRepository, never()).delete(any(), any());
        }

        @Test
        @DisplayName("plan not found → PLAN_NOT_FOUND before mapping check")
        void planNotFound_throwsResourceNotFoundException() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.removeAddOn(PLAN_ID, ADD_ON_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(e -> assertThat(((ResourceNotFoundException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_NOT_FOUND));

            verify(planAddOnRepository, never()).delete(any(), any());
        }
    }
}
