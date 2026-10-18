package com.example.admsvc.api.controller;

import com.example.admsvc.api.dto.response.PermissionResponse;
import com.example.admsvc.infrastructure.persistence.repository.PermissionRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/permissions")
public class PermissionController {

    private final PermissionRepository permissionRepository;

    public PermissionController(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    @GetMapping
    public List<PermissionResponse> list() {
        return permissionRepository.findAll().stream().map(PermissionResponse::from).toList();
    }
}
