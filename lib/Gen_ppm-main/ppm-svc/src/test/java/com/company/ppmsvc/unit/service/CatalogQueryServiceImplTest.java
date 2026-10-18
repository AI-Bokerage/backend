package com.company.ppmsvc.unit.service;

import com.company.ppmsvc.plan.model.CatalogPlanDetailResponse;
import com.company.ppmsvc.plan.model.CatalogPlanSummaryResponse;
import com.company.ppmsvc.plan.usecase.CatalogQueryServiceImpl;
import com.company.ppmsvc.entitlement.model.EntitlementType;
import com.company.ppmsvc.module.model.ModuleCode;
import com.company.ppmsvc.plan.model.PlanVisibility;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.entitlement.model.Entitlement;
import com.company.ppmsvc.module.model.Module;
import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.planentitlement.model.PlanEntitlement;
import com.company.ppmsvc.planmodule.model.PlanModule;
import com.company.ppmsvc.plan.model.PlanVersion;
import com.company.ppmsvc.entitlement.port.EntitlementRepositoryPort;
import com.company.ppmsvc.module.port.ModuleRepositoryPort;
import com.company.ppmsvc.planentitlement.port.PlanEntitlementRepositoryPort;
import com.company.ppmsvc.planmodule.port.PlanModuleRepositoryPort;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.plan.port.PlanVersionRepositoryPort;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CatalogQueryServiceImpl")
class CatalogQueryServiceImplTest {

    static final UUID PLAN_ID        = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID MODULE_ID      = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID ENTITLEMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    static final UUID VERSION_ID     = UUID.fromString("00000000-0000-0000-0000-000000000004");
    static final UUID ACTOR_ID       = UUID.fromString("00000000-0000-0000-0000-000000000005");

    @Mock PlanRepositoryPort            planRepository;
    @Mock PlanVersionRepositoryPort     planVersionRepository;
    @Mock PlanModuleRepositoryPort      planModuleRepository;
    @Mock ModuleRepositoryPort          moduleRepository;
    @Mock PlanEntitlementRepositoryPort planEntitlementRepository;
    @Mock EntitlementRepositoryPort     entitlementRepository;

    @InjectMocks CatalogQueryServiceImpl service;

    // ── fixtures ──────────────────────────────────────────────────────────────

    private Plan buildPlan(UUID id, String slug, PlanVisibility visibility, boolean active) {
        Instant now = Instant.now();
        return Plan.builder()
                .id(id).code("PLN-" + id.toString().substring(0, 4))
                .slug(slug).name("Test Plan " + slug)
                .tagline("tagline").description("description")
                .visibility(visibility).trialDays(7).active(active)
                .createdAt(now).updatedAt(now)
                .build();
    }

    private PlanVersion buildVersion(UUID versionId, UUID planId, int versionNo,
                                     boolean active, LocalDate effectiveFrom) {
        Instant now = Instant.now();
        return PlanVersion.builder()
                .id(versionId).planId(planId)
                .versionNo(versionNo).effectiveFrom(effectiveFrom)
                .effectiveTo(null).active(active)
                .createdAt(now).updatedAt(now)
                .createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
                .build();
    }

    private Module buildModule(UUID id, ModuleCode code) {
        Instant now = Instant.now();
        return Module.builder()
                .id(id).code(code).name(code.getValue()).description("desc")
                .active(true)
                .createdAt(now).updatedAt(now)
                .createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
                .build();
    }

    private Entitlement buildEntitlement(UUID id, String code) {
        Instant now = Instant.now();
        return Entitlement.builder()
                .id(id).code(code).name(code).description("desc")
                .type(EntitlementType.QUOTA).active(true)
                .createdAt(now).updatedAt(now)
                .createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
                .build();
    }

    private PlanModule buildPlanModule(UUID planId, UUID moduleId) {
        Instant now = Instant.now();
        return PlanModule.builder()
                .id(UUID.randomUUID()).planId(planId).moduleId(moduleId)
                .createdAt(now).createdBy(ACTOR_ID)
                .build();
    }

    private PlanEntitlement buildPlanEntitlement(UUID planId, UUID entitlementId, String value) {
        Instant now = Instant.now();
        return PlanEntitlement.builder()
                .id(UUID.randomUUID()).planId(planId)
                .entitlementId(entitlementId).value(value)
                .createdAt(now).createdBy(ACTOR_ID)
                .build();
    }

    // ── listPublicPlans ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("listPublicPlans()")
    class ListPublicPlans {

        @Test
        @DisplayName("returns empty list when no active public plans exist")
        void listPublicPlans_noPlansCatalog_returnsEmptyList() {
            when(planRepository.findAll()).thenReturn(List.of());

            List<CatalogPlanSummaryResponse> result = service.listPublicPlans();

            assertThat(result).isEmpty();
            verify(planVersionRepository, never()).findAll();
        }

        @Test
        @DisplayName("filters out PRIVATE and LEGACY plans — only PUBLIC included")
        void listPublicPlans_mixedVisibility_onlyPublicReturned() {
            Plan pub     = buildPlan(PLAN_ID,          "pub",   PlanVisibility.PUBLIC,  true);
            Plan priv    = buildPlan(UUID.randomUUID(), "priv",  PlanVisibility.PRIVATE, true);
            Plan legacy  = buildPlan(UUID.randomUUID(), "legac", PlanVisibility.LEGACY,  true);
            when(planRepository.findAll()).thenReturn(List.of(pub, priv, legacy));
            when(planVersionRepository.findAll()).thenReturn(List.of());

            List<CatalogPlanSummaryResponse> result = service.listPublicPlans();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).slug()).isEqualTo("pub");
        }

        @Test
        @DisplayName("filters out inactive plans — only active included")
        void listPublicPlans_inactivePlan_excluded() {
            Plan active   = buildPlan(PLAN_ID,          "active",   PlanVisibility.PUBLIC, true);
            Plan inactive = buildPlan(UUID.randomUUID(), "inactive", PlanVisibility.PUBLIC, false);
            when(planRepository.findAll()).thenReturn(List.of(active, inactive));
            when(planVersionRepository.findAll()).thenReturn(List.of());

            List<CatalogPlanSummaryResponse> result = service.listPublicPlans();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).slug()).isEqualTo("active");
        }

        @Test
        @DisplayName("plan with no version rows has null latestVersion")
        void listPublicPlans_planWithNoVersions_latestVersionIsNull() {
            Plan plan = buildPlan(PLAN_ID, "starter", PlanVisibility.PUBLIC, true);
            when(planRepository.findAll()).thenReturn(List.of(plan));
            when(planVersionRepository.findAll()).thenReturn(List.of());

            List<CatalogPlanSummaryResponse> result = service.listPublicPlans();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).latestVersion()).isNull();
        }

        @Test
        @DisplayName("latest active version is resolved from grouped version rows (no N+1)")
        void listPublicPlans_versionsGroupedByPlanId_latestActiveVersionSelected() {
            Plan plan = buildPlan(PLAN_ID, "starter", PlanVisibility.PUBLIC, true);

            // versionNo desc order (as findAll() guarantees)
            PlanVersion v2 = buildVersion(UUID.randomUUID(), PLAN_ID, 2, true,  LocalDate.now().minusDays(10));
            PlanVersion v1 = buildVersion(UUID.randomUUID(), PLAN_ID, 1, true,  LocalDate.now().minusDays(90));

            when(planRepository.findAll()).thenReturn(List.of(plan));
            when(planVersionRepository.findAll()).thenReturn(List.of(v2, v1));

            List<CatalogPlanSummaryResponse> result = service.listPublicPlans();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).latestVersion()).isNotNull();
            assertThat(result.get(0).latestVersion().versionNo()).isEqualTo(2);
        }

        @Test
        @DisplayName("results are ordered by plan name ASC regardless of DB return order")
        void listPublicPlans_multiplePublicPlans_orderedByNameAsc() {
            Plan zebra  = buildPlan(UUID.randomUUID(), "zebra",  PlanVisibility.PUBLIC, true);
            Plan alpha  = buildPlan(UUID.randomUUID(), "alpha",  PlanVisibility.PUBLIC, true);
            Plan middle = buildPlan(UUID.randomUUID(), "middle", PlanVisibility.PUBLIC, true);
            // DB returns in arbitrary order — service must sort
            when(planRepository.findAll()).thenReturn(List.of(zebra, alpha, middle));
            when(planVersionRepository.findAll()).thenReturn(List.of());

            List<CatalogPlanSummaryResponse> result = service.listPublicPlans();

            assertThat(result).extracting(CatalogPlanSummaryResponse::name)
                    .containsExactly(
                        "Test Plan alpha",
                        "Test Plan middle",
                        "Test Plan zebra");
        }

        @Test
        @DisplayName("inactive version rows are skipped; null latestVersion when all inactive")
        void listPublicPlans_allVersionsInactive_latestVersionIsNull() {
            Plan plan = buildPlan(PLAN_ID, "starter", PlanVisibility.PUBLIC, true);
            PlanVersion v1 = buildVersion(VERSION_ID, PLAN_ID, 1, false, LocalDate.now().minusDays(30));

            when(planRepository.findAll()).thenReturn(List.of(plan));
            when(planVersionRepository.findAll()).thenReturn(List.of(v1));

            List<CatalogPlanSummaryResponse> result = service.listPublicPlans();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).latestVersion()).isNull();
        }
    }

    // ── getPlanDetail ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getPlanDetail(String slug)")
    class GetPlanDetail {

        @Test
        @DisplayName("unknown slug throws PLAN_NOT_FOUND")
        void getPlanDetail_unknownSlug_throwsResourceNotFoundException() {
            when(planRepository.findBySlug("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getPlanDetail("ghost"))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(planVersionRepository, never()).findByPlanIdOrderByVersionNoDesc(any());
        }

        @Test
        @DisplayName("plan with no modules, entitlements, or versions returns empty lists and null latestVersion")
        void getPlanDetail_emptyPlan_returnsEmptyComposition() {
            Plan plan = buildPlan(PLAN_ID, "starter", PlanVisibility.PUBLIC, true);
            when(planRepository.findBySlug("starter")).thenReturn(Optional.of(plan));
            when(planVersionRepository.findByPlanIdOrderByVersionNoDesc(PLAN_ID)).thenReturn(List.of());
            when(planModuleRepository.findByPlanId(PLAN_ID)).thenReturn(List.of());
            when(planEntitlementRepository.findByPlanId(PLAN_ID)).thenReturn(List.of());

            CatalogPlanDetailResponse result = service.getPlanDetail("starter");

            assertThat(result.planId()).isEqualTo(PLAN_ID);
            assertThat(result.slug()).isEqualTo("starter");
            assertThat(result.latestVersion()).isNull();
            assertThat(result.modules()).isEmpty();
            assertThat(result.entitlements()).isEmpty();

            verify(moduleRepository, never()).findAll();
            verify(entitlementRepository, never()).findAllById(any());
        }

        @Test
        @DisplayName("latest active version is the first active row from findByPlanIdOrderByVersionNoDesc")
        void getPlanDetail_versionsPresent_latestActiveVersionResolved() {
            Plan plan = buildPlan(PLAN_ID, "starter", PlanVisibility.PUBLIC, true);
            PlanVersion v2 = buildVersion(VERSION_ID, PLAN_ID, 2, true,  LocalDate.now().minusDays(5));
            PlanVersion v1 = buildVersion(UUID.randomUUID(), PLAN_ID, 1, true, LocalDate.now().minusDays(60));

            when(planRepository.findBySlug("starter")).thenReturn(Optional.of(plan));
            when(planVersionRepository.findByPlanIdOrderByVersionNoDesc(PLAN_ID))
                    .thenReturn(List.of(v2, v1));
            when(planModuleRepository.findByPlanId(PLAN_ID)).thenReturn(List.of());
            when(planEntitlementRepository.findByPlanId(PLAN_ID)).thenReturn(List.of());

            CatalogPlanDetailResponse result = service.getPlanDetail("starter");

            assertThat(result.latestVersion()).isNotNull();
            assertThat(result.latestVersion().versionId()).isEqualTo(VERSION_ID);
            assertThat(result.latestVersion().versionNo()).isEqualTo(2);
        }

        @Test
        @DisplayName("modules assembled from planModuleRepository + moduleRepository.findAll()")
        void getPlanDetail_withModules_modulesAssembledCorrectly() {
            Plan plan = buildPlan(PLAN_ID, "growth", PlanVisibility.PUBLIC, true);
            Module module = buildModule(MODULE_ID, ModuleCode.INVOICING);
            PlanModule planModule = buildPlanModule(PLAN_ID, MODULE_ID);

            when(planRepository.findBySlug("growth")).thenReturn(Optional.of(plan));
            when(planVersionRepository.findByPlanIdOrderByVersionNoDesc(PLAN_ID)).thenReturn(List.of());
            when(planModuleRepository.findByPlanId(PLAN_ID)).thenReturn(List.of(planModule));
            when(moduleRepository.findAll()).thenReturn(List.of(module));
            when(planEntitlementRepository.findByPlanId(PLAN_ID)).thenReturn(List.of());

            CatalogPlanDetailResponse result = service.getPlanDetail("growth");

            assertThat(result.modules()).hasSize(1);
            assertThat(result.modules().get(0).moduleId()).isEqualTo(MODULE_ID);
            assertThat(result.modules().get(0).code()).isEqualTo(ModuleCode.INVOICING);
        }

        @Test
        @DisplayName("entitlements assembled with plan-specific value from PlanEntitlement")
        void getPlanDetail_withEntitlements_entitlementsAssembledCorrectly() {
            Plan plan = buildPlan(PLAN_ID, "growth", PlanVisibility.PUBLIC, true);
            Entitlement ent = buildEntitlement(ENTITLEMENT_ID, "max_users");
            PlanEntitlement pe = buildPlanEntitlement(PLAN_ID, ENTITLEMENT_ID, "50");

            when(planRepository.findBySlug("growth")).thenReturn(Optional.of(plan));
            when(planVersionRepository.findByPlanIdOrderByVersionNoDesc(PLAN_ID)).thenReturn(List.of());
            when(planModuleRepository.findByPlanId(PLAN_ID)).thenReturn(List.of());
            when(planEntitlementRepository.findByPlanId(PLAN_ID)).thenReturn(List.of(pe));
            when(entitlementRepository.findAllById(Set.of(ENTITLEMENT_ID))).thenReturn(List.of(ent));

            CatalogPlanDetailResponse result = service.getPlanDetail("growth");

            assertThat(result.entitlements()).hasSize(1);
            assertThat(result.entitlements().get(0).entitlementId()).isEqualTo(ENTITLEMENT_ID);
            assertThat(result.entitlements().get(0).code()).isEqualTo("max_users");
            assertThat(result.entitlements().get(0).value()).isEqualTo("50");
            assertThat(result.entitlements().get(0).type()).isEqualTo(EntitlementType.QUOTA);
        }

        @Test
        @DisplayName("orphaned moduleId not returned by moduleRepository.findAll() is silently skipped")
        void getPlanDetail_orphanedModuleId_silentlySkipped() {
            Plan plan = buildPlan(PLAN_ID, "growth", PlanVisibility.PUBLIC, true);
            UUID orphanId = UUID.randomUUID();
            PlanModule planModule = buildPlanModule(PLAN_ID, orphanId);

            when(planRepository.findBySlug("growth")).thenReturn(Optional.of(plan));
            when(planVersionRepository.findByPlanIdOrderByVersionNoDesc(PLAN_ID)).thenReturn(List.of());
            when(planModuleRepository.findByPlanId(PLAN_ID)).thenReturn(List.of(planModule));
            when(moduleRepository.findAll()).thenReturn(List.of()); // orphan not present
            when(planEntitlementRepository.findByPlanId(PLAN_ID)).thenReturn(List.of());

            CatalogPlanDetailResponse result = service.getPlanDetail("growth");

            assertThat(result.modules()).isEmpty();
        }

        @Test
        @DisplayName("orphaned entitlementId not returned by findAllById() is silently skipped")
        void getPlanDetail_orphanedEntitlementId_silentlySkipped() {
            Plan plan = buildPlan(PLAN_ID, "growth", PlanVisibility.PUBLIC, true);
            UUID orphanId = UUID.randomUUID();
            PlanEntitlement pe = buildPlanEntitlement(PLAN_ID, orphanId, "10");

            when(planRepository.findBySlug("growth")).thenReturn(Optional.of(plan));
            when(planVersionRepository.findByPlanIdOrderByVersionNoDesc(PLAN_ID)).thenReturn(List.of());
            when(planModuleRepository.findByPlanId(PLAN_ID)).thenReturn(List.of());
            when(planEntitlementRepository.findByPlanId(PLAN_ID)).thenReturn(List.of(pe));
            when(entitlementRepository.findAllById(Set.of(orphanId))).thenReturn(List.of()); // orphan absent

            CatalogPlanDetailResponse result = service.getPlanDetail("growth");

            assertThat(result.entitlements()).isEmpty();
        }
    }
}
