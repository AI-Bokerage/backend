package com.company.ppmsvc.integration;

import com.company.ppmsvc.module.model.ModuleCode;
import com.company.ppmsvc.plan.model.PlanVisibility;
import com.company.ppmsvc.module.model.Module;
import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.planmodule.model.PlanModule;
import com.company.ppmsvc.module.port.ModuleRepositoryPort;
import com.company.ppmsvc.planmodule.port.PlanModuleRepositoryPort;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.infrastructure.persistence.repository.ModuleJpaRepository;
import com.company.ppmsvc.infrastructure.persistence.repository.PlanJpaRepository;
import com.company.ppmsvc.infrastructure.persistence.repository.PlanModuleJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Repository-level integration tests for Plan ↔ Module Mapping persistence (PPM-03).
 *
 * <p>Runs against a real PostgreSQL instance (Testcontainers).  Flyway applies all
 * five migrations before the context starts, ensuring the {@code ppm_plan_modules}
 * table, FK constraints, and unique index are in place.
 *
 * <p>No class-level {@code @Transactional} is used — constraint tests require the
 * INSERT to flush to the DB before the violation is observable.  Cleanup is done
 * via {@code @AfterEach} in FK-safe deletion order.
 */
@DisplayName("Plan Module Mapping — Repository")
class PlanModuleRepositoryTest extends AbstractContainerIntegrationTest {

    @Autowired PlanModuleRepositoryPort planModuleRepository;
    @Autowired PlanRepositoryPort       planRepository;
    @Autowired ModuleRepositoryPort     moduleRepository;
    @Autowired PlanModuleJpaRepository  planModuleJpaRepository;
    @Autowired PlanJpaRepository        planJpaRepository;
    @Autowired ModuleJpaRepository      moduleJpaRepository;

    UUID planId;
    UUID moduleId1;
    UUID moduleId2;

    @BeforeEach
    void setUp() {
        planId    = UUID.randomUUID();
        moduleId1 = UUID.randomUUID();
        moduleId2 = UUID.randomUUID();
        Instant now = Instant.now();

        // Use domain ports so version = null → Spring Data calls persist() not merge()
        planRepository.save(Plan.builder()
            .id(planId)
            .code("RPO-TEST")
            .slug("RPO-0001")
            .name("Repo Test Plan")
            .visibility(PlanVisibility.PUBLIC)
            .trialDays(0)
            .active(true)
            .createdAt(now).updatedAt(now)
            .build());

        moduleRepository.save(Module.builder()
            .id(moduleId1)
            .code(ModuleCode.LEAD_MANAGEMENT)
            .name("Lead Management")
            .active(true)
            .createdAt(now).updatedAt(now)
            .build());

        moduleRepository.save(Module.builder()
            .id(moduleId2)
            .code(ModuleCode.PROJECT_MANAGEMENT)
            .name("Project Management")
            .active(true)
            .createdAt(now).updatedAt(now)
            .build());
    }

    @AfterEach
    void cleanUp() {
        // FK-safe order: mappings first, then plans and modules
        planModuleJpaRepository.deleteAll();
        planJpaRepository.deleteAll();
        moduleJpaRepository.deleteAll();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private PlanModule buildMapping(UUID pId, UUID mId) {
        return PlanModule.builder()
            .id(UUID.randomUUID())
            .planId(pId)
            .moduleId(mId)
            .createdAt(Instant.now())
            .createdBy(DEV_USER)
            .build();
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("save — persists a new mapping and returns it with version=0")
        void save_valid_persistsAndReturns() {
            PlanModule saved = planModuleRepository.save(buildMapping(planId, moduleId1));

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getPlanId()).isEqualTo(planId);
            assertThat(saved.getModuleId()).isEqualTo(moduleId1);
            assertThat(saved.getVersion()).isEqualTo(0L);
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getCreatedBy()).isEqualTo(DEV_USER);
        }
    }

    // ── findByPlanId ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByPlanId")
    class FindByPlanId {

        @Test
        @DisplayName("findByPlanId — returns all mappings for the plan")
        void findByPlanId_twoModules_returnsBoth() {
            planModuleRepository.save(buildMapping(planId, moduleId1));
            planModuleRepository.save(buildMapping(planId, moduleId2));

            List<PlanModule> result = planModuleRepository.findByPlanId(planId);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(PlanModule::getModuleId)
                .containsExactlyInAnyOrder(moduleId1, moduleId2);
        }

        @Test
        @DisplayName("findByPlanId — returns empty list when plan has no assignments")
        void findByPlanId_none_returnsEmpty() {
            assertThat(planModuleRepository.findByPlanId(planId)).isEmpty();
        }
    }

    // ── findByModuleId ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByModuleId")
    class FindByModuleId {

        @Test
        @DisplayName("findByModuleId — returns the mapping referencing the module")
        void findByModuleId_exists_returnsMapping() {
            planModuleRepository.save(buildMapping(planId, moduleId1));

            List<PlanModule> result = planModuleRepository.findByModuleId(moduleId1);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPlanId()).isEqualTo(planId);
        }

        @Test
        @DisplayName("findByModuleId — returns empty when module has no assignments")
        void findByModuleId_none_returnsEmpty() {
            assertThat(planModuleRepository.findByModuleId(moduleId1)).isEmpty();
        }
    }

    // ── exists ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("exists")
    class Exists {

        @Test
        @DisplayName("exists — returns true when mapping is present")
        void exists_present_returnsTrue() {
            planModuleRepository.save(buildMapping(planId, moduleId1));
            assertThat(planModuleRepository.exists(planId, moduleId1)).isTrue();
        }

        @Test
        @DisplayName("exists — returns false when mapping is absent")
        void exists_absent_returnsFalse() {
            assertThat(planModuleRepository.exists(planId, moduleId1)).isFalse();
        }
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("delete — removes the specified mapping only")
        void delete_existing_removesOneRow() {
            planModuleRepository.save(buildMapping(planId, moduleId1));
            planModuleRepository.save(buildMapping(planId, moduleId2));

            planModuleRepository.delete(planId, moduleId1);

            assertThat(planModuleRepository.exists(planId, moduleId1)).isFalse();
            assertThat(planModuleRepository.exists(planId, moduleId2)).isTrue();
        }

        @Test
        @DisplayName("delete — is a no-op when mapping does not exist")
        void delete_absent_isNoOp() {
            planModuleRepository.delete(planId, moduleId1); // must not throw
        }
    }

    // ── deleteAllByPlanId ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteAllByPlanId")
    class DeleteAllByPlanId {

        @Test
        @DisplayName("deleteAllByPlanId — removes all mappings for the plan")
        void deleteAllByPlanId_twoMappings_removesAll() {
            planModuleRepository.save(buildMapping(planId, moduleId1));
            planModuleRepository.save(buildMapping(planId, moduleId2));

            planModuleRepository.deleteAllByPlanId(planId);

            assertThat(planModuleRepository.findByPlanId(planId)).isEmpty();
        }

        @Test
        @DisplayName("deleteAllByPlanId — is a no-op when plan has no mappings")
        void deleteAllByPlanId_none_isNoOp() {
            planModuleRepository.deleteAllByPlanId(planId); // must not throw
        }
    }

    // ── UniqueConstraint ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("UniqueConstraint")
    class UniqueConstraint {

        @Test
        @DisplayName("unique constraint — second insert of same (planId, moduleId) throws DataIntegrityViolationException")
        void uniqueConstraint_duplicateInsert_throwsDataIntegrity() {
            planModuleRepository.save(buildMapping(planId, moduleId1));

            assertThatThrownBy(() -> planModuleRepository.save(buildMapping(planId, moduleId1)))
                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("unique constraint — same module can be assigned to different plans")
        void uniqueConstraint_sameModuleDifferentPlans_allowed() {
            // Create a second plan for this test using the port (null version → persist)
            UUID planId2 = UUID.randomUUID();
            Instant now = Instant.now();
            planRepository.save(Plan.builder()
                .id(planId2)
                .code("RPO-TEST-2")
                .slug("RPO-0002")
                .name("Repo Test Plan Two")
                .visibility(PlanVisibility.PRIVATE)
                .trialDays(0)
                .active(true)
                .createdAt(now).updatedAt(now)
                .build());

            planModuleRepository.save(buildMapping(planId,  moduleId1));
            planModuleRepository.save(buildMapping(planId2, moduleId1)); // must not throw

            assertThat(planModuleRepository.findByModuleId(moduleId1)).hasSize(2);
        }
    }
}
