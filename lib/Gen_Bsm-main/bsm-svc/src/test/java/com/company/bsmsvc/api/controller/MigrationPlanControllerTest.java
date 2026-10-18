package com.company.bsmsvc.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.bsmsvc.api.advice.GlobalExceptionHandler;
import com.company.bsmsvc.api.dto.request.CreateMigrationPlanItemRequest;
import com.company.bsmsvc.api.dto.request.CreateMigrationPlanRequest;
import com.company.bsmsvc.api.dto.response.MigrationPlanItemResponse;
import com.company.bsmsvc.api.dto.response.MigrationPlanResponse;
import com.company.bsmsvc.api.mapper.MigrationPlanApiMapper;
import com.company.bsmsvc.application.service.MigrationPlanService;
import com.company.bsmsvc.domain.enums.MigrationAction;
import com.company.bsmsvc.domain.enums.MigrationPlanStatus;
import com.company.bsmsvc.domain.enums.MigrationResourceType;
import com.company.bsmsvc.domain.model.MigrationPlan;
import com.company.bsmsvc.domain.model.MigrationPlanItem;
import com.company.bsmsvc.domain.model.PageResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = MigrationPlanController.class)
@Import(GlobalExceptionHandler.class)
class MigrationPlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MigrationPlanService migrationPlanService;
    @MockitoBean
    private MigrationPlanApiMapper migrationPlanApiMapper;

    @Test
    void createMigrationPlanShouldReturnCreatedPayload() throws Exception {
        UUID subscriptionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID targetPlanVersionId = UUID.randomUUID();
        UUID createdBy = UUID.randomUUID();
        CreateMigrationPlanRequest request = new CreateMigrationPlanRequest(
            subscriptionId,
            tenantId,
            targetPlanVersionId,
            createdBy,
            List.of(new CreateMigrationPlanItemRequest(MigrationResourceType.PROJECT, UUID.randomUUID(), MigrationAction.ARCHIVE, Map.of("reason", "cap")))
        );
        MigrationPlan domainPlan = MigrationPlan.builder()
            .subscriptionId(subscriptionId)
            .tenantId(tenantId)
            .targetPlanVersionId(targetPlanVersionId)
            .createdBy(createdBy)
            .build();
        MigrationPlanItem domainItem = MigrationPlanItem.builder()
            .resourceType(MigrationResourceType.PROJECT)
            .resourceId(request.items().getFirst().resourceId())
            .action(MigrationAction.ARCHIVE)
            .metadata(Map.of("reason", "cap"))
            .build();
        MigrationPlan createdPlan = MigrationPlan.builder()
            .id(UUID.randomUUID())
            .subscriptionId(subscriptionId)
            .tenantId(tenantId)
            .targetPlanVersionId(targetPlanVersionId)
            .status(MigrationPlanStatus.DRAFT)
            .createdBy(createdBy)
            .items(List.of(domainItem.toBuilder().id(UUID.randomUUID()).migrationPlanId(UUID.randomUUID()).createdAt(Instant.now()).build()))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        MigrationPlanResponse response = new MigrationPlanResponse(
            createdPlan.getId(),
            subscriptionId,
            tenantId,
            targetPlanVersionId,
            MigrationPlanStatus.DRAFT,
            createdBy,
            List.of(new MigrationPlanItemResponse(
                createdPlan.getItems().getFirst().getId(),
                createdPlan.getItems().getFirst().getMigrationPlanId(),
                MigrationResourceType.PROJECT,
                createdPlan.getItems().getFirst().getResourceId(),
                MigrationAction.ARCHIVE,
                createdPlan.getItems().getFirst().getMetadata(),
                createdPlan.getItems().getFirst().getCreatedAt()
            )),
            createdPlan.getCreatedAt(),
            createdPlan.getUpdatedAt()
        );

        when(migrationPlanApiMapper.toDomain(request)).thenReturn(domainPlan);
        when(migrationPlanApiMapper.toDomainItems(request.items())).thenReturn(List.of(domainItem));
        when(migrationPlanService.createMigrationPlan(domainPlan, List.of(domainItem))).thenReturn(createdPlan);
        when(migrationPlanApiMapper.toResponse(createdPlan)).thenReturn(response);

        mockMvc.perform(post("/api/v1/bsm/migration-plans")
                .contentType("application/json")
                .content("""
                    {
                      "subscriptionId": "%s",
                      "tenantId": "%s",
                      "targetPlanVersionId": "%s",
                      "createdBy": "%s",
                      "items": [
                        {
                          "resourceType": "PROJECT",
                          "resourceId": "%s",
                          "action": "ARCHIVE",
                          "metadata": {
                            "reason": "cap"
                          }
                        }
                      ]
                    }
                    """.formatted(subscriptionId, tenantId, targetPlanVersionId, createdBy, request.items().getFirst().resourceId())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void getMigrationPlanShouldReturnSuccessPayload() throws Exception {
        UUID planId = UUID.randomUUID();
        MigrationPlan plan = MigrationPlan.builder()
            .id(planId)
            .subscriptionId(UUID.randomUUID())
            .tenantId(UUID.randomUUID())
            .targetPlanVersionId(UUID.randomUUID())
            .status(MigrationPlanStatus.DRAFT)
            .createdBy(UUID.randomUUID())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .items(List.of())
            .build();
        MigrationPlanResponse response = new MigrationPlanResponse(
            planId, plan.getSubscriptionId(), plan.getTenantId(), plan.getTargetPlanVersionId(), plan.getStatus(),
            plan.getCreatedBy(), List.of(), plan.getCreatedAt(), plan.getUpdatedAt()
        );

        when(migrationPlanService.getMigrationPlan(planId)).thenReturn(plan);
        when(migrationPlanApiMapper.toResponse(plan)).thenReturn(response);

        mockMvc.perform(get("/api/v1/bsm/migration-plans/{id}", planId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(planId.toString()));
    }

    @Test
    void listMigrationPlansShouldReturnPaginatedPayload() throws Exception {
        MigrationPlan plan = MigrationPlan.builder()
            .id(UUID.randomUUID())
            .subscriptionId(UUID.randomUUID())
            .tenantId(UUID.randomUUID())
            .targetPlanVersionId(UUID.randomUUID())
            .status(MigrationPlanStatus.DRAFT)
            .createdBy(UUID.randomUUID())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .items(List.of())
            .build();
        MigrationPlanResponse response = new MigrationPlanResponse(
            plan.getId(), plan.getSubscriptionId(), plan.getTenantId(), plan.getTargetPlanVersionId(), plan.getStatus(),
            plan.getCreatedBy(), List.of(), plan.getCreatedAt(), plan.getUpdatedAt()
        );

        when(migrationPlanService.listMigrationPlans(any(), any(Integer.class), any(Integer.class), any(), any()))
            .thenReturn(new PageResult<>(List.of(plan), 0, 20, 1, 1, false));
        when(migrationPlanApiMapper.toResponse(plan)).thenReturn(response);

        mockMvc.perform(get("/api/v1/bsm/migration-plans").param("page", "0").param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.pagination.totalElements").value(1))
            .andExpect(jsonPath("$.data[0].id").value(plan.getId().toString()));
    }
}
