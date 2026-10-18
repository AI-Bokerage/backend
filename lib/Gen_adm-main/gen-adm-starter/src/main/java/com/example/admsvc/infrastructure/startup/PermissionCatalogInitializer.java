package com.example.admsvc.infrastructure.startup;

import com.example.admsvc.config.properties.GenAdmProperties;
import com.example.admsvc.infrastructure.persistence.entity.PermissionEntity;
import com.example.admsvc.infrastructure.persistence.repository.PermissionRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class PermissionCatalogInitializer implements ApplicationRunner {

    private final GenAdmProperties properties;
    private final PermissionRepository permissionRepository;

    public PermissionCatalogInitializer(GenAdmProperties properties, PermissionRepository permissionRepository) {
        this.properties = properties;
        this.permissionRepository = permissionRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (GenAdmProperties.PermissionDefinition definition : properties.getPermissions()) {
            PermissionEntity permission = permissionRepository.findByCode(definition.code())
                    .orElseGet(() -> PermissionEntity.builder().code(definition.code()).build());
            permission.setDescription(definition.description());
            permissionRepository.save(permission);
        }
    }
}
