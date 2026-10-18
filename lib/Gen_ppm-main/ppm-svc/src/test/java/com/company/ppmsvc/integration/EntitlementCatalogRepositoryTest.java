package com.company.ppmsvc.integration;

import com.company.ppmsvc.entitlement.model.EntitlementType;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.entitlement.model.Entitlement;
import com.company.ppmsvc.entitlement.port.EntitlementRepositoryPort;
import com.company.ppmsvc.infrastructure.persistence.repository.EntitlementJpaRepository;
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
 * Repository-level integration tests for Entitlement Catalog persistence (PPM-04).
 *
 * <p>All tests run against a real PostgreSQL instance managed by Testcontainers.
 * Flyway applies all migrations (V001–V006) before the context starts, ensuring
 * {@code ppm_entitlements} and its constraints are in place.
 *
 * <p>No class-level {@code @Transactional} — constraint tests require the INSERT
 * to flush to the DB before the violation is observable.  Cleanup is via
 * {@code @AfterEach} hard-delete on the JPA repository.
 */
@DisplayName("Entitlement Catalog — Repository")
class EntitlementCatalogRepositoryTest extends AbstractContainerIntegrationTest {

    @Autowired
    private EntitlementRepositoryPort entitlementRepository;

    @Autowired
    private EntitlementJpaRepository entitlementJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        entitlementJpaRepository.deleteAll();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Entitlement buildEntitlement(String code, EntitlementType type) {
        Instant now = Instant.now();
        return Entitlement.builder()
            .id(UUID.randomUUID())
            // version intentionally omitted: null → persist() not merge()
            .code(code)
            .name("Test: " + code)
            .description("Integration test entitlement — " + code)
            .type(type)
            .active(true)
            .createdAt(now)
            .updatedAt(now)
            .createdBy(DEV_USER)
            .updatedBy(DEV_USER)
            .build();
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("persists a new entitlement and returns the saved state with version=0")
        void save_newEntitlement_persistsAndReturns() {
            Entitlement saved = entitlementRepository.save(
                buildEntitlement("max_internal_users", EntitlementType.QUOTA));

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getCode()).isEqualTo("max_internal_users");
            assertThat(saved.getName()).isEqualTo("Test: max_internal_users");
            assertThat(saved.getType()).isEqualTo(EntitlementType.QUOTA);
            assertThat(saved.isActive()).isTrue();
            assertThat(saved.getVersion()).isEqualTo(0L);
        }
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("returns entitlement when ID exists")
        void findById_exists_returnsEntitlement() {
            Entitlement saved = entitlementRepository.save(
                buildEntitlement("reporting_enabled", EntitlementType.BOOLEAN));

            Optional<Entitlement> found = entitlementRepository.findById(saved.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getCode()).isEqualTo("reporting_enabled");
        }

        @Test
        @DisplayName("returns empty when ID does not exist")
        void findById_missing_returnsEmpty() {
            assertThat(entitlementRepository.findById(UUID.randomUUID())).isEmpty();
        }
    }

    // ── findByCode ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByCode")
    class FindByCode {

        @Test
        @DisplayName("returns entitlement when code exists")
        void findByCode_exists_returnsEntitlement() {
            entitlementRepository.save(
                buildEntitlement("storage_gb", EntitlementType.QUOTA));

            Optional<Entitlement> found = entitlementRepository.findByCode("storage_gb");

            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("Test: storage_gb");
        }

        @Test
        @DisplayName("returns empty when code does not exist")
        void findByCode_missing_returnsEmpty() {
            assertThat(entitlementRepository.findByCode("nonexistent_code")).isEmpty();
        }
    }

    // ── existsByCode ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("existsByCode")
    class ExistsByCode {

        @Test
        @DisplayName("returns true when entitlement with code exists")
        void existsByCode_exists_returnsTrue() {
            entitlementRepository.save(
                buildEntitlement("api_requests_per_minute", EntitlementType.RATE_LIMIT));

            assertThat(entitlementRepository.existsByCode("api_requests_per_minute")).isTrue();
        }

        @Test
        @DisplayName("returns false when no entitlement with code exists")
        void existsByCode_missing_returnsFalse() {
            assertThat(entitlementRepository.existsByCode("unknown_entitlement")).isFalse();
        }
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("returns all active entitlements ordered by code ascending")
        void findAll_multiple_returnsAllOrderedByCode() {
            entitlementRepository.save(buildEntitlement("storage_gb",              EntitlementType.QUOTA));
            entitlementRepository.save(buildEntitlement("max_internal_users",      EntitlementType.QUOTA));
            entitlementRepository.save(buildEntitlement("api_requests_per_minute", EntitlementType.RATE_LIMIT));

            List<Entitlement> all = entitlementRepository.findAll();

            assertThat(all).hasSize(3);
            assertThat(all).extracting(Entitlement::getCode)
                .containsExactlyInAnyOrder(
                    "storage_gb", "max_internal_users", "api_requests_per_minute");
        }

        @Test
        @DisplayName("returns empty list when no entitlements exist")
        void findAll_empty_returnsEmptyList() {
            assertThat(entitlementRepository.findAll()).isEmpty();
        }
    }

    // ── unique code constraint ─────────────────────────────────────────────────

    @Nested
    @DisplayName("unique code constraint")
    class UniqueCodeConstraint {

        @Test
        @DisplayName("throws DataIntegrityViolationException when duplicate code is saved")
        void save_duplicateCode_throwsConstraintViolation() {
            entitlementRepository.save(buildEntitlement("max_active_projects", EntitlementType.QUOTA));

            assertThatThrownBy(() ->
                entitlementRepository.save(buildEntitlement("max_active_projects", EntitlementType.QUOTA)))
                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("constraint violation message contains index name for GlobalExceptionHandler detection")
        void save_duplicateCode_exceptionContainsConstraintName() {
            entitlementRepository.save(buildEntitlement("reporting_enabled", EntitlementType.BOOLEAN));

            assertThatThrownBy(() ->
                entitlementRepository.save(buildEntitlement("reporting_enabled", EntitlementType.BOOLEAN)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_ppm_entitlements_code");
        }
    }

    // ── audit fields ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("audit fields")
    class AuditFields {

        @Test
        @DisplayName("createdAt, updatedAt, createdBy, updatedBy are populated after save")
        void save_newEntitlement_populatesAuditFields() {
            Instant before = Instant.now().minusSeconds(1);

            Entitlement saved = entitlementRepository.save(
                buildEntitlement("sso_enabled", EntitlementType.BOOLEAN));

            assertThat(saved.getCreatedAt()).isAfter(before);
            assertThat(saved.getUpdatedAt()).isAfter(before);
            assertThat(saved.getCreatedBy()).isEqualTo(DEV_USER);
            assertThat(saved.getUpdatedBy()).isEqualTo(DEV_USER);
        }

        @Test
        @DisplayName("version is 0 after initial save")
        void save_newEntitlement_versionIsZero() {
            Entitlement saved = entitlementRepository.save(
                buildEntitlement("max_projects", EntitlementType.QUOTA));

            assertThat(saved.getVersion()).isEqualTo(0L);
        }
    }

    // ── wire value persistence ────────────────────────────────────────────────

    @Nested
    @DisplayName("wire value persistence")
    class WireValuePersistence {

        @Test
        @DisplayName("EntitlementType.BOOLEAN stored as 'boolean', not 'BOOLEAN'")
        void save_booleanType_storedAsWireValue() {
            Entitlement saved = entitlementRepository.save(
                buildEntitlement("white_label_enabled", EntitlementType.BOOLEAN));

            String stored = jdbcTemplate.queryForObject(
                "SELECT type FROM ppm_entitlements WHERE id = ?",
                String.class, saved.getId());

            assertThat(stored).isEqualTo("boolean");
            assertThat(stored).doesNotContain("BOOLEAN");
        }

        @Test
        @DisplayName("EntitlementType.QUOTA stored as 'quota', not 'QUOTA'")
        void save_quotaType_storedAsWireValue() {
            Entitlement saved = entitlementRepository.save(
                buildEntitlement("max_seats", EntitlementType.QUOTA));

            String stored = jdbcTemplate.queryForObject(
                "SELECT type FROM ppm_entitlements WHERE id = ?",
                String.class, saved.getId());

            assertThat(stored).isEqualTo("quota");
        }

        @Test
        @DisplayName("EntitlementType.RATE_LIMIT stored as 'rate_limit', not 'RATE_LIMIT'")
        void save_rateLimitType_storedAsWireValue() {
            Entitlement saved = entitlementRepository.save(
                buildEntitlement("api_rpm", EntitlementType.RATE_LIMIT));

            String stored = jdbcTemplate.queryForObject(
                "SELECT type FROM ppm_entitlements WHERE id = ?",
                String.class, saved.getId());

            assertThat(stored).isEqualTo("rate_limit");
        }

        @Test
        @DisplayName("type is reconstituted correctly from DB wire value")
        void findByCode_afterSave_typeReconstitutedCorrectly() {
            entitlementRepository.save(buildEntitlement("audit_export", EntitlementType.BOOLEAN));

            Optional<Entitlement> found = entitlementRepository.findByCode("audit_export");

            assertThat(found).isPresent();
            assertThat(found.get().getType()).isEqualTo(EntitlementType.BOOLEAN);
            assertThat(found.get().getType().getValue()).isEqualTo("boolean");
        }
    }

    // ── optimistic locking ────────────────────────────────────────────────────

    @Nested
    @DisplayName("optimistic locking")
    class OptimisticLocking {

        @Test
        @DisplayName("throws OptimisticLockingFailureException when stale version is saved")
        void save_staleVersion_throwsOptimisticLockException() {
            Entitlement original = entitlementRepository.save(
                buildEntitlement("max_users_lock_test", EntitlementType.QUOTA));

            // First update — version 0 → DB version becomes 1
            Entitlement firstUpdate = Entitlement.builder()
                .id(original.getId())
                .version(original.getVersion())  // 0
                .code(original.getCode())
                .name("Max Users v1")
                .description(original.getDescription())
                .type(original.getType())
                .active(original.isActive())
                .createdAt(original.getCreatedAt())
                .updatedAt(Instant.now())
                .createdBy(original.getCreatedBy())
                .updatedBy(DEV_USER)
                .build();
            entitlementRepository.save(firstUpdate);

            // Second update from stale version 0 — must fail
            Entitlement staleUpdate = Entitlement.builder()
                .id(original.getId())
                .version(original.getVersion())  // still 0, DB has 1
                .code(original.getCode())
                .name("Max Users stale")
                .description(original.getDescription())
                .type(original.getType())
                .active(original.isActive())
                .createdAt(original.getCreatedAt())
                .updatedAt(Instant.now())
                .createdBy(original.getCreatedBy())
                .updatedBy(DEV_USER)
                .build();

            assertThatThrownBy(() -> entitlementRepository.save(staleUpdate))
                .isInstanceOf(OptimisticLockingFailureException.class);
        }
    }

    // ── soft delete ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("soft delete")
    class SoftDelete {

        @Test
        @DisplayName("softDelete hides entitlement from findById")
        void softDelete_existingEntitlement_hiddenFromFindById() {
            Entitlement saved = entitlementRepository.save(
                buildEntitlement("hidden_feature", EntitlementType.BOOLEAN));

            entitlementRepository.softDelete(saved.getId(), DEV_USER);

            assertThat(entitlementRepository.findById(saved.getId())).isEmpty();
        }

        @Test
        @DisplayName("softDelete hides entitlement from findAll")
        void softDelete_existingEntitlement_hiddenFromFindAll() {
            Entitlement saved = entitlementRepository.save(
                buildEntitlement("deleted_quota", EntitlementType.QUOTA));

            entitlementRepository.softDelete(saved.getId(), DEV_USER);

            assertThat(entitlementRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("softDelete makes existsByCode return false")
        void softDelete_existingEntitlement_existsByCodeReturnsFalse() {
            Entitlement saved = entitlementRepository.save(
                buildEntitlement("removable_rate", EntitlementType.RATE_LIMIT));

            entitlementRepository.softDelete(saved.getId(), DEV_USER);

            assertThat(entitlementRepository.existsByCode("removable_rate")).isFalse();
        }

        @Test
        @DisplayName("active filtering — soft-deleted entitlement excluded from findAll but active one included")
        void softDelete_activeFiltering_onlyActiveReturned() {
            Entitlement active  = entitlementRepository.save(buildEntitlement("active_ent",  EntitlementType.BOOLEAN));
            Entitlement deleted = entitlementRepository.save(buildEntitlement("deleted_ent", EntitlementType.QUOTA));

            entitlementRepository.softDelete(deleted.getId(), DEV_USER);

            List<Entitlement> all = entitlementRepository.findAll();
            assertThat(all).hasSize(1);
            assertThat(all.get(0).getId()).isEqualTo(active.getId());
        }

        @Test
        @DisplayName("soft-deleted code can be reused by a new entitlement (partial index)")
        void softDelete_deletedCode_canBeReused() {
            Entitlement first = entitlementRepository.save(
                buildEntitlement("reusable_code", EntitlementType.QUOTA));
            entitlementRepository.softDelete(first.getId(), DEV_USER);

            // Partial unique index is WHERE deleted_at IS NULL — reuse must succeed
            Entitlement second = entitlementRepository.save(
                buildEntitlement("reusable_code", EntitlementType.QUOTA));

            assertThat(second.getId()).isNotEqualTo(first.getId());
            assertThat(entitlementRepository.findByCode("reusable_code")).isPresent();
        }

        @Test
        @DisplayName("softDelete on unknown ID throws ResourceNotFoundException")
        void softDelete_unknownId_throwsNotFoundException() {
            assertThatThrownBy(() ->
                entitlementRepository.softDelete(UUID.randomUUID(), DEV_USER))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
