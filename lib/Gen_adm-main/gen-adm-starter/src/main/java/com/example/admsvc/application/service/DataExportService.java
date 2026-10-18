package com.example.admsvc.application.service;

import com.example.admsvc.infrastructure.persistence.entity.DataExportEntity;

import java.util.List;
import java.util.UUID;

public interface DataExportService {

    DataExportEntity request(UUID tenantId, UUID requestedByUserId);

    DataExportEntity getStatus(UUID tenantId, UUID exportId);

    List<DataExportEntity> listExports(UUID tenantId);

    String download(UUID tenantId, UUID exportId);

    DataExportEntity revoke(UUID tenantId, UUID exportId, UUID revokedByUserId);
}
