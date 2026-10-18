package com.company.ppmsvc.integration;

import com.company.ppmsvc.addon.model.AddOnType;
import com.company.ppmsvc.plan.model.PlanVisibility;
import com.company.ppmsvc.addon.model.AddOn;
import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.planaddon.model.PlanAddOn;
import com.company.ppmsvc.addon.port.AddOnRepositoryPort;
import com.company.ppmsvc.planaddon.port.PlanAddOnRepositoryPort;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.infrastructure.persistence.repository.AddOnJpaRepository;
import com.company.ppmsvc.infrastructure.persistence.repository.PlanAddOnJpaRepository;
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
 * Repository-level integration tests for Plan ↔ Add-On Mapping persistence (PPM-11).
 *
 * <p>Runs against a real PostgreSQL instance (Testcontainers).  Flyway applies all
 * V001–V010 migrations before the context starts, ensuring the {@code ppm_plan_add_ons}
 * table, FK constraints, and unique index are in place.
 *
 * <p>No class-level {@code @Transactional} is used — constraint tests require the
 * INSERT to flush to the DB before the violation is observable.  Cleanup is done
 * via {@code @AfterEach} in FK-safe deletion order.
 */
@DisplayName("Plan Add-On Mapping — Repository")
class PlanAddOnRepositoryTest extends AbstractContainerIntegrationTest {

    @Autowired PlanAddOnRepositoryPort planAddOnRepository;
    @Autowired PlanRepositoryPort      planRepository;
    @Autowired AddOnRepositoryPort     addOnRepository;
    @Autowired PlanAddOnJpaRepository  planAddOnJpaRepository;
    @Autowired PlanJpaRepository       planJpaRepository;
    @Autowired AddOnJpaRepository      addOnJpaRepository;

    UUID planId;
    UUID addOnId1;
    UUID addOnId2;

    @BeforeEach
    void setUp() {
        planId   = UUID.randomUUID();
        addOnId1 = UUID.randomUUID();
        addOnId2 = UUID.randomUUID();
        Instant now = Instant.now();

        planRepository.save(Plan.builder()
                .id(planId)
                .code("PA-TEST")
                .slug("PA-0001")
                .name("Plan AddOn Test Plan")
                .visibility(PlanVisibility.PUBLIC)
                .trialDays(0).active(true)
                .createdAt(now).updatedAt(now)
                .build());

        addOnRepository.save(AddOn.builder()
                .id(addOnId1)
                .code("EXTRA_USERS_10")
                .name("Extra Users 10").type(AddOnType.QUOTA).active(true)
                .createdAt(now).updatedAt(now)
                .createdBy(DEV_USER).updatedBy(DEV_USER)
                .build());

        addOnRepository.save(AddOn.builder()
                .id(addOnId2)
                .code("EXTRA_USERS_25")
                .name("Extra Users 25").type(AddOnType.QUOTA).active(true)
                .createdAt(now).updatedAt(now)
                .createdBy(DEV_USER).updatedBy(DEV_USER)
                .build());
    }

    @AfterEach
    void cleanUp() {
        planAddOnJpaRepository.deleteAll();
        planJpaRepository.deleteAll();
        addOnJpaRepository.deleteAll();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private PlanAddOn buildMapping(UUID pId, UUID aId) {
        return PlanAddOn.builder()
                .id(UUID.randomUUID())
                .planId(pId)
                .addOnId(aId)
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
            PlanAddOn saved = planAddOnRepository.save(buildMapping(planId, addOnId1));

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getPlanId()).isEqualTo(planId);
            assertThat(saved.getAddOnId()).isEqualTo(addOnId1);
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
        @DisplayName("saveAll — persists multiple mappings in a single call")
        void saveAll_twoMappings_persistsBoth() {
            List<PlanAddOn> saved = planAddOnRepository.saveAll(List.of(
                buildMapping(planId, addOnId1),
                buildMapping(planId, addOnId2)
            ));

            assertThat(saved).hasSize(2);
            assertThat(planAddOnRepository.findByPlanId(planId)).hasSize(2);
        }

        @Test
        @DisplayName("saveAll — empty list is a no-op")
        void saveAll_empty_isNoOp() {
            List<PlanAddOn> saved = planAddOnRepository.saveAll(List.of());

            assertThat(saved).isEmpty();
        }
    }

    // ── findByPlanId ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByPlanId")
    class FindByPlanId {

        @Test
        @DisplayName("findByPlanId — returns all mappings for the plan")
        void findByPlanId_twoAddOns_returnsBoth() {
            planAddOnRepository.save(buildMapping(planId, addOnId1));
            planAddOnRepository.save(buildMapping(planId, addOnId2));

            List<PlanAddOn> result = planAddOnRepository.findByPlanId(planId);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(PlanAddOn::getAddOnId)
                    .containsExactlyInAnyOrder(addOnId1, addOnId2);
        }

        @Test
        @DisplayName("findByPlanId — returns empty list when plan has no assignments")
        void findByPlanId_none_returnsEmpty() {
            assertThat(planAddOnRepository.findByPlanId(planId)).isEmpty();
        }
    }

    // ── findByAddOnId ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByAddOnId")
    class FindByAddOnId {

        @Test
        @DisplayName("findByAddOnId — returns the mapping referencing the add-on")
        void findByAddOnId_exists_returnsMapping() {
            planAddOnRepository.save(buildMapping(planId, addOnId1));

            List<PlanAddOn> result = planAddOnRepository.findByAddOnId(addOnId1);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPlanId()).isEqualTo(planId);
        }

        @Test
        @DisplayName("findByAddOnId — returns empty when add-on has no assignments")
        void findByAddOnId_none_returnsEmpty() {
            assertThat(planAddOnRepository.findByAddOnId(addOnId1)).isEmpty();
        }
    }

    // ── exists ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("exists")
    class Exists {

        @Test
        @DisplayName("exists — returns true when mapping is present")
        void exists_present_returnsTrue() {
            planAddOnRepository.save(buildMapping(planId, addOnId1));

            assertThat(planAddOnRepository.exists(planId, addOnId1)).isTrue();
        }

        @Test
        @DisplayName("exists — returns false when mapping is absent")
        void exists_absent_returnsFalse() {
            assertThat(planAddOnRepository.exists(planId, addOnId1)).isFalse();
        }
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("delete — removes the specified mapping only")
        void delete_existing_removesOneRow() {
            planAddOnRepository.save(buildMapping(planId, addOnId1));
            planAddOnRepository.save(buildMapping(planId, addOnId2));

            planAddOnRepository.delete(planId, addOnId1);

            assertThat(planAddOnRepository.exists(planId, addOnId1)).isFalse();
            assertThat(planAddOnRepository.exists(planId, addOnId2)).isTrue();
        }

        @Test
        @DisplayName("delete — is a no-op when mapping does not exist")
        void delete_absent_isNoOp() {
            planAddOnRepository.delete(planId, addOnId1); // must not throw
        }
    }

    // ── deleteAllByPlanId ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteAllByPlanId")
    class DeleteAllByPlanId {

        @Test
        @DisplayName("deleteAllByPlanId — removes all mappings for the plan")
        void deleteAllByPlanId_twoMappings_removesAll() {
            planAddOnRepository.save(buildMapping(planId, addOnId1));
            planAddOnRepository.save(buildMapping(planId, addOnId2));

            planAddOnRepository.deleteAllByPlanId(planId);

            assertThat(planAddOnRepository.findByPlanId(planId)).isEmpty();
        }

        @Test
        @DisplayName("deleteAllByPlanId — is a no-op when plan has no mappings")
        void deleteAllByPlanId_none_isNoOp() {
            planAddOnRepository.deleteAllByPlanId(planId); // must not throw
        }
    }

    // ── unique constraint ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("unique constraint")
    class UniqueConstraint {

        @Test
        @DisplayName("throws DataIntegrityViolationException on duplicate (planId, addOnId)")
        void uniqueConstraint_duplicateInsert_throwsDataIntegrity() {
            planAddOnRepository.save(buildMapping(planId, addOnId1));

            assertThatThrownBy(() -> planAddOnRepository.save(buildMapping(planId, addOnId1)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("constraint violation message contains constraint name for GlobalExceptionHandler detection")
        void uniqueConstraint_duplicateInsert_exceptionContainsConstraintName() {
            planAddOnRepository.save(buildMapping(planId, addOnId1));

            assertThatThrownBy(() -> planAddOnRepository.save(buildMapping(planId, addOnId1)))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("uq_ppm_plan_add_on");
        }

        @Test
        @DisplayName("same add-on can be assigned to different plans")
        void uniqueConstraint_sameAddOnDifferentPlans_allowed() {
            UUID planId2 = UUID.randomUUID();
            Instant now = Instant.now();
            planRepository.save(Plan.builder()
                    .id(planId2)
                    .code("PA-TEST-2").slug("PA-0002").name("Plan AddOn Test Plan 2")
                    .visibility(PlanVisibility.PRIVATE)
                    .trialDays(0).active(true)
                    .createdAt(now).updatedAt(now)
                    .build());

            planAddOnRepository.save(buildMapping(planId,  addOnId1));
            planAddOnRepository.save(buildMapping(planId2, addOnId1)); // must not throw

            assertThat(planAddOnRepository.findByAddOnId(addOnId1)).hasSize(2);
        }
    }

    // ── FK integrity ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("FK integrity")
    class FkIntegrity {

        @Test
        @DisplayName("saving with unknown plan_id throws DataIntegrityViolationException")
        void save_unknownPlanId_throwsFkViolation() {
            assertThatThrownBy(() ->
                planAddOnRepository.save(buildMapping(UUID.randomUUID(), addOnId1)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("saving with unknown add_on_id throws DataIntegrityViolationException")
        void save_unknownAddOnId_throwsFkViolation() {
            assertThatThrownBy(() ->
                planAddOnRepository.save(buildMapping(planId, UUID.randomUUID())))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("no cascade — deleting plan does not propagate without explicit cleanup")
        void noCascade_deletingPlanWithMappings_requiresManualCleanup() {
            planAddOnRepository.save(buildMapping(planId, addOnId1));

            // Confirm mapping exists before deletion
            assertThat(planAddOnRepository.findByPlanId(planId)).hasSize(1);

            // Manual cleanup order: mappings first, then plan
            planAddOnRepository.deleteAllByPlanId(planId);
            planJpaRepository.deleteById(planId);

            assertThat(planAddOnRepository.findByPlanId(planId)).isEmpty();
        }
    }
}
