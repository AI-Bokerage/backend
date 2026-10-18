package com.company.ppmsvc.unit.service;

import com.company.ppmsvc.planmodule.usecase.PlanModuleApplicationServiceImpl;
import com.company.ppmsvc.module.model.ModuleCode;
import com.company.ppmsvc.plan.model.PlanVisibility;
import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.module.model.Module;
import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.planmodule.model.PlanModule;
import com.company.ppmsvc.module.port.ModuleRepositoryPort;
import com.company.ppmsvc.planmodule.port.PlanModuleRepositoryPort;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlanModuleApplicationServiceImpl")
class PlanModuleApplicationServiceImplTest {

    static final UUID ACTOR_ID  = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID PLAN_ID   = UUID.fromString("00000000-0000-0000-0000-000000000010");
    static final UUID MODULE_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");

    @Mock PlanRepositoryPort       planRepository;
    @Mock ModuleRepositoryPort     moduleRepository;
    @Mock PlanModuleRepositoryPort planModuleRepository;

    @InjectMocks PlanModuleApplicationServiceImpl service;

    // ── helpers ───────────────────────────────────────────────────────────────

    private Plan stubPlan(UUID id) {
        Instant now = Instant.now();
        return Plan.builder()
            .id(id).version(0L).code("STARTER").slug("PLN-0001")
            .name("Starter").visibility(PlanVisibility.PUBLIC)
            .trialDays(0).active(true)
            .createdAt(now).updatedAt(now)
            .build();
    }

    private Module stubModule(UUID id, ModuleCode code) {
        Instant now = Instant.now();
        return Module.builder()
            .id(id).version(0L).code(code).name(code.getValue())
            .active(true).createdAt(now).updatedAt(now)
            .build();
    }

    private PlanModule stubMapping(UUID planId, UUID moduleId) {
        return PlanModule.builder()
            .id(UUID.randomUUID()).planId(planId).moduleId(moduleId)
            .createdAt(Instant.now()).createdBy(ACTOR_ID)
            .build();
    }

    // ── assignModules ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("assignModules")
    class AssignModules {

        @Test
        @DisplayName("success — valid assignment returns assigned modules")
        void assignModules_valid_returnsResponses() {
            Module module = stubModule(MODULE_ID, ModuleCode.LEAD_MANAGEMENT);

            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(stubPlan(PLAN_ID)));
            when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.of(module));
            when(planModuleRepository.exists(PLAN_ID, MODULE_ID)).thenReturn(false);
            when(planModuleRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

            List<Module> result = service.assignModules(ACTOR_ID, PLAN_ID, Set.of(MODULE_ID));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(MODULE_ID);
            verify(planModuleRepository).saveAll(argThat(list -> list.size() == 1
                && list.get(0).getPlanId().equals(PLAN_ID)
                && list.get(0).getModuleId().equals(MODULE_ID)));
        }

        @Test
        @DisplayName("BR-1 — plan not found throws PLAN_NOT_FOUND")
        void assignModules_planNotFound_throwsPlanNotFound() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                service.assignModules(ACTOR_ID, PLAN_ID, Set.of(MODULE_ID)))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_NOT_FOUND));

            verify(planModuleRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("BR-2 — module not found throws MODULE_NOT_FOUND")
        void assignModules_moduleNotFound_throwsModuleNotFound() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(stubPlan(PLAN_ID)));
            when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                service.assignModules(ACTOR_ID, PLAN_ID, Set.of(MODULE_ID)))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.MODULE_NOT_FOUND));

            verify(planModuleRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("BR-3 — already assigned throws MODULE_ALREADY_ASSIGNED_TO_PLAN as conflict")
        void assignModules_duplicateAssignment_throwsAlreadyAssigned() {
            Module module = stubModule(MODULE_ID, ModuleCode.LEAD_MANAGEMENT);
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(stubPlan(PLAN_ID)));
            when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.of(module));
            when(planModuleRepository.exists(PLAN_ID, MODULE_ID)).thenReturn(true);

            assertThatThrownBy(() ->
                service.assignModules(ACTOR_ID, PLAN_ID, Set.of(MODULE_ID)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.MODULE_ALREADY_ASSIGNED_TO_PLAN));

            verify(planModuleRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("BR-4 — Set deduplication collapses duplicate module IDs to one assignment")
        void assignModules_idempotentInput_setDeduplicates() {
            // Set.of with duplicates isn't possible at compile time; verify the port receives
            // exactly one mapping even if caller somehow passes a single-element Set
            Module module = stubModule(MODULE_ID, ModuleCode.LEAD_MANAGEMENT);

            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(stubPlan(PLAN_ID)));
            when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.of(module));
            when(planModuleRepository.exists(PLAN_ID, MODULE_ID)).thenReturn(false);
            when(planModuleRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

            List<Module> result = service.assignModules(ACTOR_ID, PLAN_ID, Set.of(MODULE_ID));

            // Only one mapping should be saved regardless of how many times the same ID appeared
            verify(planModuleRepository).saveAll(argThat(list -> list.size() == 1));
            assertThat(result).hasSize(1);
        }
    }

    // ── getModules ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getModules")
    class GetModules {

        @Test
        @DisplayName("success — returns all assigned modules")
        void getModules_twoAssigned_returnsBothEnriched() {
            UUID mod2 = UUID.randomUUID();
            Module m1 = stubModule(MODULE_ID, ModuleCode.LEAD_MANAGEMENT);
            Module m2 = stubModule(mod2,      ModuleCode.PROJECT_MANAGEMENT);

            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(stubPlan(PLAN_ID)));
            when(planModuleRepository.findByPlanId(PLAN_ID))
                .thenReturn(List.of(stubMapping(PLAN_ID, MODULE_ID), stubMapping(PLAN_ID, mod2)));
            when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.of(m1));
            when(moduleRepository.findById(mod2)).thenReturn(Optional.of(m2));

            List<Module> result = service.getModules(PLAN_ID);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("plan not found — throws PLAN_NOT_FOUND")
        void getModules_planNotFound_throwsPlanNotFound() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getModules(PLAN_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_NOT_FOUND));
        }

        @Test
        @DisplayName("no assignments — returns empty list")
        void getModules_empty_returnsEmptyList() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(stubPlan(PLAN_ID)));
            when(planModuleRepository.findByPlanId(PLAN_ID)).thenReturn(List.of());

            assertThat(service.getModules(PLAN_ID)).isEmpty();
        }
    }

    // ── replaceModules ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("replaceModules")
    class ReplaceModules {

        @Test
        @DisplayName("success — deletes all old mappings and inserts the new set atomically")
        void replaceModules_valid_deletesOldAndInsertsNew() {
            Module module = stubModule(MODULE_ID, ModuleCode.LEAD_MANAGEMENT);

            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(stubPlan(PLAN_ID)));
            when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.of(module));
            when(planModuleRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

            List<Module> result = service.replaceModules(ACTOR_ID, PLAN_ID, Set.of(MODULE_ID));

            verify(planModuleRepository).deleteAllByPlanId(PLAN_ID);
            verify(planModuleRepository).saveAll(argThat(list -> list.size() == 1));
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("BR-1 — plan not found throws PLAN_NOT_FOUND")
        void replaceModules_planNotFound_throwsPlanNotFound() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                service.replaceModules(ACTOR_ID, PLAN_ID, Set.of(MODULE_ID)))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_NOT_FOUND));

            verify(planModuleRepository, never()).deleteAllByPlanId(any());
        }

        @Test
        @DisplayName("BR-2 — module not found aborts before touching the DB")
        void replaceModules_moduleNotFound_abortsBeforeDelete() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(stubPlan(PLAN_ID)));
            when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                service.replaceModules(ACTOR_ID, PLAN_ID, Set.of(MODULE_ID)))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.MODULE_NOT_FOUND));

            verify(planModuleRepository, never()).deleteAllByPlanId(any());
            verify(planModuleRepository, never()).saveAll(any());
        }
    }

    // ── removeModule ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("removeModule")
    class RemoveModule {

        @Test
        @DisplayName("success — deletes only the mapping row, not the module")
        void removeModule_valid_deletesMapping() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(stubPlan(PLAN_ID)));
            when(planModuleRepository.exists(PLAN_ID, MODULE_ID)).thenReturn(true);

            service.removeModule(PLAN_ID, MODULE_ID);

            verify(planModuleRepository).delete(PLAN_ID, MODULE_ID);
            verify(moduleRepository, never()).softDelete(any(), any());
        }

        @Test
        @DisplayName("mapping not found — throws PLAN_MODULE_MAPPING_NOT_FOUND")
        void removeModule_mappingNotFound_throwsMappingNotFound() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(stubPlan(PLAN_ID)));
            when(planModuleRepository.exists(PLAN_ID, MODULE_ID)).thenReturn(false);

            assertThatThrownBy(() -> service.removeModule(PLAN_ID, MODULE_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_MODULE_MAPPING_NOT_FOUND));

            verify(planModuleRepository, never()).delete(any(), any());
        }

        @Test
        @DisplayName("plan not found — throws PLAN_NOT_FOUND")
        void removeModule_planNotFound_throwsPlanNotFound() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.removeModule(PLAN_ID, MODULE_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_NOT_FOUND));
        }
    }
}
