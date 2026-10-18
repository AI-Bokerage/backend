package com.company.ppmsvc.unit.service;

import com.company.ppmsvc.addon.usecase.AddOnApplicationServiceImpl;
import com.company.ppmsvc.addon.model.AddOnType;
import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.addon.model.AddOn;
import com.company.ppmsvc.addon.port.AddOnRepositoryPort;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AddOnApplicationService")
class AddOnApplicationServiceImplTest {

    static final UUID ACTOR_ID  = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID ADD_ON_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock AddOnRepositoryPort addOnRepository;

    @InjectMocks
    AddOnApplicationServiceImpl service;

    // ── fixtures ──────────────────────────────────────────────────────────────

    private AddOn buildAddOn(String code, AddOnType type, boolean active) {
        Instant now = Instant.now();
        return AddOn.builder()
            .id(ADD_ON_ID)
            .version(0L)
            .code(code)
            .name("Add-On " + code)
            .description("Desc — " + code)
            .type(type)
            .active(active)
            .createdAt(now)
            .updatedAt(now)
            .createdBy(ACTOR_ID)
            .updatedBy(ACTOR_ID)
            .build();
    }

    // ── createAddOn ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createAddOn")
    class CreateAddOn {

        @Test
        @DisplayName("BR-A1+A3+A4: success — saves with normalised code, trimmed name, active=true default")
        void success() {
            AddOn saved = buildAddOn("EXTRA_USERS_10", AddOnType.QUOTA, true);
            when(addOnRepository.existsByCode("EXTRA_USERS_10")).thenReturn(false);
            when(addOnRepository.save(any())).thenReturn(saved);

            service.createAddOn(ACTOR_ID, " extra_users_10 ", "  Extra Users  ", "10 extra seats",
                AddOnType.QUOTA, null);

            ArgumentCaptor<AddOn> cap = ArgumentCaptor.forClass(AddOn.class);
            verify(addOnRepository).save(cap.capture());
            AddOn persisted = cap.getValue();
            assertThat(persisted.getCode()).isEqualTo("EXTRA_USERS_10");
            assertThat(persisted.getName()).isEqualTo("Extra Users");
            assertThat(persisted.isActive()).isTrue();
        }

        @Test
        @DisplayName("BR-A1: duplicate code → ADD_ON_CODE_ALREADY_EXISTS (409)")
        void duplicateCode_throwsBusinessException() {
            when(addOnRepository.existsByCode("EXISTING")).thenReturn(true);

            assertThatThrownBy(() -> service.createAddOn(ACTOR_ID, "EXISTING", "Name", null,
                AddOnType.FEATURE, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ADD_ON_CODE_ALREADY_EXISTS));

            verify(addOnRepository, never()).save(any());
        }

        @Test
        @DisplayName("BR-A2: code is stripped and uppercased before uniqueness check")
        void codeNormalised() {
            AddOn saved = buildAddOn("MY_CODE", AddOnType.SERVICE, true);
            when(addOnRepository.existsByCode("MY_CODE")).thenReturn(false);
            when(addOnRepository.save(any())).thenReturn(saved);

            service.createAddOn(ACTOR_ID, " my_code ", "Name", null, AddOnType.SERVICE, true);

            verify(addOnRepository).existsByCode("MY_CODE");
        }

        @Test
        @DisplayName("BR-A3: name is stripped of leading/trailing whitespace")
        void nameTrimmed() {
            AddOn saved = buildAddOn("CODE", AddOnType.FEATURE, true);
            when(addOnRepository.existsByCode("CODE")).thenReturn(false);
            when(addOnRepository.save(any())).thenReturn(saved);

            service.createAddOn(ACTOR_ID, "CODE", "  Trimmed Name  ", null, AddOnType.FEATURE, true);

            ArgumentCaptor<AddOn> cap = ArgumentCaptor.forClass(AddOn.class);
            verify(addOnRepository).save(cap.capture());
            assertThat(cap.getValue().getName()).isEqualTo("Trimmed Name");
        }

        @Test
        @DisplayName("BR-A4: active defaults to true when argument is null")
        void activeDefaultsToTrue() {
            AddOn saved = buildAddOn("CODE", AddOnType.FEATURE, true);
            when(addOnRepository.existsByCode("CODE")).thenReturn(false);
            when(addOnRepository.save(any())).thenReturn(saved);

            service.createAddOn(ACTOR_ID, "CODE", "Name", null, AddOnType.FEATURE, null);

            ArgumentCaptor<AddOn> cap = ArgumentCaptor.forClass(AddOn.class);
            verify(addOnRepository).save(cap.capture());
            assertThat(cap.getValue().isActive()).isTrue();
        }

        @Test
        @DisplayName("BR-A4: explicit active=false is honoured")
        void explicitActiveFalseHonoured() {
            AddOn saved = buildAddOn("CODE", AddOnType.FEATURE, false);
            when(addOnRepository.existsByCode("CODE")).thenReturn(false);
            when(addOnRepository.save(any())).thenReturn(saved);

            service.createAddOn(ACTOR_ID, "CODE", "Name", null, AddOnType.FEATURE, false);

            ArgumentCaptor<AddOn> cap = ArgumentCaptor.forClass(AddOn.class);
            verify(addOnRepository).save(cap.capture());
            assertThat(cap.getValue().isActive()).isFalse();
        }

        @Test
        @DisplayName("audit fields — createdBy and updatedBy are set to given actor")
        void auditFieldsSetFromActor() {
            AddOn saved = buildAddOn("CODE", AddOnType.SERVICE, true);
            when(addOnRepository.existsByCode("CODE")).thenReturn(false);
            when(addOnRepository.save(any())).thenReturn(saved);

            service.createAddOn(ACTOR_ID, "CODE", "Name", null, AddOnType.SERVICE, null);

            ArgumentCaptor<AddOn> cap = ArgumentCaptor.forClass(AddOn.class);
            verify(addOnRepository).save(cap.capture());
            assertThat(cap.getValue().getCreatedBy()).isEqualTo(ACTOR_ID);
            assertThat(cap.getValue().getUpdatedBy()).isEqualTo(ACTOR_ID);
        }
    }

    // ── updateAddOn ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateAddOn")
    class UpdateAddOn {

        @Test
        @DisplayName("success — saves and returns updated state")
        void success() {
            AddOn existing = buildAddOn("CODE", AddOnType.QUOTA, true);
            when(addOnRepository.findById(ADD_ON_ID)).thenReturn(Optional.of(existing));
            AddOn saved = buildAddOn("CODE", AddOnType.QUOTA, false);
            when(addOnRepository.save(any())).thenReturn(saved);

            service.updateAddOn(ACTOR_ID, ADD_ON_ID, "New Name", null, false);

            verify(addOnRepository).save(any());
        }

        @Test
        @DisplayName("add-on not found → ADD_ON_NOT_FOUND (ResourceNotFoundException)")
        void notFound_throwsResourceNotFoundException() {
            when(addOnRepository.findById(ADD_ON_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateAddOn(ACTOR_ID, ADD_ON_ID, null, null, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(e -> assertThat(((ResourceNotFoundException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ADD_ON_NOT_FOUND));
        }

        @Test
        @DisplayName("BR-A5: code is preserved — cannot be changed via update")
        void codeIsImmutable() {
            AddOn existing = buildAddOn("ORIGINAL", AddOnType.QUOTA, true);
            when(addOnRepository.findById(ADD_ON_ID)).thenReturn(Optional.of(existing));
            when(addOnRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.updateAddOn(ACTOR_ID, ADD_ON_ID, "New Name", null, null);

            ArgumentCaptor<AddOn> cap = ArgumentCaptor.forClass(AddOn.class);
            verify(addOnRepository).save(cap.capture());
            assertThat(cap.getValue().getCode()).isEqualTo("ORIGINAL");
        }

        @Test
        @DisplayName("BR-A6: type is preserved — cannot be changed via update")
        void typeIsImmutable() {
            AddOn existing = buildAddOn("CODE", AddOnType.QUOTA, true);
            when(addOnRepository.findById(ADD_ON_ID)).thenReturn(Optional.of(existing));
            when(addOnRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.updateAddOn(ACTOR_ID, ADD_ON_ID, "New Name", null, null);

            ArgumentCaptor<AddOn> cap = ArgumentCaptor.forClass(AddOn.class);
            verify(addOnRepository).save(cap.capture());
            assertThat(cap.getValue().getType()).isEqualTo(AddOnType.QUOTA);
        }

        @Test
        @DisplayName("null name — existing name is preserved")
        void nullNamePreservesExisting() {
            AddOn existing = buildAddOn("CODE", AddOnType.FEATURE, true);
            when(addOnRepository.findById(ADD_ON_ID)).thenReturn(Optional.of(existing));
            when(addOnRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.updateAddOn(ACTOR_ID, ADD_ON_ID, null, null, null);

            ArgumentCaptor<AddOn> cap = ArgumentCaptor.forClass(AddOn.class);
            verify(addOnRepository).save(cap.capture());
            assertThat(cap.getValue().getName()).isEqualTo(existing.getName());
        }

        @Test
        @DisplayName("null description — existing description is preserved")
        void nullDescriptionPreservesExisting() {
            AddOn existing = buildAddOn("CODE", AddOnType.FEATURE, true);
            when(addOnRepository.findById(ADD_ON_ID)).thenReturn(Optional.of(existing));
            when(addOnRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.updateAddOn(ACTOR_ID, ADD_ON_ID, null, null, null);

            ArgumentCaptor<AddOn> cap = ArgumentCaptor.forClass(AddOn.class);
            verify(addOnRepository).save(cap.capture());
            assertThat(cap.getValue().getDescription()).isEqualTo(existing.getDescription());
        }

        @Test
        @DisplayName("null active — existing active flag is preserved")
        void nullActivePreservesExisting() {
            AddOn existing = buildAddOn("CODE", AddOnType.SERVICE, false);
            when(addOnRepository.findById(ADD_ON_ID)).thenReturn(Optional.of(existing));
            when(addOnRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.updateAddOn(ACTOR_ID, ADD_ON_ID, null, null, null);

            ArgumentCaptor<AddOn> cap = ArgumentCaptor.forClass(AddOn.class);
            verify(addOnRepository).save(cap.capture());
            assertThat(cap.getValue().isActive()).isFalse();
        }

        @Test
        @DisplayName("BR-A7: updatedBy is refreshed to given actor on update")
        void auditRefreshOnUpdate() {
            UUID otherActor = UUID.fromString("00000000-0000-0000-0000-000000000099");
            Instant past = Instant.now().minusSeconds(3600);
            AddOn existing = AddOn.builder()
                .id(ADD_ON_ID).version(1L).code("CODE").name("Name")
                .type(AddOnType.QUOTA).active(true)
                .createdAt(past).updatedAt(past).createdBy(otherActor).updatedBy(otherActor)
                .build();
            when(addOnRepository.findById(ADD_ON_ID)).thenReturn(Optional.of(existing));
            when(addOnRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.updateAddOn(ACTOR_ID, ADD_ON_ID, "Updated", null, null);

            ArgumentCaptor<AddOn> cap = ArgumentCaptor.forClass(AddOn.class);
            verify(addOnRepository).save(cap.capture());
            assertThat(cap.getValue().getUpdatedBy()).isEqualTo(ACTOR_ID);
            assertThat(cap.getValue().getCreatedBy()).isEqualTo(otherActor);  // preserved
        }
    }

    // ── getAddOn ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAddOn")
    class GetAddOn {

        @Test
        @DisplayName("success — delegates to repository and returns domain AddOn")
        void success() {
            AddOn addOn = buildAddOn("CODE", AddOnType.FEATURE, true);
            when(addOnRepository.findById(ADD_ON_ID)).thenReturn(Optional.of(addOn));

            AddOn result = service.getAddOn(ADD_ON_ID);

            assertThat(result).isEqualTo(addOn);
        }

        @Test
        @DisplayName("not found → ADD_ON_NOT_FOUND (ResourceNotFoundException)")
        void notFound_throwsResourceNotFoundException() {
            when(addOnRepository.findById(ADD_ON_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getAddOn(ADD_ON_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(e -> assertThat(((ResourceNotFoundException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ADD_ON_NOT_FOUND));
        }
    }

    // ── getAddOnByCode ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAddOnByCode")
    class GetAddOnByCode {

        @Test
        @DisplayName("success — delegates to repository and returns domain AddOn")
        void success() {
            AddOn addOn = buildAddOn("MY_CODE", AddOnType.QUOTA, true);
            when(addOnRepository.findByCode("MY_CODE")).thenReturn(Optional.of(addOn));

            AddOn result = service.getAddOnByCode("MY_CODE");

            assertThat(result).isEqualTo(addOn);
        }

        @Test
        @DisplayName("not found → ADD_ON_NOT_FOUND (ResourceNotFoundException)")
        void notFound_throwsResourceNotFoundException() {
            when(addOnRepository.findByCode("MISSING")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getAddOnByCode("MISSING"))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(e -> assertThat(((ResourceNotFoundException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ADD_ON_NOT_FOUND));
        }

        @Test
        @DisplayName("soft-deleted add-on invisible — repository returns empty → NOT_FOUND")
        void softDeletedIsInvisible() {
            // @SQLRestriction("deleted_at IS NULL") means the JPA repo returns empty for deleted rows;
            // the service must propagate this as ADD_ON_NOT_FOUND, not a null pointer.
            when(addOnRepository.findByCode("DELETED_CODE")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getAddOnByCode("DELETED_CODE"))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── listAddOns ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("listAddOns")
    class ListAddOns {

        @Test
        @DisplayName("no filters — returns every add-on from repository")
        void noFilters_returnsAll() {
            AddOn a = buildAddOn("A", AddOnType.FEATURE, true);
            AddOn b = buildAddOn("B", AddOnType.QUOTA, false);
            when(addOnRepository.findAll()).thenReturn(List.of(a, b));

            List<AddOn> result = service.listAddOns(null, null);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("active=true filter — returns only active add-ons")
        void activeFilter_returnsOnlyActive() {
            AddOn active = buildAddOn("ACTIVE", AddOnType.FEATURE, true);
            AddOn inactive = buildAddOn("INACTIVE", AddOnType.FEATURE, false);
            when(addOnRepository.findAll()).thenReturn(List.of(active, inactive));

            List<AddOn> result = service.listAddOns(true, null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).isActive()).isTrue();
        }

        @Test
        @DisplayName("active=false filter — returns only inactive add-ons")
        void inactiveFilter_returnsOnlyInactive() {
            AddOn active = buildAddOn("ACTIVE", AddOnType.FEATURE, true);
            AddOn inactive = buildAddOn("INACTIVE", AddOnType.QUOTA, false);
            when(addOnRepository.findAll()).thenReturn(List.of(active, inactive));

            List<AddOn> result = service.listAddOns(false, null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).isActive()).isFalse();
        }

        @Test
        @DisplayName("type filter — returns only add-ons of matching type")
        void typeFilter_returnsMatchingType() {
            AddOn feature = buildAddOn("FEAT", AddOnType.FEATURE, true);
            AddOn quota = buildAddOn("QUOT", AddOnType.QUOTA, true);
            when(addOnRepository.findAll()).thenReturn(List.of(feature, quota));

            List<AddOn> result = service.listAddOns(null, AddOnType.FEATURE);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getType()).isEqualTo(AddOnType.FEATURE);
        }

        @Test
        @DisplayName("combined active+type filter — applies both dimensions")
        void combinedFilter_appliesBothDimensions() {
            AddOn activeFeature  = buildAddOn("AF", AddOnType.FEATURE, true);
            AddOn inactiveFeature = buildAddOn("IF", AddOnType.FEATURE, false);
            AddOn activeQuota    = buildAddOn("AQ", AddOnType.QUOTA, true);
            when(addOnRepository.findAll()).thenReturn(List.of(activeFeature, inactiveFeature, activeQuota));

            List<AddOn> result = service.listAddOns(true, AddOnType.FEATURE);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("empty catalog — returns empty list")
        void emptyList() {
            when(addOnRepository.findAll()).thenReturn(List.of());

            List<AddOn> result = service.listAddOns(null, null);

            assertThat(result).isEmpty();
        }
    }

    // ── deleteAddOn ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteAddOn")
    class DeleteAddOn {

        @Test
        @DisplayName("success — delegates softDelete to repository with given actor")
        void success() {
            service.deleteAddOn(ACTOR_ID, ADD_ON_ID);

            verify(addOnRepository).softDelete(ADD_ON_ID, ACTOR_ID);
        }

        @Test
        @DisplayName("not found — ResourceNotFoundException from repository propagates")
        void notFound_propagatesFromRepository() {
            doThrow(new ResourceNotFoundException(ErrorCode.ADD_ON_NOT_FOUND, "not found"))
                .when(addOnRepository).softDelete(ADD_ON_ID, ACTOR_ID);

            assertThatThrownBy(() -> service.deleteAddOn(ACTOR_ID, ADD_ON_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(e -> assertThat(((ResourceNotFoundException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ADD_ON_NOT_FOUND));
        }
    }
}
