package com.company.ppmsvc.integration;

import com.company.ppmsvc.common.BillingCycle;
import com.company.ppmsvc.plan.model.PlanVisibility;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.planprice.model.PlanPrice;
import com.company.ppmsvc.planprice.port.PlanPriceRepositoryPort;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.infrastructure.persistence.repository.PlanJpaRepository;
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
 * Repository-level integration tests for Plan Price persistence (PPM-05).
 *
 * <p>All tests run against a real PostgreSQL instance managed by Testcontainers.
 * Flyway applies V001–V007 before the context starts, ensuring {@code ppm_plan_prices}
 * and all its constraints are in place.
 *
 * <p>No class-level {@code @Transactional} — constraint tests require the INSERT to
 * flush to the DB before the violation is observable.  Cleanup is via {@code @AfterEach}
 * hard-delete in FK-safe order: prices → plans.
 */
@DisplayName("Plan Price — Repository")
class PlanPriceRepositoryTest extends AbstractContainerIntegrationTest {

    @Autowired private PlanPriceRepositoryPort planPriceRepository;
    @Autowired private PlanRepositoryPort      planRepository;
    @Autowired private PlanJpaRepository       planJpaRepository;
    @Autowired private JdbcTemplate            jdbcTemplate;

    // Seeded plan — every test can create prices against this plan
    private UUID planId;

    @BeforeEach
    void seedPlan() {
        planId = UUID.randomUUID();
        Instant now = Instant.now();
        planRepository.save(Plan.builder()
            .id(planId)
            .code("PP-TEST-" + planId.toString().substring(0, 8))
            .slug("PP-" + planId.toString().substring(0, 8))
            .name("Price Test Plan")
            .visibility(PlanVisibility.PUBLIC)
            .trialDays(0).active(true)
            .createdAt(now).updatedAt(now)
            .build());
    }

    @AfterEach
    void cleanUp() {
        // Native SQL bypasses @SQLRestriction so soft-deleted price rows are also removed,
        // preventing FK violations when the plan row is subsequently hard-deleted.
        jdbcTemplate.execute("DELETE FROM ppm_plan_prices");
        planJpaRepository.deleteAll();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private PlanPrice buildPrice(UUID pid, String region, String currency,
                                  BillingCycle cycle, BigDecimal amount,
                                  LocalDate effectiveFrom) {
        Instant now = Instant.now();
        return PlanPrice.builder()
            .id(UUID.randomUUID())
            // version intentionally omitted: null → persist() not merge()
            .planId(pid)
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

    private PlanPrice buildPrice(String region, String currency,
                                  BillingCycle cycle, BigDecimal amount) {
        return buildPrice(planId, region, currency, cycle, amount, LocalDate.of(2025, 1, 1));
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("persists a new price row and returns the saved state with version=0")
        void save_newPrice_persistsAndReturns() {
            PlanPrice saved = planPriceRepository.save(
                buildPrice("INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("499.0000")));

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getPlanId()).isEqualTo(planId);
            assertThat(saved.getRegion()).isEqualTo("INDIA");
            assertThat(saved.getCurrency()).isEqualTo("INR");
            assertThat(saved.getCycle()).isEqualTo(BillingCycle.MONTHLY);
            assertThat(saved.getAmount()).isEqualByComparingTo(new BigDecimal("499.0000"));
            assertThat(saved.isActive()).isTrue();
            assertThat(saved.getVersion()).isEqualTo(0L);
        }

        @Test
        @DisplayName("MONTHLY and ANNUAL prices for same plan/region/currency coexist as separate rows")
        void save_monthlyAndAnnual_coexistAsSeparateRows() {
            PlanPrice monthly = planPriceRepository.save(
                buildPrice("INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("499.0000")));
            PlanPrice annual  = planPriceRepository.save(
                buildPrice("INDIA", "INR", BillingCycle.ANNUAL,  new BigDecimal("4990.0000")));

            assertThat(monthly.getId()).isNotEqualTo(annual.getId());
            assertThat(planPriceRepository.findByPlanId(planId)).hasSize(2);
        }
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("returns price when ID exists")
        void findById_exists_returnsPrice() {
            PlanPrice saved = planPriceRepository.save(
                buildPrice("US", "USD", BillingCycle.MONTHLY, new BigDecimal("9.9900")));

            Optional<PlanPrice> found = planPriceRepository.findById(saved.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getCurrency()).isEqualTo("USD");
            assertThat(found.get().getCycle()).isEqualTo(BillingCycle.MONTHLY);
        }

        @Test
        @DisplayName("returns empty when ID does not exist")
        void findById_missing_returnsEmpty() {
            assertThat(planPriceRepository.findById(UUID.randomUUID())).isEmpty();
        }
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("returns all active price rows ordered by planId")
        void findAll_multiple_returnsAll() {
            planPriceRepository.save(buildPrice("INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("499")));
            planPriceRepository.save(buildPrice("INDIA", "INR", BillingCycle.ANNUAL,  new BigDecimal("4990")));
            planPriceRepository.save(buildPrice("US",    "USD", BillingCycle.MONTHLY, new BigDecimal("9.99")));

            assertThat(planPriceRepository.findAll()).hasSize(3);
        }

        @Test
        @DisplayName("returns empty list when no prices exist")
        void findAll_empty_returnsEmptyList() {
            assertThat(planPriceRepository.findAll()).isEmpty();
        }
    }

    // ── findByPlanId ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByPlanId")
    class FindByPlanId {

        @Test
        @DisplayName("returns only prices for the given plan")
        void findByPlanId_multipleRegions_returnsAllForPlan() {
            planPriceRepository.save(buildPrice("INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("499")));
            planPriceRepository.save(buildPrice("INDIA", "INR", BillingCycle.ANNUAL,  new BigDecimal("4990")));
            planPriceRepository.save(buildPrice("US",    "USD", BillingCycle.MONTHLY, new BigDecimal("9.99")));

            List<PlanPrice> results = planPriceRepository.findByPlanId(planId);

            assertThat(results).hasSize(3);
            assertThat(results).allMatch(p -> p.getPlanId().equals(planId));
        }

        @Test
        @DisplayName("returns empty list when plan has no prices")
        void findByPlanId_noPrices_returnsEmpty() {
            assertThat(planPriceRepository.findByPlanId(UUID.randomUUID())).isEmpty();
        }
    }

    // ── findByPlanIdAndRegionAndCurrency ──────────────────────────────────────

    @Nested
    @DisplayName("findByPlanIdAndRegionAndCurrency")
    class FindByPlanIdAndRegionAndCurrency {

        @Test
        @DisplayName("returns only prices matching planId + region + currency")
        void find_matchingKey_returnsCorrectRows() {
            planPriceRepository.save(buildPrice("INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("499")));
            planPriceRepository.save(buildPrice("INDIA", "INR", BillingCycle.ANNUAL,  new BigDecimal("4990")));
            planPriceRepository.save(buildPrice("US",    "USD", BillingCycle.MONTHLY, new BigDecimal("9.99")));

            List<PlanPrice> inr = planPriceRepository
                .findByPlanIdAndRegionAndCurrency(planId, "INDIA", "INR");

            assertThat(inr).hasSize(2);
            assertThat(inr).allMatch(p -> p.getCurrency().equals("INR"));
        }

        @Test
        @DisplayName("returns empty list when no matching combination exists")
        void find_noMatch_returnsEmpty() {
            assertThat(planPriceRepository
                .findByPlanIdAndRegionAndCurrency(planId, "EU", "EUR"))
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
            planPriceRepository.save(
                buildPrice(planId, "INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("499"), date));

            assertThat(planPriceRepository.exists(planId, "INDIA", "INR", BillingCycle.MONTHLY, date))
                .isTrue();
        }

        @Test
        @DisplayName("returns false when effective_from differs")
        void exists_differentDate_returnsFalse() {
            LocalDate date = LocalDate.of(2025, 1, 1);
            planPriceRepository.save(
                buildPrice(planId, "INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("499"), date));

            assertThat(planPriceRepository.exists(planId, "INDIA", "INR",
                BillingCycle.MONTHLY, LocalDate.of(2026, 1, 1)))
                .isFalse();
        }

        @Test
        @DisplayName("returns false when cycle differs")
        void exists_differentCycle_returnsFalse() {
            LocalDate date = LocalDate.of(2025, 1, 1);
            planPriceRepository.save(
                buildPrice(planId, "INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("499"), date));

            assertThat(planPriceRepository.exists(planId, "INDIA", "INR",
                BillingCycle.ANNUAL, date))
                .isFalse();
        }

        @Test
        @DisplayName("returns false when no price exists for key")
        void exists_noRow_returnsFalse() {
            assertThat(planPriceRepository.exists(
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
            PlanPrice saved = planPriceRepository.save(
                buildPrice("INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("499")));

            planPriceRepository.softDelete(saved.getId(), DEV_USER);

            assertThat(planPriceRepository.findById(saved.getId())).isEmpty();
        }

        @Test
        @DisplayName("softDelete hides price from findByPlanId")
        void softDelete_hiddenFromFindByPlanId() {
            PlanPrice saved = planPriceRepository.save(
                buildPrice("INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("499")));

            planPriceRepository.softDelete(saved.getId(), DEV_USER);

            assertThat(planPriceRepository.findByPlanId(planId)).isEmpty();
        }

        @Test
        @DisplayName("softDelete hides price from findAll")
        void softDelete_hiddenFromFindAll() {
            PlanPrice saved = planPriceRepository.save(
                buildPrice("US", "USD", BillingCycle.ANNUAL, new BigDecimal("99.99")));

            planPriceRepository.softDelete(saved.getId(), DEV_USER);

            assertThat(planPriceRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("softDelete makes exists() return false for the key")
        void softDelete_existsReturnsFalse() {
            LocalDate date = LocalDate.of(2025, 1, 1);
            PlanPrice saved = planPriceRepository.save(
                buildPrice(planId, "INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("499"), date));

            planPriceRepository.softDelete(saved.getId(), DEV_USER);

            assertThat(planPriceRepository.exists(planId, "INDIA", "INR", BillingCycle.MONTHLY, date))
                .isFalse();
        }

        @Test
        @DisplayName("soft-deleted row physically retained in DB (deleted_at is set)")
        void softDelete_physicalRowRetained() {
            PlanPrice saved = planPriceRepository.save(
                buildPrice("INDIA", "INR", BillingCycle.ANNUAL, new BigDecimal("4990")));

            planPriceRepository.softDelete(saved.getId(), DEV_USER);

            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ppm_plan_prices WHERE id = ?",
                Integer.class, saved.getId());
            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("softDelete on unknown ID throws ResourceNotFoundException")
        void softDelete_unknownId_throwsNotFoundException() {
            assertThatThrownBy(() ->
                planPriceRepository.softDelete(UUID.randomUUID(), DEV_USER))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("active filtering — soft-deleted price excluded; active one still returned")
        void softDelete_activeFiltering_onlyActiveReturned() {
            PlanPrice active  = planPriceRepository.save(
                buildPrice("INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("499")));
            PlanPrice deleted = planPriceRepository.save(
                buildPrice("US",    "USD", BillingCycle.ANNUAL,  new BigDecimal("99.99")));

            planPriceRepository.softDelete(deleted.getId(), DEV_USER);

            List<PlanPrice> all = planPriceRepository.findAll();
            assertThat(all).hasSize(1);
            assertThat(all.get(0).getId()).isEqualTo(active.getId());
        }
    }

    // ── unique constraint ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("unique pricing key constraint")
    class UniqueConstraint {

        @Test
        @DisplayName("throws DataIntegrityViolationException on duplicate (planId,region,currency,cycle,effectiveFrom)")
        void save_duplicateKey_throwsConstraintViolation() {
            LocalDate date = LocalDate.of(2025, 1, 1);
            planPriceRepository.save(
                buildPrice(planId, "INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("499"), date));

            assertThatThrownBy(() ->
                planPriceRepository.save(
                    buildPrice(planId, "INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("599"), date)))
                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("constraint violation message contains index name for GlobalExceptionHandler detection")
        void save_duplicateKey_exceptionContainsIndexName() {
            LocalDate date = LocalDate.of(2025, 6, 1);
            planPriceRepository.save(
                buildPrice(planId, "US", "USD", BillingCycle.ANNUAL, new BigDecimal("99.99"), date));

            assertThatThrownBy(() ->
                planPriceRepository.save(
                    buildPrice(planId, "US", "USD", BillingCycle.ANNUAL, new BigDecimal("109.99"), date)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_ppm_plan_prices_active");
        }

        @Test
        @DisplayName("same key with different effectiveFrom is allowed (distinct price versions)")
        void save_sameKeyDifferentDate_succeeds() {
            planPriceRepository.save(
                buildPrice(planId, "INDIA", "INR", BillingCycle.MONTHLY,
                    new BigDecimal("499"), LocalDate.of(2025, 1, 1)));

            PlanPrice nextVersion = planPriceRepository.save(
                buildPrice(planId, "INDIA", "INR", BillingCycle.MONTHLY,
                    new BigDecimal("599"), LocalDate.of(2026, 1, 1)));

            assertThat(nextVersion.getId()).isNotNull();
            assertThat(planPriceRepository.findByPlanIdAndRegionAndCurrency(planId, "INDIA", "INR"))
                .hasSize(2);
        }

        @Test
        @DisplayName("soft-deleted key can be reused by a new price row (partial index)")
        void save_sameKeyAfterSoftDelete_succeeds() {
            LocalDate date = LocalDate.of(2025, 1, 1);
            PlanPrice first = planPriceRepository.save(
                buildPrice(planId, "INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("499"), date));

            planPriceRepository.softDelete(first.getId(), DEV_USER);

            // Partial unique index is WHERE deleted_at IS NULL — reuse must succeed
            PlanPrice second = planPriceRepository.save(
                buildPrice(planId, "INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("549"), date));

            assertThat(second.getId()).isNotEqualTo(first.getId());
            assertThat(planPriceRepository.findById(second.getId())).isPresent();
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

            PlanPrice saved = planPriceRepository.save(
                buildPrice("INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("499")));

            assertThat(saved.getCreatedAt()).isAfter(before);
            assertThat(saved.getUpdatedAt()).isAfter(before);
            assertThat(saved.getCreatedBy()).isEqualTo(DEV_USER);
            assertThat(saved.getUpdatedBy()).isEqualTo(DEV_USER);
        }

        @Test
        @DisplayName("version is 0 after initial save")
        void save_newPrice_versionIsZero() {
            PlanPrice saved = planPriceRepository.save(
                buildPrice("US", "USD", BillingCycle.ANNUAL, new BigDecimal("99.99")));

            assertThat(saved.getVersion()).isEqualTo(0L);
        }
    }

    // ── billing cycle wire value persistence ──────────────────────────────────

    @Nested
    @DisplayName("billing cycle wire value persistence")
    class BillingCycleWireValue {

        @Test
        @DisplayName("MONTHLY stored as 'monthly', not 'MONTHLY'")
        void save_monthly_storedAsWireValue() {
            PlanPrice saved = planPriceRepository.save(
                buildPrice("INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("499")));

            String stored = jdbcTemplate.queryForObject(
                "SELECT cycle FROM ppm_plan_prices WHERE id = ?",
                String.class, saved.getId());

            assertThat(stored).isEqualTo("monthly");
            assertThat(stored).doesNotContain("MONTHLY");
        }

        @Test
        @DisplayName("ANNUAL stored as 'annual', not 'ANNUAL'")
        void save_annual_storedAsWireValue() {
            PlanPrice saved = planPriceRepository.save(
                buildPrice("INDIA", "INR", BillingCycle.ANNUAL, new BigDecimal("4990")));

            String stored = jdbcTemplate.queryForObject(
                "SELECT cycle FROM ppm_plan_prices WHERE id = ?",
                String.class, saved.getId());

            assertThat(stored).isEqualTo("annual");
            assertThat(stored).doesNotContain("ANNUAL");
        }

        @Test
        @DisplayName("BillingCycle reconstituted correctly from DB wire value")
        void findById_afterSave_cycleReconstitutedCorrectly() {
            PlanPrice saved = planPriceRepository.save(
                buildPrice("US", "USD", BillingCycle.ANNUAL, new BigDecimal("99.99")));

            Optional<PlanPrice> found = planPriceRepository.findById(saved.getId());

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
            PlanPrice original = planPriceRepository.save(
                buildPrice("INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("499")));

            // First update — version 0 → DB version becomes 1
            PlanPrice firstUpdate = PlanPrice.builder()
                .id(original.getId())
                .version(original.getVersion())   // 0
                .planId(original.getPlanId())
                .region(original.getRegion())
                .currency(original.getCurrency())
                .cycle(original.getCycle())
                .amount(new BigDecimal("599.0000"))
                .taxInclusive(original.isTaxInclusive())
                .effectiveFrom(original.getEffectiveFrom())
                .active(original.isActive())
                .createdAt(original.getCreatedAt())
                .updatedAt(Instant.now())
                .createdBy(original.getCreatedBy())
                .updatedBy(DEV_USER)
                .build();
            planPriceRepository.save(firstUpdate);

            // Second update from stale version 0 — must fail
            PlanPrice staleUpdate = PlanPrice.builder()
                .id(original.getId())
                .version(original.getVersion())   // still 0, DB has 1
                .planId(original.getPlanId())
                .region(original.getRegion())
                .currency(original.getCurrency())
                .cycle(original.getCycle())
                .amount(new BigDecimal("699.0000"))
                .taxInclusive(original.isTaxInclusive())
                .effectiveFrom(original.getEffectiveFrom())
                .active(original.isActive())
                .createdAt(original.getCreatedAt())
                .updatedAt(Instant.now())
                .createdBy(original.getCreatedBy())
                .updatedBy(DEV_USER)
                .build();

            assertThatThrownBy(() -> planPriceRepository.save(staleUpdate))
                .isInstanceOf(OptimisticLockingFailureException.class);
        }
    }

    // ── FK integrity ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("FK integrity")
    class FkIntegrity {

        @Test
        @DisplayName("saving a price against an unknown plan_id throws DataIntegrityViolationException")
        void save_unknownPlanId_throwsFkViolation() {
            UUID ghostPlanId = UUID.randomUUID();
            Instant now = Instant.now();

            PlanPrice orphan = PlanPrice.builder()
                .id(UUID.randomUUID())
                .planId(ghostPlanId)
                .region("INDIA").currency("INR")
                .cycle(BillingCycle.MONTHLY)
                .amount(new BigDecimal("499"))
                .taxInclusive(false)
                .effectiveFrom(LocalDate.of(2025, 1, 1))
                .active(true)
                .createdAt(now).updatedAt(now)
                .createdBy(DEV_USER).updatedBy(DEV_USER)
                .build();

            assertThatThrownBy(() -> planPriceRepository.save(orphan))
                .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
