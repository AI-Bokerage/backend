package com.company.ppmsvc.integration;

import com.company.ppmsvc.plan.model.PlanVisibility;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.plan.model.PlanVersion;
import com.company.ppmsvc.plan.port.PlanVersionRepositoryPort;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.infrastructure.persistence.repository.PlanJpaRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * Repository-level integration tests for Plan Version persistence (PPM-06 Phase 1).
 *
 * <p>All tests run against a real PostgreSQL instance managed by Testcontainers.
 * Flyway applies V001–V008 before the context starts, ensuring {@code ppm_plan_versions}
 * and all its constraints are in place.
 *
 * <p>No class-level {@code @Transactional} — constraint tests require the INSERT to
 * flush to the DB before the violation is observable.  Cleanup is via {@code @AfterEach}
 * hard-delete in FK-safe order: versions → plans.
 */
@DisplayName("Plan Version — Repository")
class PlanVersionRepositoryTest extends AbstractContainerIntegrationTest {

    @Autowired private PlanVersionRepositoryPort planVersionRepository;
    @Autowired private PlanRepositoryPort        planRepository;
    @Autowired private PlanJpaRepository         planJpaRepository;
    @Autowired private JdbcTemplate              jdbcTemplate;

    // Seeded plan — every test creates versions against this plan
    private UUID planId;

    @BeforeEach
    void seedPlan() {
        planId = UUID.randomUUID();
        Instant now = Instant.now();
        planRepository.save(Plan.builder()
            .id(planId)
            .code("PV-TEST-" + planId.toString().substring(0, 8))
            .slug("PV-" + planId.toString().substring(0, 8))
            .name("Version Test Plan")
            .visibility(PlanVisibility.PUBLIC)
            .trialDays(0).active(true)
            .createdAt(now).updatedAt(now)
            .build());
    }

    @AfterEach
    void cleanUp() {
        // Native SQL bypasses @SQLRestriction so soft-deleted version rows are also removed,
        // preventing FK violations when the plan row is subsequently hard-deleted.
        jdbcTemplate.execute("DELETE FROM ppm_plan_versions");
        planJpaRepository.deleteAll();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private PlanVersion buildVersion(UUID pid, Integer versionNo, LocalDate effectiveFrom) {
        Instant now = Instant.now();
        return PlanVersion.builder()
            .id(UUID.randomUUID())
            // version intentionally omitted: null → persist() not merge()
            .planId(pid)
            .versionNo(versionNo)
            .effectiveFrom(effectiveFrom)
            .effectiveTo(null)
            .active(true)
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build();
    }

    private PlanVersion buildVersion(Integer versionNo, LocalDate effectiveFrom) {
        return buildVersion(planId, versionNo, effectiveFrom);
    }

    private PlanVersion buildVersion(Integer versionNo) {
        return buildVersion(planId, versionNo, LocalDate.of(2026, versionNo, 1));
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("persists a new version row and returns saved state with version=0")
        void save_newVersion_persistsAndReturns() {
            PlanVersion saved = planVersionRepository.save(buildVersion(1));

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getPlanId()).isEqualTo(planId);
            assertThat(saved.getVersionNo()).isEqualTo(1);
            assertThat(saved.getEffectiveFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(saved.getEffectiveTo()).isNull();
            assertThat(saved.isActive()).isTrue();
            assertThat(saved.getVersion()).isEqualTo(0L);
        }

        @Test
        @DisplayName("multiple versions for same plan coexist as separate rows")
        void save_multipleVersions_coexistAsSeparateRows() {
            planVersionRepository.save(buildVersion(1));
            planVersionRepository.save(buildVersion(2));
            planVersionRepository.save(buildVersion(3));

            assertThat(planVersionRepository.findByPlanId(planId)).hasSize(3);
        }
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("returns version when ID exists")
        void findById_exists_returnsVersion() {
            PlanVersion saved = planVersionRepository.save(buildVersion(1));

            Optional<PlanVersion> found = planVersionRepository.findById(saved.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getVersionNo()).isEqualTo(1);
            assertThat(found.get().getPlanId()).isEqualTo(planId);
        }

        @Test
        @DisplayName("returns empty when ID does not exist")
        void findById_missing_returnsEmpty() {
            assertThat(planVersionRepository.findById(UUID.randomUUID())).isEmpty();
        }

        @Test
        @DisplayName("returns empty for soft-deleted version")
        void findById_softDeleted_returnsEmpty() {
            PlanVersion saved = planVersionRepository.save(buildVersion(1));
            planVersionRepository.softDelete(saved.getId(), DEV_USER);

            assertThat(planVersionRepository.findById(saved.getId())).isEmpty();
        }
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("returns all active version rows ordered by planId asc, versionNo desc")
        void findAll_multiple_returnsAll() {
            planVersionRepository.save(buildVersion(1));
            planVersionRepository.save(buildVersion(2));
            planVersionRepository.save(buildVersion(3));

            List<PlanVersion> all = planVersionRepository.findAll();
            assertThat(all).hasSize(3);
        }

        @Test
        @DisplayName("returns empty list when no versions exist")
        void findAll_empty_returnsEmptyList() {
            assertThat(planVersionRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("excludes soft-deleted rows")
        void findAll_softDeleted_excluded() {
            PlanVersion v1 = planVersionRepository.save(buildVersion(1));
            planVersionRepository.save(buildVersion(2));
            planVersionRepository.softDelete(v1.getId(), DEV_USER);

            assertThat(planVersionRepository.findAll()).hasSize(1);
        }
    }

    // ── findByPlanId ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByPlanId")
    class FindByPlanId {

        @Test
        @DisplayName("returns only versions for the given plan")
        void findByPlanId_returnsMatchingVersions() {
            planVersionRepository.save(buildVersion(1));
            planVersionRepository.save(buildVersion(2));

            // Second plan — its versions must not appear in first plan's results
            UUID otherPlanId = UUID.randomUUID();
            Instant now = Instant.now();
            planRepository.save(Plan.builder()
                .id(otherPlanId)
                .code("PV-OTHER-" + otherPlanId.toString().substring(0, 8))
                .slug("PVO-" + otherPlanId.toString().substring(0, 8))
                .name("Other Plan")
                .visibility(PlanVisibility.PUBLIC)
                .trialDays(0).active(true)
                .createdAt(now).updatedAt(now)
                .build());
            planVersionRepository.save(buildVersion(otherPlanId, 1, LocalDate.of(2026, 1, 1)));

            List<PlanVersion> results = planVersionRepository.findByPlanId(planId);
            assertThat(results).hasSize(2);
            assertThat(results).allMatch(v -> v.getPlanId().equals(planId));
        }

        @Test
        @DisplayName("excludes soft-deleted rows for the plan")
        void findByPlanId_softDeleted_excluded() {
            PlanVersion v1 = planVersionRepository.save(buildVersion(1));
            planVersionRepository.save(buildVersion(2));
            planVersionRepository.softDelete(v1.getId(), DEV_USER);

            assertThat(planVersionRepository.findByPlanId(planId)).hasSize(1);
        }
    }

    // ── findByPlanIdOrderByVersionNoDesc ──────────────────────────────────────

    @Nested
    @DisplayName("findByPlanIdOrderByVersionNoDesc")
    class FindByPlanIdOrderByVersionNoDesc {

        @Test
        @DisplayName("returns versions in descending version_no order: v5, v4, v3, v2, v1")
        void findByPlanIdOrderByVersionNoDesc_returnsDescending() {
            planVersionRepository.save(buildVersion(1));
            planVersionRepository.save(buildVersion(2));
            planVersionRepository.save(buildVersion(3));
            planVersionRepository.save(buildVersion(4));
            planVersionRepository.save(buildVersion(5));

            List<PlanVersion> results = planVersionRepository.findByPlanIdOrderByVersionNoDesc(planId);

            assertThat(results).hasSize(5);
            assertThat(results.get(0).getVersionNo()).isEqualTo(5);
            assertThat(results.get(1).getVersionNo()).isEqualTo(4);
            assertThat(results.get(2).getVersionNo()).isEqualTo(3);
            assertThat(results.get(3).getVersionNo()).isEqualTo(2);
            assertThat(results.get(4).getVersionNo()).isEqualTo(1);
        }

        @Test
        @DisplayName("returns empty list when plan has no active versions")
        void findByPlanIdOrderByVersionNoDesc_empty_returnsEmptyList() {
            assertThat(planVersionRepository.findByPlanIdOrderByVersionNoDesc(planId)).isEmpty();
        }
    }

    // ── findByPlanIdAndVersionNo ──────────────────────────────────────────────

    @Nested
    @DisplayName("findByPlanIdAndVersionNo")
    class FindByPlanIdAndVersionNo {

        @Test
        @DisplayName("returns version when (planId, versionNo) matches")
        void findByPlanIdAndVersionNo_found_returnsVersion() {
            planVersionRepository.save(buildVersion(1));
            planVersionRepository.save(buildVersion(2));

            Optional<PlanVersion> found = planVersionRepository.findByPlanIdAndVersionNo(planId, 2);

            assertThat(found).isPresent();
            assertThat(found.get().getVersionNo()).isEqualTo(2);
        }

        @Test
        @DisplayName("returns empty when versionNo does not exist for the plan")
        void findByPlanIdAndVersionNo_missing_returnsEmpty() {
            assertThat(planVersionRepository.findByPlanIdAndVersionNo(planId, 99)).isEmpty();
        }
    }

    // ── existsByPlanIdAndVersionNo ────────────────────────────────────────────

    @Nested
    @DisplayName("existsByPlanIdAndVersionNo")
    class ExistsByPlanIdAndVersionNo {

        @Test
        @DisplayName("returns true when version exists")
        void exists_found_returnsTrue() {
            planVersionRepository.save(buildVersion(1));

            assertThat(planVersionRepository.existsByPlanIdAndVersionNo(planId, 1)).isTrue();
        }

        @Test
        @DisplayName("returns false when version does not exist")
        void exists_missing_returnsFalse() {
            assertThat(planVersionRepository.existsByPlanIdAndVersionNo(planId, 1)).isFalse();
        }

        @Test
        @DisplayName("returns false after soft-delete")
        void exists_softDeleted_returnsFalse() {
            PlanVersion saved = planVersionRepository.save(buildVersion(1));
            planVersionRepository.softDelete(saved.getId(), DEV_USER);

            assertThat(planVersionRepository.existsByPlanIdAndVersionNo(planId, 1)).isFalse();
        }
    }

    // ── softDelete ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("softDelete")
    class SoftDelete {

        @Test
        @DisplayName("soft-deleted version is hidden from findById")
        void softDelete_hiddenFromFindById() {
            PlanVersion saved = planVersionRepository.save(buildVersion(1));
            planVersionRepository.softDelete(saved.getId(), DEV_USER);

            assertThat(planVersionRepository.findById(saved.getId())).isEmpty();
        }

        @Test
        @DisplayName("soft-deleted version is hidden from findAll")
        void softDelete_hiddenFromFindAll() {
            PlanVersion saved = planVersionRepository.save(buildVersion(1));
            planVersionRepository.softDelete(saved.getId(), DEV_USER);

            assertThat(planVersionRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("soft-deleted version is hidden from findByPlanId")
        void softDelete_hiddenFromFindByPlanId() {
            PlanVersion saved = planVersionRepository.save(buildVersion(1));
            planVersionRepository.softDelete(saved.getId(), DEV_USER);

            assertThat(planVersionRepository.findByPlanId(planId)).isEmpty();
        }

        @Test
        @DisplayName("soft-deleted version is invisible to existsByPlanIdAndVersionNo")
        void softDelete_hiddenFromExists() {
            PlanVersion saved = planVersionRepository.save(buildVersion(1));
            planVersionRepository.softDelete(saved.getId(), DEV_USER);

            assertThat(planVersionRepository.existsByPlanIdAndVersionNo(planId, 1)).isFalse();
        }

        @Test
        @DisplayName("soft-delete on unknown ID throws ResourceNotFoundException")
        void softDelete_unknownId_throwsResourceNotFoundException() {
            UUID unknownId = UUID.randomUUID();

            assertThatThrownBy(() -> planVersionRepository.softDelete(unknownId, DEV_USER))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("physical row retained after soft-delete")
        void softDelete_physicalRowRetained() {
            PlanVersion saved = planVersionRepository.save(buildVersion(1));
            planVersionRepository.softDelete(saved.getId(), DEV_USER);

            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ppm_plan_versions WHERE id = ?",
                Integer.class, saved.getId());
            assertThat(count).isEqualTo(1);
        }
    }

    // ── UniqueConstraint ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("UniqueConstraint")
    class UniqueConstraint {

        @Test
        @DisplayName("duplicate (plan_id, version_no) among active rows fails")
        void duplicate_planIdAndVersionNo_fails() {
            planVersionRepository.save(buildVersion(1, LocalDate.of(2026, 1, 1)));

            assertThatThrownBy(() ->
                planVersionRepository.save(buildVersion(1, LocalDate.of(2026, 6, 1))))
                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("duplicate (plan_id, effective_from) among active rows fails")
        void duplicate_planIdAndEffectiveFrom_fails() {
            planVersionRepository.save(buildVersion(1, LocalDate.of(2026, 1, 1)));

            assertThatThrownBy(() ->
                planVersionRepository.save(buildVersion(2, LocalDate.of(2026, 1, 1))))
                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("reuse of soft-deleted version_no succeeds")
        void reuseAfterSoftDelete_versionNo_succeeds() {
            PlanVersion v1 = planVersionRepository.save(buildVersion(1, LocalDate.of(2026, 1, 1)));
            planVersionRepository.softDelete(v1.getId(), DEV_USER);

            // Same version_no, different effective_from — must succeed
            PlanVersion reused = planVersionRepository.save(buildVersion(1, LocalDate.of(2027, 1, 1)));
            assertThat(reused.getId()).isNotEqualTo(v1.getId());
            assertThat(reused.getVersionNo()).isEqualTo(1);
        }

        @Test
        @DisplayName("reuse of soft-deleted effective_from succeeds")
        void reuseAfterSoftDelete_effectiveFrom_succeeds() {
            PlanVersion v1 = planVersionRepository.save(buildVersion(1, LocalDate.of(2026, 1, 1)));
            planVersionRepository.softDelete(v1.getId(), DEV_USER);

            // Different version_no, same effective_from — must succeed
            PlanVersion reused = planVersionRepository.save(buildVersion(2, LocalDate.of(2026, 1, 1)));
            assertThat(reused.getId()).isNotEqualTo(v1.getId());
            assertThat(reused.getEffectiveFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
        }
    }

    // ── AuditFields ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("AuditFields")
    class AuditFields {

        @Test
        @DisplayName("all audit fields populated on save")
        void auditFields_populated() {
            PlanVersion saved = planVersionRepository.save(buildVersion(1));

            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getUpdatedAt()).isNotNull();
            assertThat(saved.getCreatedBy()).isEqualTo(DEV_USER);
            assertThat(saved.getUpdatedBy()).isEqualTo(DEV_USER);
        }
    }

    // ── OptimisticLocking ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("OptimisticLocking")
    class OptimisticLocking {

        @Test
        @DisplayName("saving a stale version throws OptimisticLockingFailureException")
        void optimisticLocking_staleVersion_throws() {
            PlanVersion saved = planVersionRepository.save(buildVersion(1));

            // Build a domain object with the same ID but a stale version counter
            PlanVersion stale = PlanVersion.builder()
                .id(saved.getId())
                .version(saved.getVersion())  // version=0 — stale after the first save
                .planId(saved.getPlanId())
                .versionNo(saved.getVersionNo())
                .effectiveFrom(saved.getEffectiveFrom())
                .effectiveTo(null)
                .active(false)  // mutated to trigger an update
                .createdAt(saved.getCreatedAt()).updatedAt(Instant.now())
                .createdBy(DEV_USER).updatedBy(DEV_USER)
                .build();

            // Force a DB increment by doing a legitimate save first
            PlanVersion updated = planVersionRepository.save(
                PlanVersion.builder()
                    .id(saved.getId())
                    .version(saved.getVersion())
                    .planId(saved.getPlanId())
                    .versionNo(saved.getVersionNo())
                    .effectiveFrom(saved.getEffectiveFrom())
                    .effectiveTo(null)
                    .active(false)
                    .createdAt(saved.getCreatedAt()).updatedAt(Instant.now())
                    .createdBy(DEV_USER).updatedBy(DEV_USER)
                    .build());
            assertThat(updated.getVersion()).isEqualTo(1L);

            // Now try to save stale — version=0 is now stale
            assertThatThrownBy(() -> planVersionRepository.save(stale))
                .isInstanceOf(OptimisticLockingFailureException.class);
        }
    }

    // ── FkIntegrity ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("FkIntegrity")
    class FkIntegrity {

        @Test
        @DisplayName("saving a version with an unknown plan_id fails with FK violation")
        void save_unknownPlanId_failsFkConstraint() {
            UUID unknownPlanId = UUID.randomUUID();

            assertThatThrownBy(() ->
                planVersionRepository.save(buildVersion(unknownPlanId, 1, LocalDate.of(2026, 1, 1))))
                .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
