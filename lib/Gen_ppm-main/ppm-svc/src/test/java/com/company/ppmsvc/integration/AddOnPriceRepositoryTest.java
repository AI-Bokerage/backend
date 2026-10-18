package com.company.ppmsvc.integration;

import com.company.ppmsvc.addon.model.AddOnType;
import com.company.ppmsvc.common.BillingCycle;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.addon.model.AddOn;
import com.company.ppmsvc.addonprice.model.AddOnPrice;
import com.company.ppmsvc.addonprice.port.AddOnPriceRepositoryPort;
import com.company.ppmsvc.addon.port.AddOnRepositoryPort;
import com.company.ppmsvc.infrastructure.persistence.repository.AddOnJpaRepository;
import java.math.BigDecimal;
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
 * Repository-level integration tests for Add-On Price persistence (PPM-11).
 *
 * <p>All tests run against a real PostgreSQL instance managed by Testcontainers.
 * Flyway applies V001–V010 before the context starts, ensuring {@code ppm_add_on_prices}
 * and all its constraints are in place.
 *
 * <p>No class-level {@code @Transactional} — constraint tests require the INSERT to
 * flush to the DB before the violation is observable.  Cleanup via {@code @AfterEach}
 * in FK-safe order: prices → add-ons.
 */
@DisplayName("Add-On Price — Repository")
class AddOnPriceRepositoryTest extends AbstractContainerIntegrationTest {

    @Autowired AddOnPriceRepositoryPort addOnPriceRepository;
    @Autowired AddOnRepositoryPort      addOnRepository;
    @Autowired AddOnJpaRepository       addOnJpaRepository;
    @Autowired JdbcTemplate             jdbcTemplate;

    private UUID addOnId;

    @BeforeEach
    void seedAddOn() {
        addOnId = UUID.randomUUID();
        Instant now = Instant.now();
        addOnRepository.save(AddOn.builder()
                .id(addOnId)
                .code("AP-TEST-" + addOnId.toString().substring(0, 8))
                .name("Price Test Add-On")
                .type(AddOnType.QUOTA)
                .active(true)
                .createdAt(now).updatedAt(now)
                .createdBy(DEV_USER).updatedBy(DEV_USER)
                .build());
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM ppm_add_on_prices");
        jdbcTemplate.execute("DELETE FROM ppm_add_ons");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private AddOnPrice buildPrice(UUID aId, String region, String currency,
                                   BillingCycle cycle, BigDecimal amount,
                                   LocalDate effectiveFrom) {
        Instant now = Instant.now();
        return AddOnPrice.builder()
                .id(UUID.randomUUID())
                .addOnId(aId)
                .region(region)
                .currency(currency)
                .cycle(cycle)
                .amount(amount)
                .taxInclusive(false)
                .effectiveFrom(effectiveFrom)
                .active(true)
                .createdAt(now).updatedAt(now)
                .createdBy(DEV_USER).updatedBy(DEV_USER)
                .build();
    }

    private AddOnPrice buildPrice(String region, String currency,
                                   BillingCycle cycle, BigDecimal amount) {
        return buildPrice(addOnId, region, currency, cycle, amount, LocalDate.of(2025, 1, 1));
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("persists a new price row and returns the saved state with version=0")
        void save_newPrice_persistsAndReturns() {
            AddOnPrice saved = addOnPriceRepository.save(
                buildPrice("INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("199.0000")));

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getAddOnId()).isEqualTo(addOnId);
            assertThat(saved.getRegion()).isEqualTo("INDIA");
            assertThat(saved.getCurrency()).isEqualTo("INR");
            assertThat(saved.getCycle()).isEqualTo(BillingCycle.MONTHLY);
            assertThat(saved.getAmount()).isEqualByComparingTo(new BigDecimal("199.0000"));
            assertThat(saved.isActive()).isTrue();
            assertThat(saved.getVersion()).isEqualTo(0L);
        }

        @Test
        @DisplayName("MONTHLY and ANNUAL prices for same add-on/region/currency coexist")
        void save_monthlyAndAnnual_coexistAsSeparateRows() {
            addOnPriceRepository.save(buildPrice("INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("199")));
            addOnPriceRepository.save(buildPrice("INDIA", "INR", BillingCycle.ANNUAL,  new BigDecimal("1990")));

            assertThat(addOnPriceRepository.findByAddOnId(addOnId)).hasSize(2);
        }
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("returns price when ID exists")
        void findById_exists_returnsPrice() {
            AddOnPrice saved = addOnPriceRepository.save(
                buildPrice("US", "USD", BillingCycle.MONTHLY, new BigDecimal("4.9900")));

            Optional<AddOnPrice> found = addOnPriceRepository.findById(saved.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getCurrency()).isEqualTo("USD");
            assertThat(found.get().getCycle()).isEqualTo(BillingCycle.MONTHLY);
        }

        @Test
        @DisplayName("returns empty when ID does not exist")
        void findById_missing_returnsEmpty() {
            assertThat(addOnPriceRepository.findById(UUID.randomUUID())).isEmpty();
        }
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("returns all active price rows")
        void findAll_multiple_returnsAll() {
            addOnPriceRepository.save(buildPrice("INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("199")));
            addOnPriceRepository.save(buildPrice("INDIA", "INR", BillingCycle.ANNUAL,  new BigDecimal("1990")));
            addOnPriceRepository.save(buildPrice("US",    "USD", BillingCycle.MONTHLY, new BigDecimal("4.99")));

            assertThat(addOnPriceRepository.findAll()).hasSize(3);
        }

        @Test
        @DisplayName("returns empty list when no prices exist")
        void findAll_empty_returnsEmptyList() {
            assertThat(addOnPriceRepository.findAll()).isEmpty();
        }
    }

    // ── findByAddOnId ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByAddOnId")
    class FindByAddOnId {

        @Test
        @DisplayName("returns only prices for the given add-on")
        void findByAddOnId_multipleRegions_returnsAllForAddOn() {
            addOnPriceRepository.save(buildPrice("INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("199")));
            addOnPriceRepository.save(buildPrice("INDIA", "INR", BillingCycle.ANNUAL,  new BigDecimal("1990")));
            addOnPriceRepository.save(buildPrice("US",    "USD", BillingCycle.MONTHLY, new BigDecimal("4.99")));

            List<AddOnPrice> results = addOnPriceRepository.findByAddOnId(addOnId);

            assertThat(results).hasSize(3);
            assertThat(results).allMatch(p -> p.getAddOnId().equals(addOnId));
        }

        @Test
        @DisplayName("returns empty list when add-on has no prices")
        void findByAddOnId_noPrices_returnsEmpty() {
            assertThat(addOnPriceRepository.findByAddOnId(UUID.randomUUID())).isEmpty();
        }
    }

    // ── findByAddOnIdAndRegionAndCurrency ─────────────────────────────────────

    @Nested
    @DisplayName("findByAddOnIdAndRegionAndCurrency")
    class FindByAddOnIdAndRegionAndCurrency {

        @Test
        @DisplayName("returns only prices matching addOnId + region + currency")
        void find_matchingKey_returnsCorrectRows() {
            addOnPriceRepository.save(buildPrice("INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("199")));
            addOnPriceRepository.save(buildPrice("INDIA", "INR", BillingCycle.ANNUAL,  new BigDecimal("1990")));
            addOnPriceRepository.save(buildPrice("US",    "USD", BillingCycle.MONTHLY, new BigDecimal("4.99")));

            List<AddOnPrice> inr = addOnPriceRepository
                    .findByAddOnIdAndRegionAndCurrency(addOnId, "INDIA", "INR");

            assertThat(inr).hasSize(2);
            assertThat(inr).allMatch(p -> p.getCurrency().equals("INR"));
        }

        @Test
        @DisplayName("returns empty list when no matching combination exists")
        void find_noMatch_returnsEmpty() {
            assertThat(addOnPriceRepository
                    .findByAddOnIdAndRegionAndCurrency(addOnId, "EU", "EUR"))
                    .isEmpty();
        }
    }

    // ── exists ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("exists")
    class Exists {

        @Test
        @DisplayName("returns true when exact business key exists")
        void exists_exactKey_returnsTrue() {
            LocalDate date = LocalDate.of(2025, 1, 1);
            addOnPriceRepository.save(
                buildPrice(addOnId, "INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("199"), date));

            assertThat(addOnPriceRepository.exists(addOnId, "INDIA", "INR", BillingCycle.MONTHLY, date))
                    .isTrue();
        }

        @Test
        @DisplayName("returns false when effective_from differs")
        void exists_differentDate_returnsFalse() {
            LocalDate date = LocalDate.of(2025, 1, 1);
            addOnPriceRepository.save(
                buildPrice(addOnId, "INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("199"), date));

            assertThat(addOnPriceRepository.exists(addOnId, "INDIA", "INR",
                BillingCycle.MONTHLY, LocalDate.of(2026, 1, 1)))
                    .isFalse();
        }

        @Test
        @DisplayName("returns false when cycle differs")
        void exists_differentCycle_returnsFalse() {
            LocalDate date = LocalDate.of(2025, 1, 1);
            addOnPriceRepository.save(
                buildPrice(addOnId, "INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("199"), date));

            assertThat(addOnPriceRepository.exists(addOnId, "INDIA", "INR",
                BillingCycle.ANNUAL, date))
                    .isFalse();
        }

        @Test
        @DisplayName("returns false when no price exists for key")
        void exists_noRow_returnsFalse() {
            assertThat(addOnPriceRepository.exists(
                UUID.randomUUID(), "INDIA", "INR", BillingCycle.MONTHLY, LocalDate.of(2025, 1, 1)))
                    .isFalse();
        }
    }

    // ── soft delete ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("soft delete")
    class SoftDelete {

        @Test
        @DisplayName("softDelete hides price from findById")
        void softDelete_hiddenFromFindById() {
            AddOnPrice saved = addOnPriceRepository.save(
                buildPrice("INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("199")));

            addOnPriceRepository.softDelete(saved.getId(), DEV_USER);

            assertThat(addOnPriceRepository.findById(saved.getId())).isEmpty();
        }

        @Test
        @DisplayName("softDelete hides price from findByAddOnId")
        void softDelete_hiddenFromFindByAddOnId() {
            AddOnPrice saved = addOnPriceRepository.save(
                buildPrice("INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("199")));

            addOnPriceRepository.softDelete(saved.getId(), DEV_USER);

            assertThat(addOnPriceRepository.findByAddOnId(addOnId)).isEmpty();
        }

        @Test
        @DisplayName("softDelete hides price from findAll")
        void softDelete_hiddenFromFindAll() {
            AddOnPrice saved = addOnPriceRepository.save(
                buildPrice("US", "USD", BillingCycle.ANNUAL, new BigDecimal("49.99")));

            addOnPriceRepository.softDelete(saved.getId(), DEV_USER);

            assertThat(addOnPriceRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("softDelete makes exists() return false for the key")
        void softDelete_existsReturnsFalse() {
            LocalDate date = LocalDate.of(2025, 1, 1);
            AddOnPrice saved = addOnPriceRepository.save(
                buildPrice(addOnId, "INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("199"), date));

            addOnPriceRepository.softDelete(saved.getId(), DEV_USER);

            assertThat(addOnPriceRepository.exists(addOnId, "INDIA", "INR", BillingCycle.MONTHLY, date))
                    .isFalse();
        }

        @Test
        @DisplayName("soft-deleted row physically retained in DB (deleted_at is set)")
        void softDelete_physicalRowRetained() {
            AddOnPrice saved = addOnPriceRepository.save(
                buildPrice("INDIA", "INR", BillingCycle.ANNUAL, new BigDecimal("1990")));

            addOnPriceRepository.softDelete(saved.getId(), DEV_USER);

            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ppm_add_on_prices WHERE id = ?",
                Integer.class, saved.getId());
            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("softDelete on unknown ID throws ResourceNotFoundException")
        void softDelete_unknownId_throwsNotFoundException() {
            assertThatThrownBy(() ->
                addOnPriceRepository.softDelete(UUID.randomUUID(), DEV_USER))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("active filtering — soft-deleted excluded; active one still returned")
        void softDelete_activeFiltering_onlyActiveReturned() {
            AddOnPrice active  = addOnPriceRepository.save(
                buildPrice("INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("199")));
            AddOnPrice deleted = addOnPriceRepository.save(
                buildPrice("US",    "USD", BillingCycle.ANNUAL,  new BigDecimal("49.99")));

            addOnPriceRepository.softDelete(deleted.getId(), DEV_USER);

            List<AddOnPrice> all = addOnPriceRepository.findAll();
            assertThat(all).hasSize(1);
            assertThat(all.get(0).getId()).isEqualTo(active.getId());
        }
    }

    // ── unique constraint ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("unique pricing key constraint")
    class UniqueConstraint {

        @Test
        @DisplayName("throws DataIntegrityViolationException on duplicate (addOnId,region,currency,cycle,effectiveFrom)")
        void save_duplicateKey_throwsConstraintViolation() {
            LocalDate date = LocalDate.of(2025, 1, 1);
            addOnPriceRepository.save(
                buildPrice(addOnId, "INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("199"), date));

            assertThatThrownBy(() ->
                addOnPriceRepository.save(
                    buildPrice(addOnId, "INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("249"), date)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("constraint violation message contains index name for GlobalExceptionHandler detection")
        void save_duplicateKey_exceptionContainsIndexName() {
            LocalDate date = LocalDate.of(2025, 6, 1);
            addOnPriceRepository.save(
                buildPrice(addOnId, "US", "USD", BillingCycle.ANNUAL, new BigDecimal("49.99"), date));

            assertThatThrownBy(() ->
                addOnPriceRepository.save(
                    buildPrice(addOnId, "US", "USD", BillingCycle.ANNUAL, new BigDecimal("59.99"), date)))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("uq_ppm_add_on_prices_active");
        }

        @Test
        @DisplayName("same key with different effectiveFrom is allowed (distinct price versions)")
        void save_sameKeyDifferentDate_succeeds() {
            addOnPriceRepository.save(
                buildPrice(addOnId, "INDIA", "INR", BillingCycle.MONTHLY,
                    new BigDecimal("199"), LocalDate.of(2025, 1, 1)));

            AddOnPrice nextVersion = addOnPriceRepository.save(
                buildPrice(addOnId, "INDIA", "INR", BillingCycle.MONTHLY,
                    new BigDecimal("249"), LocalDate.of(2026, 1, 1)));

            assertThat(nextVersion.getId()).isNotNull();
            assertThat(addOnPriceRepository.findByAddOnIdAndRegionAndCurrency(addOnId, "INDIA", "INR"))
                    .hasSize(2);
        }

        @Test
        @DisplayName("soft-deleted key can be reused by a new price row (partial index)")
        void save_sameKeyAfterSoftDelete_succeeds() {
            LocalDate date = LocalDate.of(2025, 1, 1);
            AddOnPrice first = addOnPriceRepository.save(
                buildPrice(addOnId, "INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("199"), date));

            addOnPriceRepository.softDelete(first.getId(), DEV_USER);

            AddOnPrice second = addOnPriceRepository.save(
                buildPrice(addOnId, "INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("249"), date));

            assertThat(second.getId()).isNotEqualTo(first.getId());
            assertThat(addOnPriceRepository.findById(second.getId())).isPresent();
        }
    }

    // ── audit fields ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("audit fields")
    class AuditFields {

        @Test
        @DisplayName("createdAt, updatedAt, createdBy, updatedBy populated after save")
        void save_newPrice_populatesAuditFields() {
            Instant before = Instant.now().minusSeconds(1);

            AddOnPrice saved = addOnPriceRepository.save(
                buildPrice("INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("199")));

            assertThat(saved.getCreatedAt()).isAfter(before);
            assertThat(saved.getUpdatedAt()).isAfter(before);
            assertThat(saved.getCreatedBy()).isEqualTo(DEV_USER);
            assertThat(saved.getUpdatedBy()).isEqualTo(DEV_USER);
        }

        @Test
        @DisplayName("version is 0 after initial save")
        void save_newPrice_versionIsZero() {
            AddOnPrice saved = addOnPriceRepository.save(
                buildPrice("US", "USD", BillingCycle.ANNUAL, new BigDecimal("49.99")));

            assertThat(saved.getVersion()).isEqualTo(0L);
        }
    }

    // ── billing cycle wire value ──────────────────────────────────────────────

    @Nested
    @DisplayName("billing cycle wire value persistence")
    class BillingCycleWireValue {

        @Test
        @DisplayName("MONTHLY stored as 'monthly' in add-on price")
        void save_monthly_storedAsWireValue() {
            AddOnPrice saved = addOnPriceRepository.save(
                buildPrice("INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("199")));

            String stored = jdbcTemplate.queryForObject(
                "SELECT cycle FROM ppm_add_on_prices WHERE id = ?",
                String.class, saved.getId());

            assertThat(stored).isEqualTo("monthly");
        }

        @Test
        @DisplayName("BillingCycle reconstituted correctly from DB wire value")
        void findById_afterSave_cycleReconstitutedCorrectly() {
            AddOnPrice saved = addOnPriceRepository.save(
                buildPrice("US", "USD", BillingCycle.ANNUAL, new BigDecimal("49.99")));

            Optional<AddOnPrice> found = addOnPriceRepository.findById(saved.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getCycle()).isEqualTo(BillingCycle.ANNUAL);
            assertThat(found.get().getCycle().getValue()).isEqualTo("annual");
        }
    }

    // ── optimistic locking ────────────────────────────────────────────────────

    @Nested
    @DisplayName("optimistic locking")
    class OptimisticLocking {

        @Test
        @DisplayName("throws OptimisticLockingFailureException when stale version is saved")
        void save_staleVersion_throwsOptimisticLockException() {
            AddOnPrice original = addOnPriceRepository.save(
                buildPrice("INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("199")));

            AddOnPrice firstUpdate = AddOnPrice.builder()
                    .id(original.getId())
                    .version(original.getVersion())
                    .addOnId(original.getAddOnId())
                    .region(original.getRegion())
                    .currency(original.getCurrency())
                    .cycle(original.getCycle())
                    .amount(new BigDecimal("249.0000"))
                    .taxInclusive(original.isTaxInclusive())
                    .effectiveFrom(original.getEffectiveFrom())
                    .active(original.isActive())
                    .createdAt(original.getCreatedAt())
                    .updatedAt(Instant.now())
                    .createdBy(original.getCreatedBy())
                    .updatedBy(DEV_USER)
                    .build();
            addOnPriceRepository.save(firstUpdate);

            AddOnPrice staleUpdate = AddOnPrice.builder()
                    .id(original.getId())
                    .version(original.getVersion())   // still 0, DB has 1
                    .addOnId(original.getAddOnId())
                    .region(original.getRegion())
                    .currency(original.getCurrency())
                    .cycle(original.getCycle())
                    .amount(new BigDecimal("299.0000"))
                    .taxInclusive(original.isTaxInclusive())
                    .effectiveFrom(original.getEffectiveFrom())
                    .active(original.isActive())
                    .createdAt(original.getCreatedAt())
                    .updatedAt(Instant.now())
                    .createdBy(original.getCreatedBy())
                    .updatedBy(DEV_USER)
                    .build();

            assertThatThrownBy(() -> addOnPriceRepository.save(staleUpdate))
                    .isInstanceOf(OptimisticLockingFailureException.class);
        }
    }

    // ── FK integrity ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("FK integrity")
    class FkIntegrity {

        @Test
        @DisplayName("saving a price against an unknown add_on_id throws DataIntegrityViolationException")
        void save_unknownAddOnId_throwsFkViolation() {
            UUID ghostAddOnId = UUID.randomUUID();
            Instant now = Instant.now();

            AddOnPrice orphan = AddOnPrice.builder()
                    .id(UUID.randomUUID())
                    .addOnId(ghostAddOnId)
                    .region("INDIA").currency("INR")
                    .cycle(BillingCycle.MONTHLY)
                    .amount(new BigDecimal("199"))
                    .taxInclusive(false)
                    .effectiveFrom(LocalDate.of(2025, 1, 1))
                    .active(true)
                    .createdAt(now).updatedAt(now)
                    .createdBy(DEV_USER).updatedBy(DEV_USER)
                    .build();

            assertThatThrownBy(() -> addOnPriceRepository.save(orphan))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
