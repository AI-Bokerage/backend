package com.company.ppmsvc.integration;

import com.company.ppmsvc.promocode.model.DiscountType;
import com.company.ppmsvc.plan.model.PlanVisibility;
import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.promocode.model.PromoCode;
import com.company.ppmsvc.promocodeplan.model.PromoCodePlan;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.promocodeplan.port.PromoCodePlanRepositoryPort;
import com.company.ppmsvc.promocode.port.PromoCodeRepositoryPort;
import com.company.ppmsvc.infrastructure.persistence.repository.PlanJpaRepository;
import com.company.ppmsvc.infrastructure.persistence.repository.PromoCodePlanJpaRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Repository-level integration tests for Promo Code Plan Restriction persistence (PPM-07).
 *
 * <p>All tests run against a real PostgreSQL instance managed by Testcontainers.
 * Flyway applies V001–V009 before the context starts, ensuring {@code ppm_promo_code_plans}
 * and all its constraints are in place.
 *
 * <p>No class-level {@code @Transactional} — constraint tests require the INSERT to
 * flush to the DB before the violation is observable.  Cleanup is via {@code @AfterEach}
 * hard-delete in FK-safe order.
 */
@DisplayName("Promo Code Plan Restriction — Repository")
class PromoCodePlanRepositoryTest extends AbstractContainerIntegrationTest {

    @Autowired private PromoCodePlanRepositoryPort promoCodePlanRepository;
    @Autowired private PromoCodeRepositoryPort     promoCodeRepository;
    @Autowired private PlanRepositoryPort          planRepository;
    @Autowired private PromoCodePlanJpaRepository  promoCodePlanJpaRepository;
    @Autowired private PlanJpaRepository           planJpaRepository;
    @Autowired private JdbcTemplate                jdbcTemplate;

    private UUID promoCodeId;
    private UUID planId1;
    private UUID planId2;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();

        promoCodeId = UUID.randomUUID();
        planId1     = UUID.randomUUID();
        planId2     = UUID.randomUUID();

        promoCodeRepository.save(PromoCode.builder()
            .id(promoCodeId)
            .code("SETUP-CODE-" + promoCodeId.toString().substring(0, 8))
            .discountType(DiscountType.PERCENTAGE)
            .value(new BigDecimal("10.0000"))
            .validFrom(LocalDate.of(2025, 1, 1))
            .validUntil(LocalDate.of(2025, 12, 31))
            .usageCount(0)
            .firstTimeOnly(false)
            .active(true)
            .createdAt(now).updatedAt(now)
            .createdBy(DEV_USER).updatedBy(DEV_USER)
            .build());

        planRepository.save(Plan.builder()
            .id(planId1)
            .code("PCPT-P1-" + planId1.toString().substring(0, 8))
            .slug("pcpt-p1-" + planId1.toString().substring(0, 8))
            .name("Promo Code Plan Test 1")
            .visibility(PlanVisibility.PUBLIC)
            .trialDays(0).active(true)
            .createdAt(now).updatedAt(now)
            .build());

        planRepository.save(Plan.builder()
            .id(planId2)
            .code("PCPT-P2-" + planId2.toString().substring(0, 8))
            .slug("pcpt-p2-" + planId2.toString().substring(0, 8))
            .name("Promo Code Plan Test 2")
            .visibility(PlanVisibility.PUBLIC)
            .trialDays(0).active(true)
            .createdAt(now).updatedAt(now)
            .build());
    }

    @AfterEach
    void cleanUp() {
        // FK-safe order: restriction rows → promo codes → plans
        jdbcTemplate.execute("DELETE FROM ppm_promo_code_plans");
        jdbcTemplate.execute("DELETE FROM ppm_promo_codes");
        planJpaRepository.deleteAll();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private PromoCodePlan buildMapping(UUID pcId, UUID pId) {
        return PromoCodePlan.builder()
            .id(UUID.randomUUID())
            .promoCodeId(pcId)
            .planId(pId)
            .createdAt(Instant.now())
            .createdBy(DEV_USER)
            .build();
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("persists a new restriction and returns the saved state")
        void save_valid_persistsAndReturns() {
            PromoCodePlan saved = promoCodePlanRepository.save(buildMapping(promoCodeId, planId1));

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getPromoCodeId()).isEqualTo(promoCodeId);
            assertThat(saved.getPlanId()).isEqualTo(planId1);
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getCreatedBy()).isEqualTo(DEV_USER);
        }
    }

    // ── saveAll ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("saveAll")
    class SaveAll {

        @Test
        @DisplayName("persists multiple restrictions in a single call and returns all saved states")
        void saveAll_twoMappings_persistsAndReturnsAll() {
            List<PromoCodePlan> batch = List.of(
                buildMapping(promoCodeId, planId1),
                buildMapping(promoCodeId, planId2));

            List<PromoCodePlan> saved = promoCodePlanRepository.saveAll(batch);

            assertThat(saved).hasSize(2);
            assertThat(saved).extracting(PromoCodePlan::getPlanId)
                .containsExactlyInAnyOrder(planId1, planId2);
        }

        @Test
        @DisplayName("saveAll with empty list is a no-op returning empty list")
        void saveAll_empty_returnsEmptyList() {
            List<PromoCodePlan> result = promoCodePlanRepository.saveAll(List.of());
            assertThat(result).isEmpty();
        }
    }

    // ── findByPromoCodeId ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByPromoCodeId")
    class FindByPromoCodeId {

        @Test
        @DisplayName("returns all restrictions for the given promo code in insertion order")
        void findByPromoCodeId_twoPlans_returnsBothInOrder() {
            promoCodePlanRepository.save(buildMapping(promoCodeId, planId1));
            promoCodePlanRepository.save(buildMapping(promoCodeId, planId2));

            List<PromoCodePlan> result = promoCodePlanRepository.findByPromoCodeId(promoCodeId);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(PromoCodePlan::getPlanId)
                .containsExactlyInAnyOrder(planId1, planId2);
        }

        @Test
        @DisplayName("returns empty list when promo code has no restrictions")
        void findByPromoCodeId_noRestrictions_returnsEmpty() {
            assertThat(promoCodePlanRepository.findByPromoCodeId(promoCodeId)).isEmpty();
        }
    }

    // ── findByPlanId ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByPlanId")
    class FindByPlanId {

        @Test
        @DisplayName("returns all restrictions referencing the given plan")
        void findByPlanId_twoPromoCodes_returnsBoth() {
            // Create a second promo code
            UUID anotherPromoId = UUID.randomUUID();
            Instant now = Instant.now();
            promoCodeRepository.save(PromoCode.builder()
                .id(anotherPromoId)
                .code("ANOTHER-" + anotherPromoId.toString().substring(0, 8))
                .discountType(DiscountType.FLAT)
                .value(new BigDecimal("5.0000"))
                .validFrom(LocalDate.of(2025, 1, 1))
                .validUntil(LocalDate.of(2025, 12, 31))
                .usageCount(0)
                .firstTimeOnly(false)
                .active(true)
                .createdAt(now).updatedAt(now)
                .createdBy(DEV_USER).updatedBy(DEV_USER)
                .build());

            promoCodePlanRepository.save(buildMapping(promoCodeId, planId1));
            promoCodePlanRepository.save(buildMapping(anotherPromoId, planId1));

            List<PromoCodePlan> result = promoCodePlanRepository.findByPlanId(planId1);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(PromoCodePlan::getPromoCodeId)
                .containsExactlyInAnyOrder(promoCodeId, anotherPromoId);
        }

        @Test
        @DisplayName("returns empty list when no restrictions reference the plan")
        void findByPlanId_noRestrictions_returnsEmpty() {
            assertThat(promoCodePlanRepository.findByPlanId(planId1)).isEmpty();
        }
    }

    // ── exists ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("exists")
    class Exists {

        @Test
        @DisplayName("returns true when restriction exists for (promoCodeId, planId)")
        void exists_present_returnsTrue() {
            promoCodePlanRepository.save(buildMapping(promoCodeId, planId1));

            assertThat(promoCodePlanRepository.exists(promoCodeId, planId1)).isTrue();
        }

        @Test
        @DisplayName("returns false when restriction is absent")
        void exists_absent_returnsFalse() {
            assertThat(promoCodePlanRepository.exists(promoCodeId, planId1)).isFalse();
        }
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("removes the specified restriction only")
        void delete_existing_removesOneRow() {
            promoCodePlanRepository.save(buildMapping(promoCodeId, planId1));
            promoCodePlanRepository.save(buildMapping(promoCodeId, planId2));

            promoCodePlanRepository.delete(promoCodeId, planId1);

            assertThat(promoCodePlanRepository.exists(promoCodeId, planId1)).isFalse();
            assertThat(promoCodePlanRepository.exists(promoCodeId, planId2)).isTrue();
        }

        @Test
        @DisplayName("is a no-op when restriction does not exist")
        void delete_absent_isNoOp() {
            promoCodePlanRepository.delete(promoCodeId, planId1); // must not throw
        }
    }

    // ── deleteAllByPromoCodeId ────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteAllByPromoCodeId")
    class DeleteAllByPromoCodeId {

        @Test
        @DisplayName("removes all restrictions for the given promo code")
        void deleteAllByPromoCodeId_twoRestrictions_removesAll() {
            promoCodePlanRepository.save(buildMapping(promoCodeId, planId1));
            promoCodePlanRepository.save(buildMapping(promoCodeId, planId2));

            promoCodePlanRepository.deleteAllByPromoCodeId(promoCodeId);

            assertThat(promoCodePlanRepository.findByPromoCodeId(promoCodeId)).isEmpty();
        }

        @Test
        @DisplayName("G4 — promo code and plan rows survive deleteAllByPromoCodeId (no cascade)")
        void deleteAllByPromoCodeId_parentRowsRetained() {
            promoCodePlanRepository.save(buildMapping(promoCodeId, planId1));

            promoCodePlanRepository.deleteAllByPromoCodeId(promoCodeId);

            // Promo code row must still exist
            assertThat(promoCodeRepository.findById(promoCodeId)).isPresent();
            // Plan row must still exist (verified via raw JDBC — planRepository has @SQLRestriction too)
            Integer planCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ppm_plans WHERE id = ?", Integer.class, planId1);
            assertThat(planCount).isEqualTo(1);
        }

        @Test
        @DisplayName("is a no-op when promo code has no restrictions")
        void deleteAllByPromoCodeId_none_isNoOp() {
            promoCodePlanRepository.deleteAllByPromoCodeId(promoCodeId); // must not throw
        }
    }

    // ── unique constraint ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("unique constraint")
    class UniqueConstraint {

        @Test
        @DisplayName("throws DataIntegrityViolationException on duplicate (promoCodeId, planId)")
        void save_duplicateMappingKey_throwsConstraintViolation() {
            promoCodePlanRepository.save(buildMapping(promoCodeId, planId1));

            assertThatThrownBy(() -> promoCodePlanRepository.save(buildMapping(promoCodeId, planId1)))
                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("same promo code can be restricted to multiple plans")
        void save_samePromoCodeDifferentPlans_allowed() {
            promoCodePlanRepository.save(buildMapping(promoCodeId, planId1));
            promoCodePlanRepository.save(buildMapping(promoCodeId, planId2)); // must not throw

            assertThat(promoCodePlanRepository.findByPromoCodeId(promoCodeId)).hasSize(2);
        }
    }

    // ── FK integrity ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("FK integrity")
    class FkIntegrity {

        @Test
        @DisplayName("saving a restriction against an unknown promo_code_id throws DataIntegrityViolationException")
        void save_unknownPromoCodeId_throwsFkViolation() {
            assertThatThrownBy(() ->
                promoCodePlanRepository.save(buildMapping(UUID.randomUUID(), planId1)))
                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("saving a restriction against an unknown plan_id throws DataIntegrityViolationException")
        void save_unknownPlanId_throwsFkViolation() {
            assertThatThrownBy(() ->
                promoCodePlanRepository.save(buildMapping(promoCodeId, UUID.randomUUID())))
                .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("H3 — deleting a restriction does not delete the promo code or plan (no cascade)")
        void delete_doesNotCascadeToParentRows() {
            promoCodePlanRepository.save(buildMapping(promoCodeId, planId1));

            promoCodePlanRepository.delete(promoCodeId, planId1);

            // Mapping is gone
            assertThat(promoCodePlanRepository.exists(promoCodeId, planId1)).isFalse();
            // Promo code row must still exist
            assertThat(promoCodeRepository.findById(promoCodeId)).isPresent();
            // Plan row must still exist
            Integer planCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ppm_plans WHERE id = ?", Integer.class, planId1);
            assertThat(planCount).isEqualTo(1);
        }
    }
}
