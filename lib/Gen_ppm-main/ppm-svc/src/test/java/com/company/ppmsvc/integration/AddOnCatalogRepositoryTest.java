package com.company.ppmsvc.integration;

import com.company.ppmsvc.addon.model.AddOnType;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.addon.model.AddOn;
import com.company.ppmsvc.addon.port.AddOnRepositoryPort;
import com.company.ppmsvc.infrastructure.persistence.repository.AddOnJpaRepository;
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
 * Repository-level integration tests for the Add-On Catalog persistence (PPM-11).
 *
 * <p>All tests run against a real PostgreSQL instance managed by Testcontainers.
 * Flyway applies V001–V010 before the context starts, ensuring {@code ppm_add_ons}
 * and all its constraints are in place.
 *
 * <p>No class-level {@code @Transactional} — constraint tests require the INSERT
 * to flush before the violation is observable.  Cleanup via {@code @AfterEach}
 * using native SQL to bypass {@code @SQLRestriction}.
 */
@DisplayName("Add-On Catalog — Repository")
class AddOnCatalogRepositoryTest extends AbstractContainerIntegrationTest {

    @Autowired AddOnRepositoryPort  addOnRepository;
    @Autowired AddOnJpaRepository   addOnJpaRepository;
    @Autowired JdbcTemplate         jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM ppm_add_ons");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private AddOn buildAddOn(String code, AddOnType type) {
        Instant now = Instant.now();
        return AddOn.builder()
                .id(UUID.randomUUID())
                .code(code)
                .name("Add-On " + code)
                .description("Description for " + code)
                .type(type)
                .active(true)
                .createdAt(now).updatedAt(now)
                .createdBy(DEV_USER).updatedBy(DEV_USER)
                .build();
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("persists a new add-on and returns the saved state with version=0")
        void save_newAddOn_persistsAndReturns() {
            AddOn saved = addOnRepository.save(buildAddOn("EXTRA_USERS_10", AddOnType.QUOTA));

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getCode()).isEqualTo("EXTRA_USERS_10");
            assertThat(saved.getName()).isEqualTo("Add-On EXTRA_USERS_10");
            assertThat(saved.getType()).isEqualTo(AddOnType.QUOTA);
            assertThat(saved.isActive()).isTrue();
            assertThat(saved.getVersion()).isEqualTo(0L);
        }

        @Test
        @DisplayName("add-ons with different types can coexist")
        void save_differentTypes_coexist() {
            addOnRepository.save(buildAddOn("SSO_ADDON",     AddOnType.FEATURE));
            addOnRepository.save(buildAddOn("EXTRA_STORAGE", AddOnType.QUOTA));
            addOnRepository.save(buildAddOn("PREMIUM_SUPPORT", AddOnType.SERVICE));

            assertThat(addOnRepository.findAll()).hasSize(3);
        }
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("returns add-on when ID exists")
        void findById_exists_returnsAddOn() {
            AddOn saved = addOnRepository.save(buildAddOn("STORAGE_50_GB", AddOnType.QUOTA));

            Optional<AddOn> found = addOnRepository.findById(saved.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getCode()).isEqualTo("STORAGE_50_GB");
            assertThat(found.get().getType()).isEqualTo(AddOnType.QUOTA);
        }

        @Test
        @DisplayName("returns empty when ID does not exist")
        void findById_missing_returnsEmpty() {
            assertThat(addOnRepository.findById(UUID.randomUUID())).isEmpty();
        }
    }

    // ── findByCode ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByCode")
    class FindByCode {

        @Test
        @DisplayName("returns add-on matching the exact code")
        void findByCode_exists_returnsAddOn() {
            addOnRepository.save(buildAddOn("WHITE_LABEL_ADDON", AddOnType.FEATURE));

            Optional<AddOn> found = addOnRepository.findByCode("WHITE_LABEL_ADDON");

            assertThat(found).isPresent();
            assertThat(found.get().getCode()).isEqualTo("WHITE_LABEL_ADDON");
        }

        @Test
        @DisplayName("returns empty when code does not exist")
        void findByCode_missing_returnsEmpty() {
            assertThat(addOnRepository.findByCode("GHOST_CODE")).isEmpty();
        }

        @Test
        @DisplayName("code lookup is case-sensitive")
        void findByCode_wrongCase_returnsEmpty() {
            addOnRepository.save(buildAddOn("SSO_ADDON", AddOnType.FEATURE));

            assertThat(addOnRepository.findByCode("sso_addon")).isEmpty();
        }
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("returns all active add-ons ordered by code ascending")
        void findAll_multiple_returnsSortedByCode() {
            addOnRepository.save(buildAddOn("ZEBRA_ADDON",  AddOnType.FEATURE));
            addOnRepository.save(buildAddOn("ALPHA_ADDON",  AddOnType.QUOTA));
            addOnRepository.save(buildAddOn("MIDDLE_ADDON", AddOnType.SERVICE));

            List<AddOn> all = addOnRepository.findAll();

            assertThat(all).hasSize(3);
            assertThat(all).extracting(AddOn::getCode)
                    .containsExactly("ALPHA_ADDON", "MIDDLE_ADDON", "ZEBRA_ADDON");
        }

        @Test
        @DisplayName("returns empty list when no add-ons exist")
        void findAll_empty_returnsEmptyList() {
            assertThat(addOnRepository.findAll()).isEmpty();
        }
    }

    // ── existsByCode ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("existsByCode")
    class ExistsByCode {

        @Test
        @DisplayName("returns true when active add-on with code exists")
        void existsByCode_present_returnsTrue() {
            addOnRepository.save(buildAddOn("EXTRA_USERS_25", AddOnType.QUOTA));

            assertThat(addOnRepository.existsByCode("EXTRA_USERS_25")).isTrue();
        }

        @Test
        @DisplayName("returns false when code does not exist")
        void existsByCode_absent_returnsFalse() {
            assertThat(addOnRepository.existsByCode("GHOST_CODE")).isFalse();
        }
    }

    // ── unique code constraint ─────────────────────────────────────────────────

    @Nested
    @DisplayName("unique code constraint")
    class UniqueCodeConstraint {

        @Test
        @DisplayName("throws DataIntegrityViolationException on duplicate code")
        void save_duplicateCode_throwsConstraintViolation() {
            addOnRepository.save(buildAddOn("EXTRA_USERS_10", AddOnType.QUOTA));

            assertThatThrownBy(() ->
                addOnRepository.save(buildAddOn("EXTRA_USERS_10", AddOnType.QUOTA)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("constraint violation message contains index name for GlobalExceptionHandler detection")
        void save_duplicateCode_exceptionContainsIndexName() {
            addOnRepository.save(buildAddOn("STORAGE_100_GB", AddOnType.QUOTA));

            assertThatThrownBy(() ->
                addOnRepository.save(buildAddOn("STORAGE_100_GB", AddOnType.SERVICE)))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("uq_ppm_add_ons_code");
        }

        @Test
        @DisplayName("soft-deleted code can be reused by a new add-on (partial index)")
        void save_sameCodeAfterSoftDelete_succeeds() {
            AddOn first = addOnRepository.save(buildAddOn("EXTRA_USERS_10", AddOnType.QUOTA));

            addOnRepository.softDelete(first.getId(), DEV_USER);

            AddOn second = addOnRepository.save(buildAddOn("EXTRA_USERS_10", AddOnType.QUOTA));

            assertThat(second.getId()).isNotEqualTo(first.getId());
            assertThat(addOnRepository.findByCode("EXTRA_USERS_10")).isPresent();
        }
    }

    // ── soft delete ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("soft delete")
    class SoftDelete {

        @Test
        @DisplayName("softDelete hides add-on from findById")
        void softDelete_hiddenFromFindById() {
            AddOn saved = addOnRepository.save(buildAddOn("SSO_ADDON", AddOnType.FEATURE));

            addOnRepository.softDelete(saved.getId(), DEV_USER);

            assertThat(addOnRepository.findById(saved.getId())).isEmpty();
        }

        @Test
        @DisplayName("softDelete hides add-on from findByCode")
        void softDelete_hiddenFromFindByCode() {
            AddOn saved = addOnRepository.save(buildAddOn("SSO_ADDON", AddOnType.FEATURE));

            addOnRepository.softDelete(saved.getId(), DEV_USER);

            assertThat(addOnRepository.findByCode("SSO_ADDON")).isEmpty();
        }

        @Test
        @DisplayName("softDelete hides add-on from findAll")
        void softDelete_hiddenFromFindAll() {
            AddOn saved = addOnRepository.save(buildAddOn("SSO_ADDON", AddOnType.FEATURE));

            addOnRepository.softDelete(saved.getId(), DEV_USER);

            assertThat(addOnRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("softDelete makes existsByCode() return false")
        void softDelete_existsByCodeReturnsFalse() {
            AddOn saved = addOnRepository.save(buildAddOn("SSO_ADDON", AddOnType.FEATURE));

            addOnRepository.softDelete(saved.getId(), DEV_USER);

            assertThat(addOnRepository.existsByCode("SSO_ADDON")).isFalse();
        }

        @Test
        @DisplayName("soft-deleted row physically retained in DB (deleted_at is set)")
        void softDelete_physicalRowRetained() {
            AddOn saved = addOnRepository.save(buildAddOn("SSO_ADDON", AddOnType.FEATURE));

            addOnRepository.softDelete(saved.getId(), DEV_USER);

            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ppm_add_ons WHERE id = ?",
                Integer.class, saved.getId());
            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("softDelete on unknown ID throws ResourceNotFoundException")
        void softDelete_unknownId_throwsNotFoundException() {
            assertThatThrownBy(() ->
                addOnRepository.softDelete(UUID.randomUUID(), DEV_USER))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("active filtering — soft-deleted excluded; active one still returned")
        void softDelete_activeFiltering_onlyActiveReturned() {
            AddOn active  = addOnRepository.save(buildAddOn("ACTIVE_ADDON",  AddOnType.FEATURE));
            AddOn deleted = addOnRepository.save(buildAddOn("DELETED_ADDON", AddOnType.QUOTA));

            addOnRepository.softDelete(deleted.getId(), DEV_USER);

            List<AddOn> all = addOnRepository.findAll();
            assertThat(all).hasSize(1);
            assertThat(all.get(0).getId()).isEqualTo(active.getId());
        }
    }

    // ── audit fields ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("audit fields")
    class AuditFields {

        @Test
        @DisplayName("createdAt, updatedAt, createdBy, updatedBy populated after save")
        void save_newAddOn_populatesAuditFields() {
            Instant before = Instant.now().minusSeconds(1);

            AddOn saved = addOnRepository.save(buildAddOn("AUDIT_ADDON", AddOnType.SERVICE));

            assertThat(saved.getCreatedAt()).isAfter(before);
            assertThat(saved.getUpdatedAt()).isAfter(before);
            assertThat(saved.getCreatedBy()).isEqualTo(DEV_USER);
            assertThat(saved.getUpdatedBy()).isEqualTo(DEV_USER);
        }

        @Test
        @DisplayName("version is 0 after initial save")
        void save_newAddOn_versionIsZero() {
            AddOn saved = addOnRepository.save(buildAddOn("VERSION_ADDON", AddOnType.QUOTA));

            assertThat(saved.getVersion()).isEqualTo(0L);
        }
    }

    // ── AddOnType wire value persistence ──────────────────────────────────────

    @Nested
    @DisplayName("AddOnType wire value persistence")
    class AddOnTypeWireValue {

        @Test
        @DisplayName("QUOTA stored as 'quota', not 'QUOTA'")
        void save_quota_storedAsWireValue() {
            AddOn saved = addOnRepository.save(buildAddOn("EXTRA_USERS_10", AddOnType.QUOTA));

            String stored = jdbcTemplate.queryForObject(
                "SELECT type FROM ppm_add_ons WHERE id = ?",
                String.class, saved.getId());

            assertThat(stored).isEqualTo("quota");
        }

        @Test
        @DisplayName("FEATURE stored as 'feature', not 'FEATURE'")
        void save_feature_storedAsWireValue() {
            AddOn saved = addOnRepository.save(buildAddOn("SSO_ADDON", AddOnType.FEATURE));

            String stored = jdbcTemplate.queryForObject(
                "SELECT type FROM ppm_add_ons WHERE id = ?",
                String.class, saved.getId());

            assertThat(stored).isEqualTo("feature");
        }

        @Test
        @DisplayName("SERVICE stored as 'service', not 'SERVICE'")
        void save_service_storedAsWireValue() {
            AddOn saved = addOnRepository.save(buildAddOn("PREMIUM_SUPPORT", AddOnType.SERVICE));

            String stored = jdbcTemplate.queryForObject(
                "SELECT type FROM ppm_add_ons WHERE id = ?",
                String.class, saved.getId());

            assertThat(stored).isEqualTo("service");
        }

        @Test
        @DisplayName("AddOnType reconstituted correctly from DB wire value")
        void findById_afterSave_typeReconstitutedCorrectly() {
            AddOn saved = addOnRepository.save(buildAddOn("EXTRA_USERS_10", AddOnType.QUOTA));

            Optional<AddOn> found = addOnRepository.findById(saved.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getType()).isEqualTo(AddOnType.QUOTA);
            assertThat(found.get().getType().getValue()).isEqualTo("quota");
        }

        @Test
        @DisplayName("fromValue — throws IllegalArgumentException for unknown wire value")
        void fromValue_unknownValue_throwsIllegalArgument() {
            assertThatThrownBy(() -> AddOnType.fromValue("invalid_type"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ── optimistic locking ────────────────────────────────────────────────────

    @Nested
    @DisplayName("optimistic locking")
    class OptimisticLocking {

        @Test
        @DisplayName("throws OptimisticLockingFailureException when stale version is saved")
        void save_staleVersion_throwsOptimisticLockException() {
            AddOn original = addOnRepository.save(buildAddOn("LOCK_ADDON", AddOnType.QUOTA));

            // First update — version 0 → DB version becomes 1
            AddOn firstUpdate = AddOn.builder()
                    .id(original.getId())
                    .version(original.getVersion())
                    .code(original.getCode())
                    .name("Updated Name")
                    .description(original.getDescription())
                    .type(original.getType())
                    .active(original.isActive())
                    .createdAt(original.getCreatedAt())
                    .updatedAt(Instant.now())
                    .createdBy(original.getCreatedBy())
                    .updatedBy(DEV_USER)
                    .build();
            addOnRepository.save(firstUpdate);

            // Second update from stale version 0 — must fail
            AddOn staleUpdate = AddOn.builder()
                    .id(original.getId())
                    .version(original.getVersion())   // still 0, DB has 1
                    .code(original.getCode())
                    .name("Stale Update")
                    .description(original.getDescription())
                    .type(original.getType())
                    .active(original.isActive())
                    .createdAt(original.getCreatedAt())
                    .updatedAt(Instant.now())
                    .createdBy(original.getCreatedBy())
                    .updatedBy(DEV_USER)
                    .build();

            assertThatThrownBy(() -> addOnRepository.save(staleUpdate))
                    .isInstanceOf(OptimisticLockingFailureException.class);
        }
    }
}
