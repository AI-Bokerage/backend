package com.company.ppmsvc.unit.service;

import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.plan.model.PlanVersion;
import com.company.ppmsvc.plan.model.PlanVersionLimitsResponse;
import com.company.ppmsvc.plan.model.PlanVersionMetaResponse;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.plan.port.PlanVersionRepositoryPort;
import com.company.ppmsvc.plan.usecase.PlanVersionApplicationServiceImpl;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlanVersionApplicationServiceImpl")
class PlanVersionApplicationServiceImplTest {

    static final UUID ACTOR_ID   = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID PLAN_ID    = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID VERSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    static final LocalDate DATE_2026_JAN  = LocalDate.of(2026, 1, 1);
    static final LocalDate DATE_2027_JAN  = LocalDate.of(2027, 1, 1);
    static final LocalDate DATE_2026_DEC  = LocalDate.of(2026, 12, 31);

    @Mock PlanVersionRepositoryPort planVersionRepository;
    @Mock PlanRepositoryPort        planRepository;

    @InjectMocks PlanVersionApplicationServiceImpl service;

    // ── fixtures ──────────────────────────────────────────────────────────────

    private PlanVersion buildVersion(UUID id, Integer versionNo,
                                      LocalDate effectiveFrom, LocalDate effectiveTo) {
        Instant now = Instant.now();
        return PlanVersion.builder()
            .id(id).version(0L)
            .planId(PLAN_ID)
            .versionNo(versionNo)
            .effectiveFrom(effectiveFrom)
            .effectiveTo(effectiveTo)
            .active(true)
            .createdAt(now).updatedAt(now)
            .createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
            .build();
    }

    private PlanVersion buildVersion(Integer versionNo,
                                      LocalDate effectiveFrom, LocalDate effectiveTo) {
        return buildVersion(UUID.randomUUID(), versionNo, effectiveFrom, effectiveTo);
    }

    private void stubPlanExists() {
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(Mockito.mock(Plan.class)));
    }

    private void stubNoExistingVersions() {
        when(planVersionRepository.existsByPlanIdAndVersionNo(any(), any())).thenReturn(false);
        when(planVersionRepository.findByPlanId(PLAN_ID)).thenReturn(List.of());
    }

    // ── createVersion ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createVersion()")
    class CreateVersion {

        @Test
        @DisplayName("success — first version, no existing versions")
        void create_success_firstVersion() {
            stubPlanExists();
            stubNoExistingVersions();
            PlanVersion saved = buildVersion(VERSION_ID, 1, DATE_2026_JAN, null);
            when(planVersionRepository.save(any())).thenReturn(saved);

            PlanVersion result = service.createVersion(ACTOR_ID, PLAN_ID, 1, DATE_2026_JAN,
                null, null, null, null, null, null, null, null);

            assertThat(result.getId()).isEqualTo(VERSION_ID);
            verify(planVersionRepository, times(1)).save(any());
        }

        @Test
        @DisplayName("BR-1 — plan not found throws PLAN_NOT_FOUND")
        void create_planNotFound_throws() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createVersion(ACTOR_ID, PLAN_ID, 1, DATE_2026_JAN,
                    null, null, null, null, null, null, null, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_NOT_FOUND));

            verify(planVersionRepository, never()).save(any());
        }

        @Test
        @DisplayName("BR-2 — duplicate versionNo throws PLAN_VERSION_ALREADY_EXISTS")
        void create_duplicateVersionNo_throws() {
            stubPlanExists();
            when(planVersionRepository.existsByPlanIdAndVersionNo(PLAN_ID, 1)).thenReturn(true);

            assertThatThrownBy(() -> service.createVersion(ACTOR_ID, PLAN_ID, 1, DATE_2026_JAN,
                    null, null, null, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_VERSION_ALREADY_EXISTS));

            verify(planVersionRepository, never()).save(any());
        }

        @Test
        @DisplayName("BR-3 — duplicate effectiveFrom throws PLAN_VERSION_ALREADY_EXISTS")
        void create_duplicateEffectiveFrom_throws() {
            stubPlanExists();
            when(planVersionRepository.existsByPlanIdAndVersionNo(PLAN_ID, 2)).thenReturn(false);
            // v1 already occupies effectiveFrom = DATE_2026_JAN
            PlanVersion v1 = buildVersion(1, DATE_2026_JAN, null);
            when(planVersionRepository.findByPlanId(PLAN_ID)).thenReturn(List.of(v1));

            assertThatThrownBy(() -> service.createVersion(ACTOR_ID, PLAN_ID, 2, DATE_2026_JAN,
                    null, null, null, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_VERSION_ALREADY_EXISTS));

            verify(planVersionRepository, never()).save(any());
        }

        @Test
        @DisplayName("BR-7 — effectiveFrom inside closed version range throws PLAN_VERSION_DATE_CONFLICT")
        void create_effectiveFromInsideClosedRange_throws() {
            stubPlanExists();
            when(planVersionRepository.existsByPlanIdAndVersionNo(PLAN_ID, 2)).thenReturn(false);
            // V1: 2026-01-01 → 2026-12-31 (closed)
            PlanVersion v1Closed = buildVersion(1, DATE_2026_JAN, DATE_2026_DEC);
            when(planVersionRepository.findByPlanId(PLAN_ID)).thenReturn(List.of(v1Closed));

            // Attempt to create V2 with effectiveFrom = 2026-06-01 which falls inside V1's range
            assertThatThrownBy(() -> service.createVersion(ACTOR_ID, PLAN_ID, 2, LocalDate.of(2026, 6, 1),
                    null, null, null, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_VERSION_DATE_CONFLICT));

            verify(planVersionRepository, never()).save(any());
        }

        @Test
        @DisplayName("BR-6 — previous open version auto-closed with effectiveTo = newEffectiveFrom.minusDays(1)")
        void create_autoClosesPreviousOpenVersion() {
            stubPlanExists();
            when(planVersionRepository.existsByPlanIdAndVersionNo(PLAN_ID, 2)).thenReturn(false);
            PlanVersion v1Open = buildVersion(UUID.randomUUID(), 1, DATE_2026_JAN, null);
            when(planVersionRepository.findByPlanId(PLAN_ID)).thenReturn(List.of(v1Open));

            PlanVersion savedV2 = buildVersion(VERSION_ID, 2, DATE_2027_JAN, null);
            when(planVersionRepository.save(any())).thenReturn(savedV2);

            service.createVersion(ACTOR_ID, PLAN_ID, 2, DATE_2027_JAN,
                null, null, null, null, null, null, null, null);

            // Two saves: first is the auto-closed v1, second is the new v2
            ArgumentCaptor<PlanVersion> captor = ArgumentCaptor.forClass(PlanVersion.class);
            verify(planVersionRepository, times(2)).save(captor.capture());

            PlanVersion autoClosedV1 = captor.getAllValues().get(0);
            assertThat(autoClosedV1.getId()).isEqualTo(v1Open.getId());
            assertThat(autoClosedV1.getEffectiveTo()).isEqualTo(DATE_2027_JAN.minusDays(1)); // 2026-12-31
            assertThat(autoClosedV1.getUpdatedBy()).isEqualTo(ACTOR_ID);
            assertThat(autoClosedV1.getUpdatedAt()).isAfter(v1Open.getCreatedAt());
        }

        @Test
        @DisplayName("BR-6 — no auto-close when no open-ended previous version exists")
        void create_noAutoCloseWhenNoPreviousOpen() {
            stubPlanExists();
            when(planVersionRepository.existsByPlanIdAndVersionNo(PLAN_ID, 2)).thenReturn(false);
            // V1 is already closed
            PlanVersion v1Closed = buildVersion(1, DATE_2026_JAN, DATE_2026_DEC);
            when(planVersionRepository.findByPlanId(PLAN_ID)).thenReturn(List.of(v1Closed));

            PlanVersion savedV2 = buildVersion(VERSION_ID, 2, DATE_2027_JAN, null);
            when(planVersionRepository.save(any())).thenReturn(savedV2);

            service.createVersion(ACTOR_ID, PLAN_ID, 2, DATE_2027_JAN,
                null, null, null, null, null, null, null, null);

            // Only one save: the new v2 (no auto-close)
            verify(planVersionRepository, times(1)).save(any());
        }

        @Test
        @DisplayName("BR-8 — active defaults to true when omitted")
        void create_activeDefaultsToTrue() {
            stubPlanExists();
            stubNoExistingVersions();
            when(planVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<PlanVersion> captor = ArgumentCaptor.forClass(PlanVersion.class);
            service.createVersion(ACTOR_ID, PLAN_ID, 1, DATE_2026_JAN,
                null, null, null, null, null, null, null, null);

            verify(planVersionRepository).save(captor.capture());
            assertThat(captor.getValue().isActive()).isTrue();
        }

        @Test
        @DisplayName("BR-8 — explicit active=false is respected")
        void create_explicitActiveFalseRespected() {
            stubPlanExists();
            stubNoExistingVersions();
            when(planVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<PlanVersion> captor = ArgumentCaptor.forClass(PlanVersion.class);
            service.createVersion(ACTOR_ID, PLAN_ID, 1, DATE_2026_JAN,
                false, null, null, null, null, null, null, null);

            verify(planVersionRepository).save(captor.capture());
            assertThat(captor.getValue().isActive()).isFalse();
        }

        @Test
        @DisplayName("BR-9 — audit fields set from the given actorId")
        void create_auditFieldsSetFromActor() {
            stubPlanExists();
            stubNoExistingVersions();
            when(planVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<PlanVersion> captor = ArgumentCaptor.forClass(PlanVersion.class);
            service.createVersion(ACTOR_ID, PLAN_ID, 1, DATE_2026_JAN,
                null, null, null, null, null, null, null, null);

            verify(planVersionRepository).save(captor.capture());
            assertThat(captor.getValue().getCreatedBy()).isEqualTo(ACTOR_ID);
            assertThat(captor.getValue().getUpdatedBy()).isEqualTo(ACTOR_ID);
            assertThat(captor.getValue().getCreatedAt()).isNotNull();
            assertThat(captor.getValue().getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("effectiveTo is always null on new version")
        void create_effectiveToIsNullOnNewVersion() {
            stubPlanExists();
            stubNoExistingVersions();
            when(planVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<PlanVersion> captor = ArgumentCaptor.forClass(PlanVersion.class);
            service.createVersion(ACTOR_ID, PLAN_ID, 1, DATE_2026_JAN,
                null, null, null, null, null, null, null, null);

            verify(planVersionRepository).save(captor.capture());
            assertThat(captor.getValue().getEffectiveTo()).isNull();
        }

        @Test
        @DisplayName("future effectiveFrom is accepted — no restriction to today or earlier")
        void create_futureEffectiveFrom_accepted() {
            stubPlanExists();
            stubNoExistingVersions();
            LocalDate future = LocalDate.now().plusYears(2);
            when(planVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<PlanVersion> captor = ArgumentCaptor.forClass(PlanVersion.class);
            service.createVersion(ACTOR_ID, PLAN_ID, 1, future,
                null, null, null, null, null, null, null, null);

            verify(planVersionRepository).save(captor.capture());
            assertThat(captor.getValue().getEffectiveFrom()).isEqualTo(future);
        }
    }

    // ── updateVersion ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateVersion()")
    class UpdateVersion {

        @Test
        @DisplayName("success — updates effectiveTo and active")
        void update_success() {
            PlanVersion existing = buildVersion(VERSION_ID, 1, DATE_2026_JAN, null);
            PlanVersion saved    = buildVersion(VERSION_ID, 1, DATE_2026_JAN, DATE_2026_DEC);

            when(planVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(existing));
            when(planVersionRepository.save(any())).thenReturn(saved);

            PlanVersion result = service.updateVersion(ACTOR_ID, VERSION_ID, DATE_2026_DEC,
                null, null, null, null, null, null, null, null);

            assertThat(result.getEffectiveTo()).isEqualTo(DATE_2026_DEC);
            verify(planVersionRepository).save(any());
        }

        @Test
        @DisplayName("not found — throws PLAN_VERSION_NOT_FOUND")
        void update_notFound_throws() {
            when(planVersionRepository.findById(VERSION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateVersion(ACTOR_ID, VERSION_ID, DATE_2026_DEC,
                    null, null, null, null, null, null, null, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_VERSION_NOT_FOUND));

            verify(planVersionRepository, never()).save(any());
        }

        @Test
        @DisplayName("VALIDATION_ERROR — effectiveTo before effectiveFrom throws")
        void update_effectiveToBeforeEffectiveFrom_throws() {
            PlanVersion existing = buildVersion(VERSION_ID, 1, DATE_2027_JAN, null);
            when(planVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> service.updateVersion(ACTOR_ID, VERSION_ID, DATE_2026_JAN,
                    null, null, null, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR));

            verify(planVersionRepository, never()).save(any());
        }

        @Test
        @DisplayName("BR-4 — identity fields planId, versionNo, effectiveFrom never changed")
        void update_immutableFieldsPreserved() {
            PlanVersion existing = buildVersion(VERSION_ID, 1, DATE_2026_JAN, null);

            when(planVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(existing));
            when(planVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<PlanVersion> captor = ArgumentCaptor.forClass(PlanVersion.class);
            service.updateVersion(ACTOR_ID, VERSION_ID, DATE_2026_DEC,
                null, null, null, null, null, null, null, null);

            verify(planVersionRepository).save(captor.capture());
            PlanVersion saved = captor.getValue();
            assertThat(saved.getPlanId()).isEqualTo(existing.getPlanId());
            assertThat(saved.getVersionNo()).isEqualTo(existing.getVersionNo());
            assertThat(saved.getEffectiveFrom()).isEqualTo(existing.getEffectiveFrom());
        }

        @Test
        @DisplayName("null fields leave existing values unchanged")
        void update_nullFieldsPreserveExisting() {
            PlanVersion existing = buildVersion(VERSION_ID, 1, DATE_2026_JAN, DATE_2026_DEC);

            when(planVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(existing));
            when(planVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<PlanVersion> captor = ArgumentCaptor.forClass(PlanVersion.class);
            service.updateVersion(ACTOR_ID, VERSION_ID, null,
                null, null, null, null, null, null, null, null);

            verify(planVersionRepository).save(captor.capture());
            PlanVersion saved = captor.getValue();
            assertThat(saved.getEffectiveTo()).isEqualTo(existing.getEffectiveTo());
            assertThat(saved.isActive()).isEqualTo(existing.isActive());
        }

        @Test
        @DisplayName("audit — createdAt/createdBy preserved; updatedAt/updatedBy refreshed")
        void update_auditFieldsPreservedAndRefreshed() {
            UUID originalCreator = UUID.fromString("00000000-0000-0000-0000-000000000099");
            Instant originalCreatedAt = Instant.now().minusSeconds(60);
            PlanVersion existing = PlanVersion.builder()
                .id(VERSION_ID).version(0L)
                .planId(PLAN_ID).versionNo(1)
                .effectiveFrom(DATE_2026_JAN).effectiveTo(null)
                .active(true)
                .createdAt(originalCreatedAt).updatedAt(originalCreatedAt)
                .createdBy(originalCreator).updatedBy(originalCreator)
                .build();

            when(planVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(existing));
            when(planVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<PlanVersion> captor = ArgumentCaptor.forClass(PlanVersion.class);
            service.updateVersion(ACTOR_ID, VERSION_ID, DATE_2026_DEC,
                null, null, null, null, null, null, null, null);

            verify(planVersionRepository).save(captor.capture());
            PlanVersion saved = captor.getValue();
            assertThat(saved.getCreatedAt()).isEqualTo(originalCreatedAt);  // preserved
            assertThat(saved.getCreatedBy()).isEqualTo(originalCreator);    // preserved
            assertThat(saved.getUpdatedAt()).isAfter(originalCreatedAt);    // refreshed
            assertThat(saved.getUpdatedBy()).isEqualTo(ACTOR_ID);           // refreshed
        }
    }

    // ── getVersion ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getVersion()")
    class GetVersion {

        @Test
        @DisplayName("success — returns version when it exists")
        void get_success() {
            PlanVersion version = buildVersion(VERSION_ID, 1, DATE_2026_JAN, null);
            when(planVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version));

            PlanVersion result = service.getVersion(VERSION_ID);

            assertThat(result.getId()).isEqualTo(VERSION_ID);
        }

        @Test
        @DisplayName("not found — throws PLAN_VERSION_NOT_FOUND")
        void get_notFound_throws() {
            when(planVersionRepository.findById(VERSION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getVersion(VERSION_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_VERSION_NOT_FOUND));
        }
    }

    // ── getLatestVersion ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("getLatestVersion()")
    class GetLatestVersion {

        @Test
        @DisplayName("success — returns highest versionNo (first from desc list)")
        void getLatest_success_returnsHighestVersionNo() {
            stubPlanExists();
            PlanVersion v1 = buildVersion(1, DATE_2026_JAN, DATE_2026_DEC);
            PlanVersion v2 = buildVersion(2, DATE_2027_JAN, null);
            // Port returns desc order: v2 first, then v1
            when(planVersionRepository.findByPlanIdOrderByVersionNoDesc(PLAN_ID))
                .thenReturn(List.of(v2, v1));

            PlanVersion result = service.getLatestVersion(PLAN_ID);

            assertThat(result.getVersionNo()).isEqualTo(2);
        }

        @Test
        @DisplayName("plan not found — throws PLAN_NOT_FOUND")
        void getLatest_planNotFound_throws() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getLatestVersion(PLAN_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_NOT_FOUND));
        }

        @Test
        @DisplayName("plan exists but no versions — throws PLAN_VERSION_NOT_FOUND")
        void getLatest_noVersions_throws() {
            stubPlanExists();
            when(planVersionRepository.findByPlanIdOrderByVersionNoDesc(PLAN_ID))
                .thenReturn(List.of());

            assertThatThrownBy(() -> service.getLatestVersion(PLAN_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_VERSION_NOT_FOUND));
        }
    }

    // ── listVersions ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("listVersions()")
    class ListVersions {

        private final PlanVersion v1Active   = buildVersion(1, DATE_2026_JAN, DATE_2026_DEC);
        private final PlanVersion v2Active   = buildVersion(2, DATE_2027_JAN, null);
        private final PlanVersion v3Inactive;

        {
            Instant now = Instant.now();
            v3Inactive = PlanVersion.builder()
                .id(UUID.randomUUID()).version(0L)
                .planId(PLAN_ID).versionNo(3)
                .effectiveFrom(LocalDate.of(2028, 1, 1)).effectiveTo(null)
                .active(false)
                .createdAt(now).updatedAt(now)
                .createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
                .build();
        }

        @Test
        @DisplayName("planId=null, active=null — returns all non-deleted versions via findAll")
        void list_all_returnsAll() {
            when(planVersionRepository.findAll()).thenReturn(List.of(v2Active, v1Active, v3Inactive));

            List<PlanVersion> result = service.listVersions(null, null);
            assertThat(result).hasSize(3);
        }

        @Test
        @DisplayName("planId set — delegates to findByPlanIdOrderByVersionNoDesc")
        void list_planFilter_delegatesToFindByPlanIdDesc() {
            when(planVersionRepository.findByPlanIdOrderByVersionNoDesc(PLAN_ID))
                .thenReturn(List.of(v2Active, v1Active));

            List<PlanVersion> result = service.listVersions(PLAN_ID, null);
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("active=true filter — returns only active versions")
        void list_activeFilter_returnsOnlyActive() {
            when(planVersionRepository.findAll())
                .thenReturn(List.of(v2Active, v1Active, v3Inactive));

            List<PlanVersion> result = service.listVersions(null, true);
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(PlanVersion::isActive);
        }

        @Test
        @DisplayName("active=false filter — returns only inactive versions")
        void list_inactiveFilter_returnsOnlyInactive() {
            when(planVersionRepository.findAll())
                .thenReturn(List.of(v2Active, v1Active, v3Inactive));

            List<PlanVersion> result = service.listVersions(null, false);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).isActive()).isFalse();
        }

        @Test
        @DisplayName("empty catalog — returns empty list")
        void list_empty_returnsEmptyList() {
            when(planVersionRepository.findAll()).thenReturn(List.of());

            assertThat(service.listVersions(null, null)).isEmpty();
        }
    }

    // ── getPlanVersionMeta ────────────────────────────────────────────────────

    @Nested
    @DisplayName("getPlanVersionMeta() — PPM-12A")
    class GetPlanVersionMeta {

        private Plan buildPlan(UUID id, String code, boolean active) {
            Instant now = Instant.now();
            return Plan.builder()
                .id(id).version(0L)
                .code(code).slug(code.toLowerCase()).name(code)
                .active(active)
                .createdAt(now).updatedAt(now)
                .createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
                .build();
        }

        @Test
        @DisplayName("returns meta joining version fields with parent plan code and active flag")
        void getMeta_found_returnsMeta() {
            PlanVersion version = buildVersion(VERSION_ID, 2, DATE_2026_JAN, DATE_2026_DEC);
            Plan plan = buildPlan(PLAN_ID, "PRO", true);

            when(planVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version));
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan));

            PlanVersionMetaResponse result = service.getPlanVersionMeta(VERSION_ID);

            assertThat(result.versionId()).isEqualTo(VERSION_ID);
            assertThat(result.planId()).isEqualTo(PLAN_ID);
            assertThat(result.planCode()).isEqualTo("PRO");
            assertThat(result.versionNo()).isEqualTo(2);
            assertThat(result.versionActive()).isTrue();
            assertThat(result.planActive()).isTrue();
            assertThat(result.effectiveFrom()).isEqualTo(DATE_2026_JAN);
            assertThat(result.effectiveTo()).isEqualTo(DATE_2026_DEC);
        }

        @Test
        @DisplayName("reflects inactive plan in planActive=false")
        void getMeta_inactivePlan_planActiveIsFalse() {
            PlanVersion version = buildVersion(VERSION_ID, 1, DATE_2026_JAN, null);
            Plan inactivePlan = buildPlan(PLAN_ID, "LEGACY", false);

            when(planVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version));
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(inactivePlan));

            PlanVersionMetaResponse result = service.getPlanVersionMeta(VERSION_ID);

            assertThat(result.planActive()).isFalse();
            assertThat(result.planCode()).isEqualTo("LEGACY");
        }

        @Test
        @DisplayName("version not found — throws ResourceNotFoundException")
        void getMeta_versionNotFound_throws() {
            when(planVersionRepository.findById(VERSION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getPlanVersionMeta(VERSION_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_VERSION_NOT_FOUND));

            verify(planRepository, never()).findById(any());
        }

        @Test
        @DisplayName("plan not found for version — throws ResourceNotFoundException")
        void getMeta_planNotFound_throws() {
            PlanVersion version = buildVersion(VERSION_ID, 1, DATE_2026_JAN, null);

            when(planVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version));
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getPlanVersionMeta(VERSION_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_NOT_FOUND));
        }
    }

    // ── getPlanVersionLimits ──────────────────────────────────────────────────

    @Nested
    @DisplayName("getPlanVersionLimits() — PPM-12B")
    class GetPlanVersionLimits {

        private PlanVersion buildVersionWithLimits(UUID id, Integer maxInternal, Integer maxClient,
                                                    Integer maxProjects, Long storageBytes,
                                                    Boolean customDomain, Boolean sso, Boolean priority) {
            Instant now = Instant.now();
            return PlanVersion.builder()
                .id(id).version(0L)
                .planId(PLAN_ID).versionNo(1)
                .effectiveFrom(DATE_2026_JAN).effectiveTo(null).active(true)
                .maxInternalUsers(maxInternal).maxClientUsers(maxClient)
                .maxActiveProjects(maxProjects).storageQuotaBytes(storageBytes)
                .customDomainEnabled(customDomain).ssoEnabled(sso).prioritySupport(priority)
                .createdAt(now).updatedAt(now)
                .createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
                .build();
        }

        @Test
        @DisplayName("returns all limit fields from the version")
        void getLimits_found_returnsAllFields() {
            PlanVersion version = buildVersionWithLimits(
                VERSION_ID, 50, 20, 10, 5_368_709_120L, true, false, true);

            when(planVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version));

            PlanVersionLimitsResponse result = service.getPlanVersionLimits(VERSION_ID);

            assertThat(result.planVersionId()).isEqualTo(VERSION_ID);
            assertThat(result.maxInternalUsers()).isEqualTo(50);
            assertThat(result.maxClientUsers()).isEqualTo(20);
            assertThat(result.maxActiveProjects()).isEqualTo(10);
            assertThat(result.storageQuotaBytes()).isEqualTo(5_368_709_120L);
            assertThat(result.customDomainEnabled()).isTrue();
            assertThat(result.ssoEnabled()).isFalse();
            assertThat(result.prioritySupport()).isTrue();
        }

        @Test
        @DisplayName("null limit fields are preserved (unlimited plan)")
        void getLimits_unlimitedPlan_nullFieldsPreserved() {
            PlanVersion version = buildVersionWithLimits(
                VERSION_ID, null, null, null, null, null, null, null);

            when(planVersionRepository.findById(VERSION_ID)).thenReturn(Optional.of(version));

            PlanVersionLimitsResponse result = service.getPlanVersionLimits(VERSION_ID);

            assertThat(result.maxInternalUsers()).isNull();
            assertThat(result.maxClientUsers()).isNull();
            assertThat(result.maxActiveProjects()).isNull();
            assertThat(result.storageQuotaBytes()).isNull();
            assertThat(result.customDomainEnabled()).isNull();
            assertThat(result.ssoEnabled()).isNull();
            assertThat(result.prioritySupport()).isNull();
        }

        @Test
        @DisplayName("version not found — throws ResourceNotFoundException")
        void getLimits_notFound_throws() {
            when(planVersionRepository.findById(VERSION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getPlanVersionLimits(VERSION_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_VERSION_NOT_FOUND));
        }
    }

    // ── deleteVersion ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteVersion()")
    class DeleteVersion {

        @Test
        @DisplayName("success — delegates to softDelete with actorId")
        void delete_success() {
            service.deleteVersion(ACTOR_ID, VERSION_ID);
            verify(planVersionRepository).softDelete(VERSION_ID, ACTOR_ID);
        }

        @Test
        @DisplayName("not found — ResourceNotFoundException propagates from port")
        void delete_notFound_throws() {
            Mockito.doThrow(new ResourceNotFoundException(
                    ErrorCode.PLAN_VERSION_NOT_FOUND, "not found"))
                .when(planVersionRepository).softDelete(VERSION_ID, ACTOR_ID);

            assertThatThrownBy(() -> service.deleteVersion(ACTOR_ID, VERSION_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_VERSION_NOT_FOUND));
        }
    }
}
