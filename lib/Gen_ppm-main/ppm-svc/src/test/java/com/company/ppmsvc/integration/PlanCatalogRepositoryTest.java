package com.company.ppmsvc.integration;

import com.company.ppmsvc.plan.model.PlanVisibility;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.infrastructure.persistence.repository.PlanJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Repository-level integration tests for Plan Catalog persistence.
 *
 * <p>All tests run against a real PostgreSQL instance managed by Testcontainers.
 * Flyway applies all migrations before the context starts, ensuring the
 * {@code ppm_plans} table and its constraints are in place.
 *
 * <p>No class-level {@code @Transactional} is used because unique-constraint
 * tests require the INSERT to actually reach the DB (i.e. flush) before the
 * exception is observable.  All tests clean up via {@code @AfterEach} instead
 * of relying on transaction rollback.
 */
@DisplayName("Plan Catalog — Repository")
class PlanCatalogRepositoryTest extends AbstractContainerIntegrationTest {

    @Autowired
    private PlanRepositoryPort planRepository;

    /** Direct JPA repository — used only for teardown, never in assertions. */
    @Autowired
    private PlanJpaRepository planJpaRepository;

    /** Raw JDBC access — used only in converter-roundtrip and constraint-name tests. */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        // Hard-delete is intentional: test teardown must fully remove rows
        // (including soft-deleted ones) to preserve isolation between test methods.
        planJpaRepository.deleteAll();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Plan buildPlan(String code, String slug, String name) {
        Instant now = Instant.now();
        return Plan.builder()
            .id(UUID.randomUUID())
            // version intentionally omitted: null version signals a new entity to
            // Spring Data JPA → calls persist() instead of merge()
            .code(code)
            .slug(slug)
            .name(name)
            .tagline(name + " — tagline")
            .description("Integration test plan — " + name)
            .visibility(PlanVisibility.PUBLIC)
            .trialDays(0)
            .active(true)
            .createdAt(now)
            .updatedAt(now)
            .createdBy(DEV_USER)
            .updatedBy(DEV_USER)
            .build();
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("persists a new plan and returns the saved state with version")
        void save_newPlan_persistsAndReturns() {
            Plan saved = planRepository.save(buildPlan("starter", "starter", "Starter"));

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getCode()).isEqualTo("starter");
            assertThat(saved.getSlug()).isEqualTo("starter");
            assertThat(saved.getName()).isEqualTo("Starter");
            assertThat(saved.getVisibility()).isEqualTo(PlanVisibility.PUBLIC);
            assertThat(saved.getTrialDays()).isEqualTo(0);
            assertThat(saved.isActive()).isTrue();
            assertThat(saved.getVersion()).isNotNull();
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("returns plan when ID exists")
        void findById_exists_returnsPlan() {
            Plan saved = planRepository.save(buildPlan("growth", "growth", "Growth"));

            Optional<Plan> found = planRepository.findById(saved.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getCode()).isEqualTo("growth");
            assertThat(found.get().getSlug()).isEqualTo("growth");
        }

        @Test
        @DisplayName("returns empty when ID does not exist")
        void findById_missing_returnsEmpty() {
            Optional<Plan> found = planRepository.findById(UUID.randomUUID());

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByCode")
    class FindByCode {

        @Test
        @DisplayName("returns plan when code exists")
        void findByCode_exists_returnsPlan() {
            planRepository.save(buildPlan("scale", "scale", "Scale"));

            Optional<Plan> found = planRepository.findByCode("scale");

            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("Scale");
        }

        @Test
        @DisplayName("returns empty when code does not exist")
        void findByCode_missing_returnsEmpty() {
            Optional<Plan> found = planRepository.findByCode("nonexistent");

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("findBySlug")
    class FindBySlug {

        @Test
        @DisplayName("returns plan when slug exists")
        void findBySlug_exists_returnsPlan() {
            planRepository.save(buildPlan("enterprise", "enterprise", "Enterprise"));

            Optional<Plan> found = planRepository.findBySlug("enterprise");

            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("Enterprise");
        }

        @Test
        @DisplayName("returns empty when slug does not exist")
        void findBySlug_missing_returnsEmpty() {
            Optional<Plan> found = planRepository.findBySlug("nonexistent-slug");

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsByCode")
    class ExistsByCode {

        @Test
        @DisplayName("returns true when plan with code exists")
        void existsByCode_exists_returnsTrue() {
            planRepository.save(buildPlan("trial", "trial", "Trial"));

            assertThat(planRepository.existsByCode("trial")).isTrue();
        }

        @Test
        @DisplayName("returns false when no plan with code exists")
        void existsByCode_missing_returnsFalse() {
            assertThat(planRepository.existsByCode("nonexistent")).isFalse();
        }
    }

    @Nested
    @DisplayName("existsBySlug")
    class ExistsBySlug {

        @Test
        @DisplayName("returns true when plan with slug exists")
        void existsBySlug_exists_returnsTrue() {
            planRepository.save(buildPlan("starter", "starter", "Starter"));

            assertThat(planRepository.existsBySlug("starter")).isTrue();
        }

        @Test
        @DisplayName("returns false when no plan with slug exists")
        void existsBySlug_missing_returnsFalse() {
            assertThat(planRepository.existsBySlug("nonexistent-slug")).isFalse();
        }
    }

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("returns all persisted plans ordered by code ascending")
        void findAll_multiplePlans_returnsOrderedByCode() {
            planRepository.save(buildPlan("scale",      "scale",      "Scale"));
            planRepository.save(buildPlan("enterprise", "enterprise", "Enterprise"));
            planRepository.save(buildPlan("growth",     "growth",     "Growth"));

            List<Plan> all = planRepository.findAll();

            assertThat(all).hasSize(3);
            assertThat(all).extracting(Plan::getCode)
                .containsExactly("enterprise", "growth", "scale");
        }

        @Test
        @DisplayName("returns empty list when no plans exist")
        void findAll_empty_returnsEmptyList() {
            assertThat(planRepository.findAll()).isEmpty();
        }
    }

    @Nested
    @DisplayName("unique code constraint")
    class UniqueCodeConstraint {

        @Test
        @DisplayName("throws DataIntegrityViolationException when duplicate code is saved")
        void save_duplicateCode_throwsConstraintViolation() {
            planRepository.save(buildPlan("starter", "starter-1", "Starter v1"));

            assertThatThrownBy(() ->
                planRepository.save(buildPlan("starter", "starter-2", "Starter v2")))
                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("constraint violation message contains index name for detection")
        void save_duplicateCode_exceptionContainsConstraintName() {
            planRepository.save(buildPlan("growth", "growth-1", "Growth v1"));

            assertThatThrownBy(() ->
                planRepository.save(buildPlan("growth", "growth-2", "Growth v2")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_ppm_plans_code");
        }
    }

    @Nested
    @DisplayName("unique slug constraint")
    class UniqueSlugConstraint {

        @Test
        @DisplayName("throws DataIntegrityViolationException when duplicate slug is saved")
        void save_duplicateSlug_throwsConstraintViolation() {
            planRepository.save(buildPlan("trial-v1", "trial", "Trial v1"));

            assertThatThrownBy(() ->
                planRepository.save(buildPlan("trial-v2", "trial", "Trial v2")))
                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("constraint violation message contains index name for detection")
        void save_duplicateSlug_exceptionContainsConstraintName() {
            planRepository.save(buildPlan("enterprise-v1", "enterprise", "Enterprise v1"));

            assertThatThrownBy(() ->
                planRepository.save(buildPlan("enterprise-v2", "enterprise", "Enterprise v2")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_ppm_plans_slug");
        }
    }

    @Nested
    @DisplayName("audit fields")
    class AuditFields {

        @Test
        @DisplayName("createdAt, updatedAt, createdBy and updatedBy are populated after save")
        void save_newPlan_populatesAuditFields() {
            Instant before = Instant.now().minusSeconds(1);

            Plan saved = planRepository.save(buildPlan("scale", "scale", "Scale"));

            assertThat(saved.getCreatedAt()).isAfter(before);
            assertThat(saved.getUpdatedAt()).isAfter(before);
            assertThat(saved.getCreatedBy()).isEqualTo(DEV_USER);
            assertThat(saved.getUpdatedBy()).isEqualTo(DEV_USER);
        }

        @Test
        @DisplayName("version is 0 after initial save")
        void save_newPlan_versionIsZero() {
            Plan saved = planRepository.save(buildPlan("trial", "trial", "Trial"));

            assertThat(saved.getVersion()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("converter roundtrip")
    class ConverterRoundtrip {

        @Test
        @DisplayName("stores visibility wire value in DB, not enum constant name")
        void save_planVisibility_storedAsWireValue() {
            Plan saved = planRepository.save(buildPlan("starter", "starter", "Starter"));

            // Query the raw DB value directly — bypasses JPA converter
            String storedVisibility = jdbcTemplate.queryForObject(
                "SELECT visibility FROM ppm_plans WHERE id = ?",
                String.class,
                saved.getId());

            assertThat(storedVisibility).isEqualTo("public");
            assertThat(storedVisibility).doesNotContain("PUBLIC");
        }

        @Test
        @DisplayName("enum is reconstituted correctly from DB wire value")
        void findBySlug_afterSave_enumReconstitutedCorrectly() {
            planRepository.save(buildPlan("enterprise", "enterprise", "Enterprise"));

            Optional<Plan> found = planRepository.findBySlug("enterprise");

            assertThat(found).isPresent();
            assertThat(found.get().getVisibility()).isEqualTo(PlanVisibility.PUBLIC);
            assertThat(found.get().getVisibility().getValue()).isEqualTo("public");
        }
    }

    @Nested
    @DisplayName("optimistic locking")
    class OptimisticLocking {

        @Test
        @DisplayName("throws OptimisticLockingFailureException when stale version is saved")
        void save_staleVersion_throwsOptimisticLockException() {
            // Persist the plan — DB row gets version = 0
            Plan original = planRepository.save(buildPlan("growth", "growth", "Growth"));

            // First update: version 0 → DB accepts, version becomes 1
            Plan firstUpdate = Plan.builder()
                .id(original.getId())
                .version(original.getVersion())   // 0
                .code(original.getCode())
                .slug(original.getSlug())
                .name("Growth — v1")
                .tagline(original.getTagline())
                .description(original.getDescription())
                .visibility(original.getVisibility())
                .trialDays(original.getTrialDays())
                .active(original.isActive())
                .createdAt(original.getCreatedAt())
                .updatedAt(Instant.now())
                .createdBy(original.getCreatedBy())
                .updatedBy(DEV_USER)
                .build();
            planRepository.save(firstUpdate);   // succeeds; DB version is now 1

            // Second update built from the original stale version (0) — must fail
            Plan staleUpdate = Plan.builder()
                .id(original.getId())
                .version(original.getVersion())   // still 0, DB has 1
                .code(original.getCode())
                .slug(original.getSlug())
                .name("Growth — stale")
                .tagline(original.getTagline())
                .description(original.getDescription())
                .visibility(original.getVisibility())
                .trialDays(original.getTrialDays())
                .active(original.isActive())
                .createdAt(original.getCreatedAt())
                .updatedAt(Instant.now())
                .createdBy(original.getCreatedBy())
                .updatedBy(DEV_USER)
                .build();

            assertThatThrownBy(() -> planRepository.save(staleUpdate))
                .isInstanceOf(OptimisticLockingFailureException.class);
        }
    }

    @Nested
    @DisplayName("soft delete")
    class SoftDelete {

        @Test
        @DisplayName("softDelete hides plan from findById")
        void softDelete_existingPlan_hiddenFromFindById() {
            Plan saved = planRepository.save(buildPlan("trial", "trial", "Trial"));

            planRepository.softDelete(saved.getId(), DEV_USER);

            assertThat(planRepository.findById(saved.getId())).isEmpty();
        }

        @Test
        @DisplayName("softDelete hides plan from findAll")
        void softDelete_existingPlan_hiddenFromFindAll() {
            Plan saved = planRepository.save(buildPlan("starter", "starter", "Starter"));

            planRepository.softDelete(saved.getId(), DEV_USER);

            assertThat(planRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("softDelete makes existsByCode return false")
        void softDelete_existingPlan_existsByCodeReturnsFalse() {
            Plan saved = planRepository.save(buildPlan("scale", "scale", "Scale"));

            planRepository.softDelete(saved.getId(), DEV_USER);

            assertThat(planRepository.existsByCode("scale")).isFalse();
        }

        @Test
        @DisplayName("softDelete makes existsBySlug return false")
        void softDelete_existingPlan_existsBySlugReturnsFalse() {
            Plan saved = planRepository.save(buildPlan("enterprise", "enterprise", "Enterprise"));

            planRepository.softDelete(saved.getId(), DEV_USER);

            assertThat(planRepository.existsBySlug("enterprise")).isFalse();
        }

        @Test
        @DisplayName("softDelete on unknown ID throws ResourceNotFoundException")
        void softDelete_unknownId_throwsNotFoundException() {
            assertThatThrownBy(() ->
                planRepository.softDelete(UUID.randomUUID(), DEV_USER))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("code reuse after soft delete")
    class CodeReuse {

        @Test
        @DisplayName("soft-deleted code can be reused by a new plan")
        void softDelete_deletedCode_canBeReused() {
            Plan first = planRepository.save(buildPlan("growth", "growth-v1", "Growth v1"));
            planRepository.softDelete(first.getId(), DEV_USER);

            // Unique partial index is WHERE deleted_at IS NULL, so this must succeed
            Plan second = planRepository.save(buildPlan("growth", "growth-v2", "Growth v2"));

            assertThat(second.getId()).isNotEqualTo(first.getId());
            assertThat(planRepository.findByCode("growth")).isPresent();
        }
    }

    @Nested
    @DisplayName("slug partial-index constraint (DB layer)")
    class SlugConstraintAfterSoftDelete {

        @Test
        @DisplayName("partial unique index allows same slug string after soft-delete (DB safety net only — application never reuses slugs)")
        void softDelete_deletedSlug_partialIndexAllowsReuse() {
            // This test verifies the DB-level partial unique index behaviour.
            // At the application layer, slugs are generated by ppm_plan_slug_seq and
            // are never reused — once PLN-0001 is deleted, the next plan gets PLN-0002.
            Plan first = planRepository.save(buildPlan("trial-v1", "PLN-0001", "Trial v1"));
            planRepository.softDelete(first.getId(), DEV_USER);

            // Unique partial index is WHERE deleted_at IS NULL, so this must succeed
            Plan second = planRepository.save(buildPlan("trial-v2", "PLN-0001", "Trial v2"));

            assertThat(second.getId()).isNotEqualTo(first.getId());
            assertThat(planRepository.findBySlug("PLN-0001")).isPresent();
            // Note: the application service NEVER does this — sequence always advances
        }
    }

    @Nested
    @DisplayName("nextSlugSequenceValue")
    class NextSlugSequenceValue {

        @Test
        @DisplayName("returns an incrementing long value from the DB sequence")
        void nextSlugSequenceValue_returnsIncrement() {
            long first  = planRepository.nextSlugSequenceValue();
            long second = planRepository.nextSlugSequenceValue();
            long third  = planRepository.nextSlugSequenceValue();

            // Sequence increments by 1 each call
            assertThat(second).isEqualTo(first + 1);
            assertThat(third).isEqualTo(first + 2);
        }

        @Test
        @DisplayName("formats as PLN-0001 style string using the sequence value")
        void nextSlugSequenceValue_formatsCorrectly() {
            long seq  = planRepository.nextSlugSequenceValue();
            String slug = "PLN-%04d".formatted(seq);

            assertThat(slug).matches("^PLN-\\d{4}$");
        }
    }
}
