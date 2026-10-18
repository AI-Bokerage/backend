package com.company.ppmsvc.integration;

import com.company.ppmsvc.module.model.ModuleCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.module.model.Module;
import com.company.ppmsvc.module.port.ModuleRepositoryPort;
import com.company.ppmsvc.infrastructure.persistence.repository.ModuleJpaRepository;
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
 * Repository-level integration tests for Module Catalog persistence.
 *
 * <p>All tests run against a real PostgreSQL instance managed by Testcontainers.
 * Flyway applies all migrations before the context starts, ensuring the
 * {@code ppm_modules} table and its constraints are in place.
 *
 * <p>No class-level {@code @Transactional} is used because the unique-code
 * constraint test requires the INSERT to actually reach the DB (i.e. flush)
 * before the exception is observable.  All tests clean up via {@code @AfterEach}
 * instead of relying on transaction rollback.
 */
@DisplayName("Module Catalog — Repository")
class ModuleCatalogRepositoryTest extends AbstractContainerIntegrationTest {

    @Autowired
    private ModuleRepositoryPort moduleRepository;

    /** Direct JPA repository — used only for teardown, never in assertions. */
    @Autowired
    private ModuleJpaRepository moduleJpaRepository;

    /** Raw JDBC access — used only in converter-roundtrip test. */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        // Hard-delete is intentional here: test teardown must fully remove rows
        // (including soft-deleted ones) to preserve isolation between test methods.
        moduleJpaRepository.deleteAll();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Module buildModule(ModuleCode code, String name) {
        Instant now = Instant.now();
        return Module.builder()
            .id(UUID.randomUUID())
            // version intentionally omitted: null version signals a new entity to
            // Spring Data JPA → calls persist() instead of merge()
            .code(code)
            .name(name)
            .description("Integration test module — " + name)
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
        @DisplayName("persists a new module and returns the saved state")
        void save_newModule_persistsAndReturns() {
            Module saved = moduleRepository.save(
                buildModule(ModuleCode.PROJECT_MANAGEMENT, "Project Management"));

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getCode()).isEqualTo(ModuleCode.PROJECT_MANAGEMENT);
            assertThat(saved.getName()).isEqualTo("Project Management");
            assertThat(saved.isActive()).isTrue();
            assertThat(saved.getVersion()).isNotNull();
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("returns module when ID exists")
        void findById_exists_returnsModule() {
            Module saved = moduleRepository.save(
                buildModule(ModuleCode.INVOICING, "Invoicing"));

            Optional<Module> found = moduleRepository.findById(saved.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getCode()).isEqualTo(ModuleCode.INVOICING);
        }

        @Test
        @DisplayName("returns empty when ID does not exist")
        void findById_missing_returnsEmpty() {
            Optional<Module> found = moduleRepository.findById(UUID.randomUUID());

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByCode")
    class FindByCode {

        @Test
        @DisplayName("returns module when code exists")
        void findByCode_exists_returnsModule() {
            moduleRepository.save(buildModule(ModuleCode.SSO, "SSO"));

            Optional<Module> found = moduleRepository.findByCode(ModuleCode.SSO);

            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("SSO");
        }

        @Test
        @DisplayName("returns empty when code does not exist")
        void findByCode_missing_returnsEmpty() {
            Optional<Module> found = moduleRepository.findByCode(ModuleCode.WHITE_LABEL);

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsByCode")
    class ExistsByCode {

        @Test
        @DisplayName("returns true when module with code exists")
        void existsByCode_exists_returnsTrue() {
            moduleRepository.save(buildModule(ModuleCode.MESSAGING, "Messaging"));

            assertThat(moduleRepository.existsByCode(ModuleCode.MESSAGING)).isTrue();
        }

        @Test
        @DisplayName("returns false when no module with code exists")
        void existsByCode_missing_returnsFalse() {
            assertThat(moduleRepository.existsByCode(ModuleCode.AUDIT_LOG_EXPORT)).isFalse();
        }
    }

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("returns all persisted modules ordered by code ascending")
        void findAll_multipleModules_returnsAll() {
            moduleRepository.save(buildModule(ModuleCode.REPORTING,           "Reporting"));
            moduleRepository.save(buildModule(ModuleCode.DOCUMENT_MANAGEMENT, "Document Management"));
            moduleRepository.save(buildModule(ModuleCode.API_ACCESS,          "API Access"));

            List<Module> all = moduleRepository.findAll();

            assertThat(all).hasSize(3);
            assertThat(all).extracting(Module::getCode)
                .containsExactlyInAnyOrder(
                    ModuleCode.REPORTING,
                    ModuleCode.DOCUMENT_MANAGEMENT,
                    ModuleCode.API_ACCESS);
        }

        @Test
        @DisplayName("returns empty list when no modules exist")
        void findAll_empty_returnsEmptyList() {
            assertThat(moduleRepository.findAll()).isEmpty();
        }
    }

    @Nested
    @DisplayName("unique code constraint")
    class UniqueCodeConstraint {

        @Test
        @DisplayName("throws DataIntegrityViolationException when duplicate code is saved")
        void save_duplicateCode_throwsConstraintViolation() {
            moduleRepository.save(buildModule(ModuleCode.CUSTOM_ROLES, "Custom Roles"));

            assertThatThrownBy(() ->
                moduleRepository.save(buildModule(ModuleCode.CUSTOM_ROLES, "Custom Roles Duplicate")))
                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("C4: constraint violation message contains index name for GlobalExceptionHandler detection")
        void save_duplicateCode_exceptionContainsConstraintName() {
            // GlobalExceptionHandler.resolveConstraintCode() detects MODULE_CODE_ALREADY_EXISTS
            // by scanning for "uq_ppm_modules_code" in the exception message chain.
            // This test verifies PostgreSQL actually includes the index name in the error.
            moduleRepository.save(buildModule(ModuleCode.LEAD_MANAGEMENT, "Lead Management"));

            assertThatThrownBy(() ->
                moduleRepository.save(buildModule(ModuleCode.LEAD_MANAGEMENT, "Lead Management 2")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_ppm_modules_code");
        }
    }

    @Nested
    @DisplayName("audit fields")
    class AuditFields {

        @Test
        @DisplayName("createdAt, updatedAt, createdBy and updatedBy are populated after save")
        void save_newModule_populatesAuditFields() {
            Instant before = Instant.now().minusSeconds(1);

            Module saved = moduleRepository.save(
                buildModule(ModuleCode.TIME_TRACKING, "Time Tracking"));

            assertThat(saved.getCreatedAt()).isAfter(before);
            assertThat(saved.getUpdatedAt()).isAfter(before);
            assertThat(saved.getCreatedBy()).isEqualTo(DEV_USER);
            assertThat(saved.getUpdatedBy()).isEqualTo(DEV_USER);
        }

        @Test
        @DisplayName("version is 0 after initial save")
        void save_newModule_versionIsZero() {
            Module saved = moduleRepository.save(
                buildModule(ModuleCode.CLIENT_PORTAL, "Client Portal"));

            assertThat(saved.getVersion()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("converter roundtrip")
    class ConverterRoundtrip {

        @Test
        @DisplayName("stores wire value in DB, not enum constant name")
        void save_moduleCode_storedAsWireValue() {
            Module saved = moduleRepository.save(
                buildModule(ModuleCode.LEAD_MANAGEMENT, "Lead Management"));

            // Query the raw DB value directly — bypasses JPA converter
            String storedCode = jdbcTemplate.queryForObject(
                "SELECT code FROM ppm_modules WHERE id = ?",
                String.class,
                saved.getId());

            assertThat(storedCode).isEqualTo("lead_management");
            assertThat(storedCode).doesNotContain("LEAD_MANAGEMENT");
        }

        @Test
        @DisplayName("enum is reconstituted correctly from DB wire value")
        void findByCode_afterSave_enumReconstitutedCorrectly() {
            moduleRepository.save(buildModule(ModuleCode.E_SIGNATURE, "E-Signature"));

            Optional<Module> found = moduleRepository.findByCode(ModuleCode.E_SIGNATURE);

            assertThat(found).isPresent();
            assertThat(found.get().getCode()).isEqualTo(ModuleCode.E_SIGNATURE);
            assertThat(found.get().getCode().getValue()).isEqualTo("e_signature");
        }
    }

    @Nested
    @DisplayName("optimistic locking")
    class OptimisticLocking {

        @Test
        @DisplayName("throws OptimisticLockingFailureException when stale version is saved")
        void save_staleVersion_throwsOptimisticLockException() {
            // Persist the module — DB row gets version = 0
            Module original = moduleRepository.save(
                buildModule(ModuleCode.WHITE_LABEL, "White Label"));

            // First update: version 0 → DB accepts, version becomes 1
            Module firstUpdate = Module.builder()
                .id(original.getId())
                .version(original.getVersion())   // 0
                .code(original.getCode())
                .name("White Label — v1")
                .description(original.getDescription())
                .active(original.isActive())
                .createdAt(original.getCreatedAt())
                .updatedAt(Instant.now())
                .createdBy(original.getCreatedBy())
                .updatedBy(DEV_USER)
                .build();
            moduleRepository.save(firstUpdate);   // succeeds; DB version is now 1

            // Second update built from the original stale version (0) — must fail
            Module staleUpdate = Module.builder()
                .id(original.getId())
                .version(original.getVersion())   // still 0, DB has 1
                .code(original.getCode())
                .name("White Label — stale")
                .description(original.getDescription())
                .active(original.isActive())
                .createdAt(original.getCreatedAt())
                .updatedAt(Instant.now())
                .createdBy(original.getCreatedBy())
                .updatedBy(DEV_USER)
                .build();

            assertThatThrownBy(() -> moduleRepository.save(staleUpdate))
                .isInstanceOf(OptimisticLockingFailureException.class);
        }
    }

    @Nested
    @DisplayName("soft delete")
    class SoftDelete {

        @Test
        @DisplayName("softDelete hides module from findById")
        void softDelete_existingModule_hiddenFromFindById() {
            Module saved = moduleRepository.save(
                buildModule(ModuleCode.REPORTING, "Reporting"));

            moduleRepository.softDelete(saved.getId(), DEV_USER);

            assertThat(moduleRepository.findById(saved.getId())).isEmpty();
        }

        @Test
        @DisplayName("softDelete hides module from findAll")
        void softDelete_existingModule_hiddenFromFindAll() {
            Module saved = moduleRepository.save(
                buildModule(ModuleCode.API_ACCESS, "API Access"));

            moduleRepository.softDelete(saved.getId(), DEV_USER);

            assertThat(moduleRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("softDelete makes existsByCode return false")
        void softDelete_existingModule_existsByCodeReturnsFalse() {
            Module saved = moduleRepository.save(
                buildModule(ModuleCode.MESSAGING, "Messaging"));

            moduleRepository.softDelete(saved.getId(), DEV_USER);

            assertThat(moduleRepository.existsByCode(ModuleCode.MESSAGING)).isFalse();
        }

        @Test
        @DisplayName("soft-deleted code can be reused by a new module")
        void softDelete_deletedCode_canBeReused() {
            Module first = moduleRepository.save(
                buildModule(ModuleCode.SSO, "SSO v1"));
            moduleRepository.softDelete(first.getId(), DEV_USER);

            // Unique partial index is WHERE deleted_at IS NULL, so this must succeed
            Module second = moduleRepository.save(
                buildModule(ModuleCode.SSO, "SSO v2"));

            assertThat(second.getId()).isNotEqualTo(first.getId());
            assertThat(moduleRepository.findByCode(ModuleCode.SSO)).isPresent();
        }

        @Test
        @DisplayName("softDelete on unknown ID throws ResourceNotFoundException")
        void softDelete_unknownId_throwsNotFoundException() {
            assertThatThrownBy(() ->
                moduleRepository.softDelete(UUID.randomUUID(), DEV_USER))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
