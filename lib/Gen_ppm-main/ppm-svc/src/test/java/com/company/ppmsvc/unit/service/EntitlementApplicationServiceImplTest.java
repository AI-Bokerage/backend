package com.company.ppmsvc.unit.service;

import com.company.ppmsvc.entitlement.usecase.EntitlementApplicationServiceImpl;
import com.company.ppmsvc.entitlement.model.EntitlementType;
import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.entitlement.model.Entitlement;
import com.company.ppmsvc.entitlement.port.EntitlementRepositoryPort;
import java.time.Instant;
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
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EntitlementApplicationServiceImpl")
class EntitlementApplicationServiceImplTest {

    static final UUID ACTOR_ID        = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID ENTITLEMENT_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock EntitlementRepositoryPort entitlementRepository;

    @InjectMocks EntitlementApplicationServiceImpl service;

    // ── fixtures ──────────────────────────────────────────────────────────────

    private Entitlement buildEntitlement(String code, EntitlementType type, boolean active) {
        Instant now = Instant.now();
        return Entitlement.builder()
            .id(ENTITLEMENT_ID).version(0L)
            .code(code).name("Name of " + code)
            .description("Desc").type(type).active(active)
            .createdAt(now).updatedAt(now)
            .createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
            .build();
    }

    // ── createEntitlement ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("createEntitlement()")
    class CreateEntitlement {

        @Test
        @DisplayName("success — saves entitlement and returns it")
        void create_success() {
            Entitlement saved = buildEntitlement("max_users", EntitlementType.QUOTA, true);

            when(entitlementRepository.existsByCode("max_users")).thenReturn(false);
            when(entitlementRepository.save(any())).thenReturn(saved);

            Entitlement result = service.createEntitlement(ACTOR_ID, "max_users", "Max Users", "Quota",
                EntitlementType.QUOTA, true);

            assertThat(result.getCode()).isEqualTo("max_users");
            verify(entitlementRepository).save(any());
        }

        @Test
        @DisplayName("BR-E1 — duplicate code throws ENTITLEMENT_CODE_ALREADY_EXISTS")
        void create_duplicateCode_throws() {
            when(entitlementRepository.existsByCode("max_users")).thenReturn(true);

            assertThatThrownBy(() -> service.createEntitlement(ACTOR_ID, "max_users", "Max Users", null,
                    EntitlementType.QUOTA, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.ENTITLEMENT_CODE_ALREADY_EXISTS));

            verify(entitlementRepository, never()).save(any());
        }

        @Test
        @DisplayName("BR-E5 — active defaults to true when omitted")
        void create_activeOmitted_defaultsToTrue() {
            when(entitlementRepository.existsByCode(any())).thenReturn(false);
            when(entitlementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<Entitlement> captor = ArgumentCaptor.forClass(Entitlement.class);
            service.createEntitlement(ACTOR_ID, "reporting", "Reporting", null, EntitlementType.BOOLEAN, null);

            verify(entitlementRepository).save(captor.capture());
            assertThat(captor.getValue().isActive()).isTrue();
        }

        @Test
        @DisplayName("BR-E3 — code is trimmed before persistence")
        void create_codeTrimmed() {
            when(entitlementRepository.existsByCode("storage_gb")).thenReturn(false);
            when(entitlementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<Entitlement> captor = ArgumentCaptor.forClass(Entitlement.class);
            service.createEntitlement(ACTOR_ID, "  storage_gb  ", "Storage GB", null, EntitlementType.QUOTA, true);

            verify(entitlementRepository).save(captor.capture());
            assertThat(captor.getValue().getCode()).isEqualTo("storage_gb");
        }

        @Test
        @DisplayName("BR-E4 — name is trimmed before persistence")
        void create_nameTrimmed() {
            when(entitlementRepository.existsByCode(any())).thenReturn(false);
            when(entitlementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<Entitlement> captor = ArgumentCaptor.forClass(Entitlement.class);
            service.createEntitlement(ACTOR_ID, "api_rpm", "  API RPM  ", null, EntitlementType.RATE_LIMIT, true);

            verify(entitlementRepository).save(captor.capture());
            assertThat(captor.getValue().getName()).isEqualTo("API RPM");
        }

        @Test
        @DisplayName("BR-E7 — createdBy and updatedBy are set from given actor")
        void create_auditFieldsSetFromActor() {
            when(entitlementRepository.existsByCode(any())).thenReturn(false);
            when(entitlementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<Entitlement> captor = ArgumentCaptor.forClass(Entitlement.class);
            service.createEntitlement(ACTOR_ID, "sso_enabled", "SSO", null, EntitlementType.BOOLEAN, true);

            verify(entitlementRepository).save(captor.capture());
            assertThat(captor.getValue().getCreatedBy()).isEqualTo(ACTOR_ID);
            assertThat(captor.getValue().getUpdatedBy()).isEqualTo(ACTOR_ID);
        }
    }

    // ── updateEntitlement ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateEntitlement()")
    class UpdateEntitlement {

        @Test
        @DisplayName("success — updates and returns entitlement")
        void update_success() {
            Entitlement existing = buildEntitlement("max_users", EntitlementType.QUOTA, true);
            Entitlement updated  = buildEntitlement("max_users", EntitlementType.QUOTA, false);

            when(entitlementRepository.findById(ENTITLEMENT_ID)).thenReturn(Optional.of(existing));
            when(entitlementRepository.save(any())).thenReturn(updated);

            Entitlement result = service.updateEntitlement(ACTOR_ID, ENTITLEMENT_ID, null, null, false);

            assertThat(result.isActive()).isFalse();
            verify(entitlementRepository).save(any());
        }

        @Test
        @DisplayName("not found — throws ENTITLEMENT_NOT_FOUND")
        void update_notFound_throws() {
            when(entitlementRepository.findById(ENTITLEMENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateEntitlement(ACTOR_ID, ENTITLEMENT_ID, "New Name", null, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.ENTITLEMENT_NOT_FOUND));
        }

        @Test
        @DisplayName("BR-E2 — code is never changed by an update")
        void update_codeIsImmutable() {
            Entitlement existing = buildEntitlement("original_code", EntitlementType.QUOTA, true);

            when(entitlementRepository.findById(ENTITLEMENT_ID)).thenReturn(Optional.of(existing));
            when(entitlementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<Entitlement> captor = ArgumentCaptor.forClass(Entitlement.class);
            service.updateEntitlement(ACTOR_ID, ENTITLEMENT_ID, "New Name", null, null);

            verify(entitlementRepository).save(captor.capture());
            assertThat(captor.getValue().getCode()).isEqualTo("original_code");
        }

        @Test
        @DisplayName("BR-E7 — createdBy is preserved; updatedBy is refreshed")
        void update_auditFieldsPreservedAndRefreshed() {
            UUID originalCreator = UUID.fromString("00000000-0000-0000-0000-000000000099");
            Entitlement existing = Entitlement.builder()
                .id(ENTITLEMENT_ID).version(0L)
                .code("x").name("X").type(EntitlementType.BOOLEAN).active(true)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .createdBy(originalCreator).updatedBy(originalCreator)
                .build();

            when(entitlementRepository.findById(ENTITLEMENT_ID)).thenReturn(Optional.of(existing));
            when(entitlementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<Entitlement> captor = ArgumentCaptor.forClass(Entitlement.class);
            service.updateEntitlement(ACTOR_ID, ENTITLEMENT_ID, "X2", null, null);

            verify(entitlementRepository).save(captor.capture());
            assertThat(captor.getValue().getCreatedBy()).isEqualTo(originalCreator); // preserved
            assertThat(captor.getValue().getUpdatedBy()).isEqualTo(ACTOR_ID);         // refreshed
        }
    }

    // ── getEntitlement ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getEntitlement()")
    class GetEntitlement {

        @Test
        @DisplayName("success — returns entitlement when it exists")
        void get_success() {
            Entitlement e = buildEntitlement("max_users", EntitlementType.QUOTA, true);

            when(entitlementRepository.findById(ENTITLEMENT_ID)).thenReturn(Optional.of(e));

            Entitlement result = service.getEntitlement(ENTITLEMENT_ID);

            assertThat(result.getId()).isEqualTo(ENTITLEMENT_ID);
        }

        @Test
        @DisplayName("not found — throws ENTITLEMENT_NOT_FOUND")
        void get_notFound_throws() {
            when(entitlementRepository.findById(ENTITLEMENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getEntitlement(ENTITLEMENT_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.ENTITLEMENT_NOT_FOUND));
        }
    }

    // ── getEntitlementByCode ──────────────────────────────────────────────────

    @Nested
    @DisplayName("getEntitlementByCode()")
    class GetEntitlementByCode {

        @Test
        @DisplayName("success — returns entitlement when code exists")
        void getByCode_success() {
            Entitlement e = buildEntitlement("reporting_enabled", EntitlementType.BOOLEAN, true);

            when(entitlementRepository.findByCode("reporting_enabled")).thenReturn(Optional.of(e));

            Entitlement result = service.getEntitlementByCode("reporting_enabled");

            assertThat(result.getCode()).isEqualTo("reporting_enabled");
        }

        @Test
        @DisplayName("not found — throws ENTITLEMENT_NOT_FOUND")
        void getByCode_notFound_throws() {
            when(entitlementRepository.findByCode("unknown")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getEntitlementByCode("unknown"))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.ENTITLEMENT_NOT_FOUND));
        }
    }

    // ── listEntitlements ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("listEntitlements()")
    class ListEntitlements {

        @Test
        @DisplayName("no filters — returns all non-deleted entitlements")
        void list_noFilters_returnsList() {
            Entitlement e1 = buildEntitlement("a", EntitlementType.BOOLEAN, true);
            Entitlement e2 = buildEntitlement("b", EntitlementType.QUOTA,   true);

            when(entitlementRepository.findAll()).thenReturn(List.of(e1, e2));

            List<Entitlement> result = service.listEntitlements(null, null);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("active=false — filters out active entitlements")
        void list_activeFilter_returnsOnlyInactive() {
            Entitlement active   = buildEntitlement("a", EntitlementType.BOOLEAN, true);
            Entitlement inactive = buildEntitlement("b", EntitlementType.QUOTA,   false);

            when(entitlementRepository.findAll()).thenReturn(List.of(active, inactive));

            List<Entitlement> result = service.listEntitlements(false, null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCode()).isEqualTo("b");
        }

        @Test
        @DisplayName("type=QUOTA — filters by entitlement type")
        void list_typeFilter_returnsOnlyMatchingType() {
            Entitlement bool  = buildEntitlement("flag",  EntitlementType.BOOLEAN,    true);
            Entitlement quota = buildEntitlement("seats", EntitlementType.QUOTA,      true);
            Entitlement rate  = buildEntitlement("rpm",   EntitlementType.RATE_LIMIT, true);

            when(entitlementRepository.findAll()).thenReturn(List.of(bool, quota, rate));

            List<Entitlement> result = service.listEntitlements(null, EntitlementType.QUOTA);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCode()).isEqualTo("seats");
        }
    }

    // ── deleteEntitlement ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteEntitlement()")
    class DeleteEntitlement {

        @Test
        @DisplayName("success — delegates to softDelete")
        void delete_success() {
            service.deleteEntitlement(ACTOR_ID, ENTITLEMENT_ID);

            verify(entitlementRepository).softDelete(ENTITLEMENT_ID, ACTOR_ID);
        }

        @Test
        @DisplayName("not found — ResourceNotFoundException propagates from port")
        void delete_notFound_throws() {
            org.mockito.Mockito.doThrow(new ResourceNotFoundException(
                    ErrorCode.ENTITLEMENT_NOT_FOUND, "not found"))
                .when(entitlementRepository).softDelete(ENTITLEMENT_ID, ACTOR_ID);

            assertThatThrownBy(() -> service.deleteEntitlement(ACTOR_ID, ENTITLEMENT_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.ENTITLEMENT_NOT_FOUND));
        }
    }
}
