package com.example.admsvc.infrastructure.startup;

import com.example.admsvc.config.properties.GenAdmProperties;
import com.example.admsvc.infrastructure.persistence.entity.PermissionEntity;
import com.example.admsvc.infrastructure.persistence.repository.PermissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissionCatalogInitializerTest {

    @Mock
    private PermissionRepository permissionRepository;

    @Test
    void createsPermissionsThatDoNotExistYet() {
        GenAdmProperties properties = new GenAdmProperties();
        properties.setPermissions(List.of(
                new GenAdmProperties.PermissionDefinition("users:read", "Read users"),
                new GenAdmProperties.PermissionDefinition("adm:roles:manage", "Manage roles")
        ));
        when(permissionRepository.findByCode(anyString())).thenReturn(Optional.empty());
        when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        new PermissionCatalogInitializer(properties, permissionRepository).run(null);

        ArgumentCaptor<PermissionEntity> captor = ArgumentCaptor.forClass(PermissionEntity.class);
        verify(permissionRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(PermissionEntity::getCode)
                .containsExactlyInAnyOrder("users:read", "adm:roles:manage");
    }

    @Test
    void updatesDescriptionOfAnAlreadyExistingPermissionWithoutDuplicating() {
        GenAdmProperties properties = new GenAdmProperties();
        properties.setPermissions(List.of(
                new GenAdmProperties.PermissionDefinition("users:read", "New description")
        ));
        PermissionEntity existing = PermissionEntity.builder().code("users:read").description("Old description").build();
        when(permissionRepository.findByCode("users:read")).thenReturn(Optional.of(existing));
        when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        new PermissionCatalogInitializer(properties, permissionRepository).run(null);

        ArgumentCaptor<PermissionEntity> captor = ArgumentCaptor.forClass(PermissionEntity.class);
        verify(permissionRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getDescription()).isEqualTo("New description");
    }
}
