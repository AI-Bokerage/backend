package com.company.ppmsvc.unit.service;

import com.company.ppmsvc.module.usecase.ModuleApplicationServiceImpl;
import com.company.ppmsvc.module.model.ModuleCode;
import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.module.model.Module;
import com.company.ppmsvc.module.port.ModuleRepositoryPort;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ModuleApplicationService")
class ModuleApplicationServiceImplTest {

    static final UUID ACTOR_ID  = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID MODULE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock ModuleRepositoryPort moduleRepository;

    @InjectMocks
    ModuleApplicationServiceImpl service;

    // ── fixtures ──────────────────────────────────────────────────────────────

    private Module buildModule(ModuleCode code, String name, boolean active) {
        Instant now = Instant.now();
        return Module.builder()
            .id(MODULE_ID)
            .version(0L)
            .code(code)
            .name(name)
            .description("Desc — " + name)
            .active(active)
            .createdAt(now)
            .updatedAt(now)
            .createdBy(ACTOR_ID)
            .updatedBy(ACTOR_ID)
            .build();
    }

    // ── createModule ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createModule()")
    class CreateModule {

        @Test
        @DisplayName("success — saves module and returns it")
        void createModule_success() {
            Module saved = buildModule(ModuleCode.INVOICING, "Invoicing", true);
            when(moduleRepository.existsByCode(ModuleCode.INVOICING)).thenReturn(false);
            when(moduleRepository.save(any())).thenReturn(saved);

            Module result = service.createModule(ACTOR_ID, ModuleCode.INVOICING, "Invoicing",
                "Invoice management", true);

            assertThat(result.getCode()).isEqualTo(ModuleCode.INVOICING);
            assertThat(result.getName()).isEqualTo("Invoicing");
            verify(moduleRepository).save(any());
        }

        @Test
        @DisplayName("BR-1: duplicate code throws MODULE_CODE_ALREADY_EXISTS")
        void createModule_duplicateCode_throws() {
            when(moduleRepository.existsByCode(ModuleCode.INVOICING)).thenReturn(true);

            assertThatThrownBy(() -> service.createModule(ACTOR_ID, ModuleCode.INVOICING, "Invoicing",
                null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.MODULE_CODE_ALREADY_EXISTS));

            verify(moduleRepository, never()).save(any());
        }

        @Test
        @DisplayName("BR-2: active defaults to true when omitted")
        void createModule_activeOmitted_defaultsToTrue() {
            when(moduleRepository.existsByCode(any())).thenReturn(false);
            when(moduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<Module> captor = ArgumentCaptor.forClass(Module.class);
            service.createModule(ACTOR_ID, ModuleCode.REPORTING, "Reporting", null, null);

            verify(moduleRepository).save(captor.capture());
            assertThat(captor.getValue().isActive()).isTrue();
        }

        @Test
        @DisplayName("BR-3: createdBy and updatedBy are set from actorId")
        void createModule_auditFieldsSetFromActor() {
            when(moduleRepository.existsByCode(any())).thenReturn(false);
            when(moduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<Module> captor = ArgumentCaptor.forClass(Module.class);
            service.createModule(ACTOR_ID, ModuleCode.SSO, "SSO", null, true);

            verify(moduleRepository).save(captor.capture());
            assertThat(captor.getValue().getCreatedBy()).isEqualTo(ACTOR_ID);
            assertThat(captor.getValue().getUpdatedBy()).isEqualTo(ACTOR_ID);
        }

        @Test
        @DisplayName("B3: name is stored trimmed — leading/trailing whitespace removed")
        void createModule_nameTrimmed() {
            when(moduleRepository.existsByCode(any())).thenReturn(false);
            when(moduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<Module> captor = ArgumentCaptor.forClass(Module.class);
            service.createModule(ACTOR_ID, ModuleCode.MESSAGING, "  Messaging  ", null, true);

            verify(moduleRepository).save(captor.capture());
            assertThat(captor.getValue().getName()).isEqualTo("Messaging");
        }
    }

    // ── updateModule ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateModule()")
    class UpdateModule {

        @Test
        @DisplayName("success — updates supplied fields and returns module")
        void updateModule_success() {
            Module existing = buildModule(ModuleCode.MESSAGING, "Messaging", true);
            when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.of(existing));
            when(moduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Module result = service.updateModule(ACTOR_ID, MODULE_ID, "Messaging v2", null, null);

            assertThat(result.getName()).isEqualTo("Messaging v2");
        }

        @Test
        @DisplayName("BR-1: unknown module throws MODULE_NOT_FOUND")
        void updateModule_unknownModule_throws() {
            when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                service.updateModule(ACTOR_ID, MODULE_ID, "X", null, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.MODULE_NOT_FOUND));

            verify(moduleRepository, never()).save(any());
        }

        @Test
        @DisplayName("BR-3: blank name throws VALIDATION_ERROR")
        void updateModule_blankName_throws() {
            Module existing = buildModule(ModuleCode.MESSAGING, "Messaging", true);
            when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() ->
                service.updateModule(ACTOR_ID, MODULE_ID, "   ", null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR));
        }

        @Test
        @DisplayName("BR-4: code is never changed regardless of how module is updated")
        void updateModule_codePreserved() {
            Module existing = buildModule(ModuleCode.MESSAGING, "Messaging", true);
            when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.of(existing));
            when(moduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<Module> captor = ArgumentCaptor.forClass(Module.class);
            service.updateModule(ACTOR_ID, MODULE_ID, "New Name", null, null);

            verify(moduleRepository).save(captor.capture());
            assertThat(captor.getValue().getCode()).isEqualTo(ModuleCode.MESSAGING);
        }

        @Test
        @DisplayName("BR-2: null name preserves existing name")
        void updateModule_nullName_preservesExisting() {
            Module existing = buildModule(ModuleCode.MESSAGING, "Messaging", true);
            when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.of(existing));
            when(moduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<Module> captor = ArgumentCaptor.forClass(Module.class);
            service.updateModule(ACTOR_ID, MODULE_ID, null, null, false);

            verify(moduleRepository).save(captor.capture());
            assertThat(captor.getValue().getName()).isEqualTo("Messaging");
            assertThat(captor.getValue().isActive()).isFalse();
        }

        @Test
        @DisplayName("C6: createdAt and createdBy are preserved on update")
        void updateModule_createdFieldsPreserved() {
            Instant originalCreatedAt = Instant.now().minusSeconds(3600);
            Module existing = Module.builder()
                .id(MODULE_ID).version(0L)
                .code(ModuleCode.API_ACCESS).name("API Access")
                .active(true)
                .createdAt(originalCreatedAt).updatedAt(originalCreatedAt)
                .createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
                .build();

            when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.of(existing));
            when(moduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<Module> captor = ArgumentCaptor.forClass(Module.class);
            service.updateModule(ACTOR_ID, MODULE_ID, "API Access v2", null, null);

            verify(moduleRepository).save(captor.capture());
            assertThat(captor.getValue().getCreatedAt()).isEqualTo(originalCreatedAt);
            assertThat(captor.getValue().getCreatedBy()).isEqualTo(ACTOR_ID);
        }

        @Test
        @DisplayName("BR-5: updatedAt and updatedBy are refreshed")
        void updateModule_auditFieldsRefreshed() {
            Instant originalUpdatedAt = Instant.now().minusSeconds(60);
            Module existing = Module.builder()
                .id(MODULE_ID).version(0L)
                .code(ModuleCode.API_ACCESS).name("API Access")
                .active(true)
                .createdAt(originalUpdatedAt).updatedAt(originalUpdatedAt)
                .createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
                .build();

            when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.of(existing));
            when(moduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<Module> captor = ArgumentCaptor.forClass(Module.class);
            service.updateModule(ACTOR_ID, MODULE_ID, "API Access v2", null, null);

            verify(moduleRepository).save(captor.capture());
            assertThat(captor.getValue().getUpdatedAt()).isAfter(originalUpdatedAt);
            assertThat(captor.getValue().getUpdatedBy()).isEqualTo(ACTOR_ID);
        }

        @Test
        @DisplayName("B3: name is stored trimmed on update")
        void updateModule_nameTrimmed() {
            Module existing = buildModule(ModuleCode.DOCUMENT_MANAGEMENT, "Document Management", true);
            when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.of(existing));
            when(moduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<Module> captor = ArgumentCaptor.forClass(Module.class);
            service.updateModule(ACTOR_ID, MODULE_ID, "  Docs  ", null, null);

            verify(moduleRepository).save(captor.capture());
            assertThat(captor.getValue().getName()).isEqualTo("Docs");
        }
    }

    // ── getModule ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getModule()")
    class GetModule {

        @Test
        @DisplayName("success — returns domain module")
        void getModule_success() {
            Module module = buildModule(ModuleCode.TIME_TRACKING, "Time Tracking", true);
            when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.of(module));

            Module result = service.getModule(MODULE_ID);

            assertThat(result.getCode()).isEqualTo(ModuleCode.TIME_TRACKING);
        }

        @Test
        @DisplayName("unknown module throws MODULE_NOT_FOUND")
        void getModule_unknown_throws() {
            when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getModule(MODULE_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.MODULE_NOT_FOUND));
        }

        @Test
        @DisplayName("C5: soft-deleted module — repository returns empty → service throws MODULE_NOT_FOUND")
        void getModule_softDeleted_throwsNotFound() {
            // @SQLRestriction on ModuleEntity makes findById return empty for soft-deleted rows;
            // the service propagates that as ResourceNotFoundException
            when(moduleRepository.findById(MODULE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getModule(MODULE_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.MODULE_NOT_FOUND));
        }
    }

    // ── listModules ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("listModules()")
    class ListModules {

        @Test
        @DisplayName("no criteria — returns all non-deleted modules ordered by code")
        void listModules_noCriteria_returnsAll() {
            List<Module> modules = List.of(
                buildModule(ModuleCode.API_ACCESS,  "API Access",  true),
                buildModule(ModuleCode.INVOICING,   "Invoicing",   true),
                buildModule(ModuleCode.MESSAGING,   "Messaging",   false));

            when(moduleRepository.findAll()).thenReturn(modules);

            assertThat(service.listModules(null)).hasSize(3);
        }

        @Test
        @DisplayName("active=true — filters inactive modules in-memory")
        void listModules_activeOnly_filtersInactive() {
            List<Module> modules = List.of(
                buildModule(ModuleCode.API_ACCESS, "API Access", true),
                buildModule(ModuleCode.INVOICING,  "Invoicing",  false),  // inactive
                buildModule(ModuleCode.MESSAGING,  "Messaging",  true));

            when(moduleRepository.findAll()).thenReturn(modules);

            List<Module> result = service.listModules(true);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(Module::isActive).containsOnly(true);
        }

        @Test
        @DisplayName("active=false — returns only inactive modules")
        void listModules_inactiveOnly_returnsOnlyInactive() {
            List<Module> modules = List.of(
                buildModule(ModuleCode.API_ACCESS, "API Access", true),
                buildModule(ModuleCode.INVOICING,  "Invoicing",  false));

            when(moduleRepository.findAll()).thenReturn(modules);

            List<Module> result = service.listModules(false);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCode()).isEqualTo(ModuleCode.INVOICING);
        }

        @Test
        @DisplayName("empty catalog returns empty list")
        void listModules_empty_returnsEmptyList() {
            when(moduleRepository.findAll()).thenReturn(List.of());

            assertThat(service.listModules(null)).isEmpty();
        }
    }

    // ── getModuleByCode ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("getModuleByCode()")
    class GetModuleByCode {

        @Test
        @DisplayName("success — returns domain module for known code")
        void getModuleByCode_success() {
            Module module = buildModule(ModuleCode.REPORTING, "Reporting", true);
            when(moduleRepository.findByCode(ModuleCode.REPORTING)).thenReturn(Optional.of(module));

            Module result = service.getModuleByCode(ModuleCode.REPORTING);

            assertThat(result.getCode()).isEqualTo(ModuleCode.REPORTING);
        }

        @Test
        @DisplayName("unknown code — repository returns empty → throws MODULE_NOT_FOUND")
        void getModuleByCode_notFound_throws() {
            when(moduleRepository.findByCode(ModuleCode.INVOICING)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getModuleByCode(ModuleCode.INVOICING))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.MODULE_NOT_FOUND));
        }

        @Test
        @DisplayName("soft-deleted module — repository returns empty → throws MODULE_NOT_FOUND")
        void getModuleByCode_softDeleted_throws() {
            when(moduleRepository.findByCode(ModuleCode.SSO)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getModuleByCode(ModuleCode.SSO))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.MODULE_NOT_FOUND));
        }
    }

    // ── deleteModule ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteModule()")
    class DeleteModule {

        @Test
        @DisplayName("success — delegates to repository.softDelete with given actor")
        void deleteModule_success_delegatesToRepository() {
            doNothing().when(moduleRepository).softDelete(eq(MODULE_ID), eq(ACTOR_ID));

            service.deleteModule(ACTOR_ID, MODULE_ID);

            verify(moduleRepository).softDelete(MODULE_ID, ACTOR_ID);
        }

        @Test
        @DisplayName("unknown module — repository throws ResourceNotFoundException propagated to caller")
        void deleteModule_unknownModule_throwsNotFound() {
            doThrow(new ResourceNotFoundException(
                ErrorCode.MODULE_NOT_FOUND, "Module not found: " + MODULE_ID))
                .when(moduleRepository).softDelete(eq(MODULE_ID), any());

            assertThatThrownBy(() -> service.deleteModule(ACTOR_ID, MODULE_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.MODULE_NOT_FOUND));
        }
    }
}
