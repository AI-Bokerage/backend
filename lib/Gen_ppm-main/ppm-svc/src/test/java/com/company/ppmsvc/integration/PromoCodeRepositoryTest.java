package com.company.ppmsvc.integration;

import com.company.ppmsvc.promocode.model.DiscountType;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.promocode.model.PromoCode;
import com.company.ppmsvc.promocode.port.PromoCodeRepositoryPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
 * Repository-level integration tests for Promo Code persistence (PPM-07).
 *
 * <p>All tests run against a real PostgreSQL instance managed by Testcontainers.
 * Flyway applies V001–V009 before the context starts, ensuring {@code ppm_promo_codes}
 * and all its constraints are in place.
 *
 * <p>No class-level {@code @Transactional} — constraint tests require the INSERT to
 * flush to the DB before the violation is observable.  Cleanup is via {@code @AfterEach}
 * hard-delete using raw JDBC (bypasses {@code @SQLRestriction}).
 */
@DisplayName("Promo Code — Repository")
class PromoCodeRepositoryTest extends AbstractContainerIntegrationTest {

    @Autowired private PromoCodeRepositoryPort promoCodeRepository;
    @Autowired private JdbcTemplate            jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM ppm_promo_code_plans");
        jdbcTemplate.execute("DELETE FROM ppm_promo_codes");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private PromoCode buildPromoCode(String code, DiscountType type, BigDecimal value,
                                     LocalDate validFrom, LocalDate validUntil) {
        Instant now = Instant.now();
        return PromoCode.builder()
            .id(UUID.randomUUID())
            .code(code)
            .discountType(type)
            .value(value)
            .validFrom(validFrom)
            .validUntil(validUntil)
            .usageCap(null)
            .usageCount(0)
            .firstTimeOnly(false)
            .active(true)
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build();
    }

    private PromoCode buildPromoCode(String code) {
        return buildPromoCode(code, DiscountType.PERCENTAGE, new BigDecimal("20.0000"),
            LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("persists a new promo code and returns the saved state with version=0")
        void save_newPromoCode_persistsAndReturns() {
            PromoCode saved = promoCodeRepository.save(buildPromoCode("SUMMER20"));

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getCode()).isEqualTo("SUMMER20");
            assertThat(saved.getDiscountType()).isEqualTo(DiscountType.PERCENTAGE);
            assertThat(saved.getValue()).isEqualByComparingTo(new BigDecimal("20.0000"));
            assertThat(saved.getActive()).isTrue();
            assertThat(saved.getVersion()).isEqualTo(0L);
        }

        @Test
        @DisplayName("PERCENTAGE and FLAT discount types can coexist as separate rows")
        void save_differentDiscountTypes_coexistAsSeparateRows() {
            PromoCode pct  = promoCodeRepository.save(buildPromoCode("PCT10",
                DiscountType.PERCENTAGE, new BigDecimal("10.0000"),
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)));
            PromoCode flat = promoCodeRepository.save(buildPromoCode("FLAT50",
                DiscountType.FLAT, new BigDecimal("50.0000"),
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)));

            assertThat(pct.getId()).isNotEqualTo(flat.getId());
            assertThat(promoCodeRepository.findAll()).hasSize(2);
        }
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("returns promo code when ID exists")
        void findById_exists_returnsPromoCode() {
            PromoCode saved = promoCodeRepository.save(buildPromoCode("HELLO30"));

            Optional<PromoCode> found = promoCodeRepository.findById(saved.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getCode()).isEqualTo("HELLO30");
        }

        @Test
        @DisplayName("returns empty when ID does not exist")
        void findById_missing_returnsEmpty() {
            assertThat(promoCodeRepository.findById(UUID.randomUUID())).isEmpty();
        }
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("returns all active rows ordered by code ascending")
        void findAll_multiple_returnsInCodeOrder() {
            promoCodeRepository.save(buildPromoCode("ZCODE"));
            promoCodeRepository.save(buildPromoCode("ACODE"));
            promoCodeRepository.save(buildPromoCode("MCODE"));

            List<PromoCode> all = promoCodeRepository.findAll();

            assertThat(all).hasSize(3);
            assertThat(all).extracting(PromoCode::getCode)
                .containsExactly("ACODE", "MCODE", "ZCODE");
        }

        @Test
        @DisplayName("returns empty list when no promo codes exist")
        void findAll_empty_returnsEmptyList() {
            assertThat(promoCodeRepository.findAll()).isEmpty();
        }
    }

    // ── findByCode ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByCode")
    class FindByCode {

        @Test
        @DisplayName("returns promo code when code string matches")
        void findByCode_matches_returnsPromoCode() {
            promoCodeRepository.save(buildPromoCode("WINTER15"));

            Optional<PromoCode> found = promoCodeRepository.findByCode("WINTER15");

            assertThat(found).isPresent();
            assertThat(found.get().getDiscountType()).isEqualTo(DiscountType.PERCENTAGE);
        }

        @Test
        @DisplayName("returns empty when code does not exist")
        void findByCode_missing_returnsEmpty() {
            assertThat(promoCodeRepository.findByCode("NOSUCHCODE")).isEmpty();
        }
    }

    // ── existsByCode ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("existsByCode")
    class ExistsByCode {

        @Test
        @DisplayName("returns true when active promo code with that code exists")
        void existsByCode_active_returnsTrue() {
            promoCodeRepository.save(buildPromoCode("EXIST99"));

            assertThat(promoCodeRepository.existsByCode("EXIST99")).isTrue();
        }

        @Test
        @DisplayName("returns false when no active promo code with that code exists")
        void existsByCode_missing_returnsFalse() {
            assertThat(promoCodeRepository.existsByCode("NOSUCHCODE")).isFalse();
        }
    }

    // ── soft delete ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("soft delete")
    class SoftDelete {

        @Test
        @DisplayName("softDelete hides promo code from findById")
        void softDelete_hiddenFromFindById() {
            PromoCode saved = promoCodeRepository.save(buildPromoCode("DEL001"));

            promoCodeRepository.softDelete(saved.getId(), DEV_USER);

            assertThat(promoCodeRepository.findById(saved.getId())).isEmpty();
        }

        @Test
        @DisplayName("softDelete hides promo code from findByCode")
        void softDelete_hiddenFromFindByCode() {
            PromoCode saved = promoCodeRepository.save(buildPromoCode("DEL002"));

            promoCodeRepository.softDelete(saved.getId(), DEV_USER);

            assertThat(promoCodeRepository.findByCode("DEL002")).isEmpty();
        }

        @Test
        @DisplayName("softDelete hides promo code from findAll")
        void softDelete_hiddenFromFindAll() {
            PromoCode saved = promoCodeRepository.save(buildPromoCode("DEL003"));

            promoCodeRepository.softDelete(saved.getId(), DEV_USER);

            assertThat(promoCodeRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("softDelete makes existsByCode return false")
        void softDelete_existsByCodeReturnsFalse() {
            PromoCode saved = promoCodeRepository.save(buildPromoCode("DEL004"));

            promoCodeRepository.softDelete(saved.getId(), DEV_USER);

            assertThat(promoCodeRepository.existsByCode("DEL004")).isFalse();
        }

        @Test
        @DisplayName("soft-deleted row is physically retained in DB (deleted_at is set)")
        void softDelete_physicalRowRetained() {
            PromoCode saved = promoCodeRepository.save(buildPromoCode("DEL005"));

            promoCodeRepository.softDelete(saved.getId(), DEV_USER);

            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ppm_promo_codes WHERE id = ?",
                Integer.class, saved.getId());
            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("softDelete on unknown ID throws ResourceNotFoundException")
        void softDelete_unknownId_throwsNotFoundException() {
            assertThatThrownBy(() ->
                promoCodeRepository.softDelete(UUID.randomUUID(), DEV_USER))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("active filtering — soft-deleted code excluded; active one still returned")
        void softDelete_activeFiltering_onlyActiveReturned() {
            PromoCode active  = promoCodeRepository.save(buildPromoCode("ACTIVE01"));
            PromoCode deleted = promoCodeRepository.save(buildPromoCode("DELETED01"));

            promoCodeRepository.softDelete(deleted.getId(), DEV_USER);

            List<PromoCode> all = promoCodeRepository.findAll();
            assertThat(all).hasSize(1);
            assertThat(all.get(0).getId()).isEqualTo(active.getId());
        }
    }

    // ── unique code constraint ─────────────────────────────────────────────────

    @Nested
    @DisplayName("unique code constraint")
    class UniqueCodeConstraint {

        @Test
        @DisplayName("throws DataIntegrityViolationException on duplicate active code")
        void save_duplicateCode_throwsConstraintViolation() {
            promoCodeRepository.save(buildPromoCode("DUPCODE"));

            assertThatThrownBy(() -> promoCodeRepository.save(buildPromoCode("DUPCODE")))
                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("exception message contains index name for detection")
        void save_duplicateCode_exceptionContainsIndexName() {
            promoCodeRepository.save(buildPromoCode("IDXCODE"));

            assertThatThrownBy(() -> promoCodeRepository.save(buildPromoCode("IDXCODE")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_ppm_promo_codes_code");
        }

        @Test
        @DisplayName("soft-deleted code can be reused by a new row (partial index)")
        void save_sameCodeAfterSoftDelete_succeeds() {
            PromoCode first = promoCodeRepository.save(buildPromoCode("REUSE01"));

            promoCodeRepository.softDelete(first.getId(), DEV_USER);

            PromoCode second = promoCodeRepository.save(buildPromoCode("REUSE01"));

            assertThat(second.getId()).isNotEqualTo(first.getId());
            assertThat(promoCodeRepository.findByCode("REUSE01")).isPresent();
        }
    }

    // ── audit fields ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("audit fields")
    class AuditFields {

        @Test
        @DisplayName("createdAt, updatedAt, createdBy, updatedBy populated after save")
        void save_newPromoCode_populatesAuditFields() {
            Instant before = Instant.now().minusSeconds(1);

            PromoCode saved = promoCodeRepository.save(buildPromoCode("AUDIT01"));

            assertThat(saved.getCreatedAt()).isAfter(before);
            assertThat(saved.getUpdatedAt()).isAfter(before);
            assertThat(saved.getCreatedBy()).isEqualTo(DEV_USER);
            assertThat(saved.getUpdatedBy()).isEqualTo(DEV_USER);
        }

        @Test
        @DisplayName("version is 0 after initial save")
        void save_newPromoCode_versionIsZero() {
            PromoCode saved = promoCodeRepository.save(buildPromoCode("VERSION01"));

            assertThat(saved.getVersion()).isEqualTo(0L);
        }
    }

    // ── discount type wire value persistence ──────────────────────────────────

    @Nested
    @DisplayName("discount type wire value persistence")
    class DiscountTypeWireValue {

        @Test
        @DisplayName("PERCENTAGE stored as 'percentage', not 'PERCENTAGE'")
        void save_percentage_storedAsWireValue() {
            PromoCode saved = promoCodeRepository.save(buildPromoCode("PCT_WIRE",
                DiscountType.PERCENTAGE, new BigDecimal("15.0000"),
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)));

            String stored = jdbcTemplate.queryForObject(
                "SELECT discount_type FROM ppm_promo_codes WHERE id = ?",
                String.class, saved.getId());

            assertThat(stored).isEqualTo("percentage");
            assertThat(stored).doesNotContain("PERCENTAGE");
        }

        @Test
        @DisplayName("FLAT stored as 'flat', not 'FLAT'")
        void save_flat_storedAsWireValue() {
            PromoCode saved = promoCodeRepository.save(buildPromoCode("FLAT_WIRE",
                DiscountType.FLAT, new BigDecimal("100.0000"),
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)));

            String stored = jdbcTemplate.queryForObject(
                "SELECT discount_type FROM ppm_promo_codes WHERE id = ?",
                String.class, saved.getId());

            assertThat(stored).isEqualTo("flat");
            assertThat(stored).doesNotContain("FLAT");
        }

        @Test
        @DisplayName("DiscountType reconstituted correctly from DB wire value")
        void findById_afterSave_discountTypeReconstitutedCorrectly() {
            PromoCode saved = promoCodeRepository.save(buildPromoCode("RECONSTITUTE",
                DiscountType.FLAT, new BigDecimal("50.0000"),
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)));

            Optional<PromoCode> found = promoCodeRepository.findById(saved.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getDiscountType()).isEqualTo(DiscountType.FLAT);
            assertThat(found.get().getDiscountType().getValue()).isEqualTo("flat");
        }
    }

    // ── optimistic locking ────────────────────────────────────────────────────

    @Nested
    @DisplayName("optimistic locking")
    class OptimisticLocking {

        @Test
        @DisplayName("throws OptimisticLockingFailureException when stale version is saved")
        void save_staleVersion_throwsOptimisticLockException() {
            PromoCode original = promoCodeRepository.save(buildPromoCode("LOCK_TEST"));

            // First update — version 0 → DB version becomes 1
            PromoCode firstUpdate = PromoCode.builder()
                .id(original.getId())
                .version(original.getVersion())   // 0
                .code(original.getCode())
                .discountType(original.getDiscountType())
                .value(new BigDecimal("25.0000"))
                .validFrom(original.getValidFrom())
                .validUntil(original.getValidUntil())
                .usageCap(original.getUsageCap())
                .usageCount(original.getUsageCount())
                .firstTimeOnly(original.getFirstTimeOnly())
                .active(original.getActive())
                .createdAt(original.getCreatedAt())
                .updatedAt(Instant.now())
                .createdBy(original.getCreatedBy())
                .updatedBy(DEV_USER)
                .build();
            promoCodeRepository.save(firstUpdate);

            // Second update from stale version 0 — must fail
            PromoCode staleUpdate = PromoCode.builder()
                .id(original.getId())
                .version(original.getVersion())   // still 0, DB has 1
                .code(original.getCode())
                .discountType(original.getDiscountType())
                .value(new BigDecimal("30.0000"))
                .validFrom(original.getValidFrom())
                .validUntil(original.getValidUntil())
                .usageCap(original.getUsageCap())
                .usageCount(original.getUsageCount())
                .firstTimeOnly(original.getFirstTimeOnly())
                .active(original.getActive())
                .createdAt(original.getCreatedAt())
                .updatedAt(Instant.now())
                .createdBy(original.getCreatedBy())
                .updatedBy(DEV_USER)
                .build();

            assertThatThrownBy(() -> promoCodeRepository.save(staleUpdate))
                .isInstanceOf(OptimisticLockingFailureException.class);
        }
    }
}
