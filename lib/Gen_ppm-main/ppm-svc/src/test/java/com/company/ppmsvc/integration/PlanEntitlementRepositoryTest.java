package com.company.ppmsvc.integration;

import com.company.ppmsvc.entitlement.model.EntitlementType;
import com.company.ppmsvc.plan.model.PlanVisibility;
import com.company.ppmsvc.entitlement.model.Entitlement;
import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.planentitlement.model.PlanEntitlement;
import com.company.ppmsvc.entitlement.port.EntitlementRepositoryPort;
import com.company.ppmsvc.planentitlement.port.PlanEntitlementRepositoryPort;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.infrastructure.persistence.repository.EntitlementJpaRepository;
import com.company.ppmsvc.infrastructure.persistence.repository.PlanEntitlementJpaRepository;
import com.company.ppmsvc.infrastructure.persistence.repository.PlanJpaRepository;
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
 * Repository-level integration tests for Plan ↔ Entitlement Mapping persistence (PPM-04).
 *
 * <p>Runs against a real PostgreSQL instance (Testcontainers).  Flyway applies all
 * six migrations before the context starts, ensuring {@code ppm_plan_entitlements},
 * FK constraints, and the unique index are in place.
 *
 * <p>No class-level {@code @Transactional} — constraint tests require the INSERT to
 * flush to the DB before the violation is observable.  Cleanup is via {@code @AfterEach}
 * in FK-safe deletion order.
 */
@DisplayName("Plan Entitlement Mapping — Repository")
class PlanEntitlementRepositoryTest extends AbstractContainerIntegrationTest {

    @Autowired PlanEntitlementRepositoryPort planEntitlementRepository;
    @Autowired PlanRepositoryPort            planRepository;
    @Autowired EntitlementRepositoryPort     entitlementRepository;

    @Autowired PlanEntitlementJpaRepository planEntitlementJpaRepository;
    @Autowired PlanJpaRepository            planJpaRepository;
    @Autowired EntitlementJpaRepository     entitlementJpaRepository;

    UUID planId;
    UUID entitlementId1;
    UUID entitlementId2;

    @BeforeEach
    void setUp() {
        planId         = UUID.randomUUID();
        entitlementId1 = UUID.randomUUID();
        entitlementId2 = UUID.randomUUID();
        Instant now = Instant.now();

        // Use domain ports so version = null → Spring Data calls persist() not merge()
        planRepository.save(Plan.builder()
            .id(planId)
            .code("ENT-TEST")
            .slug("ENT-0001")
            .name("Entitlement Test Plan")
            .visibility(PlanVisibility.PUBLIC)
            .trialDays(0)
            .active(true)
            .createdAt(now).updatedAt(now)
            .build());

        entitlementRepository.save(Entitlement.builder()
            .id(entitlementId1)
            .code("max_internal_users")
            .name("Max Internal Users")
            .type(EntitlementType.QUOTA)
            .active(true)
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build());

        entitlementRepository.save(Entitlement.builder()
            .id(entitlementId2)
            .code("reporting_enabled")
            .name("Reporting Enabled")
            .type(EntitlementType.BOOLEAN)
            .active(true)
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build());
    }

    @AfterEach
    void cleanUp() {
        // FK-safe order: assignments first, then plans and entitlements
        planEntitlementJpaRepository.deleteAll();
        planJpaRepository.deleteAll();
        entitlementJpaRepository.deleteAll();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private PlanEntitlement buildAssignment(UUID pId, UUID eId, String value) {
        return PlanEntitlement.builder()
            .id(UUID.randomUUID())
            .planId(pId)
            .entitlementId(eId)
            .value(value)
            .createdAt(Instant.now())
            .createdBy(DEV_USER)
            .build();
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("persists a new assignment and returns it with version=0")
        void save_valid_persistsAndReturns() {
            PlanEntitlement saved = planEntitlementRepository.save(
                buildAssignment(planId, entitlementId1, "25"));

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getPlanId()).isEqualTo(planId);
            assertThat(saved.getEntitlementId()).isEqualTo(entitlementId1);
            assertThat(saved.getValue()).isEqualTo("25");
            assertThat(saved.getVersion()).isEqualTo(0L);
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getCreatedBy()).isEqualTo(DEV_USER);
        }
    }

    // ── saveAll ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("saveAll")
    class SaveAll {

        @Test
        @DisplayName("persists a batch of assignments and returns all saved")
        void saveAll_twAssignments_returnsBoth() {
            List<PlanEntitlement> assignments = List.of(
                buildAssignment(planId, entitlementId1, "25"),
                buildAssignment(planId, entitlementId2, "true"));

            List<PlanEntitlement> saved = planEntitlementRepository.saveAll(assignments);

            assertThat(saved).hasSize(2);
            assertThat(saved).extracting(PlanEntitlement::getEntitlementId)
                .containsExactlyInAnyOrder(entitlementId1, entitlementId2);
        }
    }

    // ── findByPlanId ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByPlanId")
    class FindByPlanId {

        @Test
        @DisplayName("returns all assignments for the plan")
        void findByPlanId_twoAssignments_returnsBoth() {
            planEntitlementRepository.save(buildAssignment(planId, entitlementId1, "25"));
            planEntitlementRepository.save(buildAssignment(planId, entitlementId2, "true"));

            List<PlanEntitlement> result = planEntitlementRepository.findByPlanId(planId);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(PlanEntitlement::getEntitlementId)
                .containsExactlyInAnyOrder(entitlementId1, entitlementId2);
        }

        @Test
        @DisplayName("returns empty list when plan has no assignments")
        void findByPlanId_none_returnsEmpty() {
            assertThat(planEntitlementRepository.findByPlanId(planId)).isEmpty();
        }
    }

    // ── findByEntitlementId ───────────────────────────────────────────────────

    @Nested
    @DisplayName("findByEntitlementId")
    class FindByEntitlementId {

        @Test
        @DisplayName("returns the assignment referencing the entitlement")
        void findByEntitlementId_exists_returnsAssignment() {
            planEntitlementRepository.save(buildAssignment(planId, entitlementId1, "10"));

            List<PlanEntitlement> result =
                planEntitlementRepository.findByEntitlementId(entitlementId1);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPlanId()).isEqualTo(planId);
            assertThat(result.get(0).getValue()).isEqualTo("10");
        }

        @Test
        @DisplayName("returns empty when entitlement has no assignments")
        void findByEntitlementId_none_returnsEmpty() {
            assertThat(planEntitlementRepository.findByEntitlementId(entitlementId1)).isEmpty();
        }
    }

    // ── exists ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("exists")
    class Exists {

        @Test
        @DisplayName("returns true when assignment is present")
        void exists_present_returnsTrue() {
            planEntitlementRepository.save(buildAssignment(planId, entitlementId1, "25"));

            assertThat(planEntitlementRepository.exists(planId, entitlementId1)).isTrue();
        }

        @Test
        @DisplayName("returns false when assignment is absent")
        void exists_absent_returnsFalse() {
            assertThat(planEntitlementRepository.exists(planId, entitlementId1)).isFalse();
        }
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("removes the specified assignment only")
        void delete_existing_removesOneRow() {
            planEntitlementRepository.save(buildAssignment(planId, entitlementId1, "25"));
            planEntitlementRepository.save(buildAssignment(planId, entitlementId2, "true"));

            planEntitlementRepository.delete(planId, entitlementId1);

            assertThat(planEntitlementRepository.exists(planId, entitlementId1)).isFalse();
            assertThat(planEntitlementRepository.exists(planId, entitlementId2)).isTrue();
        }

        @Test
        @DisplayName("is a no-op when assignment does not exist")
        void delete_absent_isNoOp() {
            planEntitlementRepository.delete(planId, entitlementId1); // must not throw
        }
    }

    // ── deleteAllByPlanId ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteAllByPlanId")
    class DeleteAllByPlanId {

        @Test
        @DisplayName("removes all assignments for the plan")
        void deleteAllByPlanId_twoAssignments_removesAll() {
            planEntitlementRepository.save(buildAssignment(planId, entitlementId1, "25"));
            planEntitlementRepository.save(buildAssignment(planId, entitlementId2, "true"));

            planEntitlementRepository.deleteAllByPlanId(planId);

            assertThat(planEntitlementRepository.findByPlanId(planId)).isEmpty();
        }

        @Test
        @DisplayName("is a no-op when plan has no assignments")
        void deleteAllByPlanId_none_isNoOp() {
            planEntitlementRepository.deleteAllByPlanId(planId); // must not throw
        }
    }

    // ── unique constraint ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("unique constraint")
    class UniqueConstraint {

        @Test
        @DisplayName("second insert of same (planId, entitlementId) throws DataIntegrityViolationException")
        void uniqueConstraint_duplicateInsert_throwsDataIntegrity() {
            planEntitlementRepository.save(buildAssignment(planId, entitlementId1, "25"));

            assertThatThrownBy(() ->
                planEntitlementRepository.save(buildAssignment(planId, entitlementId1, "50")))
                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("same entitlement can be assigned to different plans")
        void uniqueConstraint_sameEntitlementDifferentPlans_allowed() {
            UUID planId2 = UUID.randomUUID();
            Instant now  = Instant.now();

            planRepository.save(Plan.builder()
                .id(planId2)
                .code("ENT-TEST-2")
                .slug("ENT-0002")
                .name("Entitlement Test Plan Two")
                .visibility(PlanVisibility.PRIVATE)
                .trialDays(0)
                .active(true)
                .createdAt(now).updatedAt(now)
                .build());

            planEntitlementRepository.save(buildAssignment(planId,  entitlementId1, "10"));
            planEntitlementRepository.save(buildAssignment(planId2, entitlementId1, "25")); // must not throw

            assertThat(planEntitlementRepository.findByEntitlementId(entitlementId1)).hasSize(2);
        }
    }

    // ── FK integrity ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("FK integrity")
    class FkIntegrity {

        @Test
        @DisplayName("deleting an assignment does not delete the underlying entitlement")
        void delete_assignment_doesNotDeleteEntitlement() {
            planEntitlementRepository.save(buildAssignment(planId, entitlementId1, "25"));

            planEntitlementRepository.delete(planId, entitlementId1);

            assertThat(entitlementRepository.findById(entitlementId1)).isPresent();
        }

        @Test
        @DisplayName("insert with unknown plan_id violates FK constraint")
        void insert_unknownPlanId_violatesFk() {
            UUID unknownPlanId = UUID.randomUUID();

            assertThatThrownBy(() ->
                planEntitlementRepository.save(buildAssignment(unknownPlanId, entitlementId1, "10")))
                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("insert with unknown entitlement_id violates FK constraint")
        void insert_unknownEntitlementId_violatesFk() {
            UUID unknownEntitlementId = UUID.randomUUID();

            assertThatThrownBy(() ->
                planEntitlementRepository.save(buildAssignment(planId, unknownEntitlementId, "10")))
                .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
