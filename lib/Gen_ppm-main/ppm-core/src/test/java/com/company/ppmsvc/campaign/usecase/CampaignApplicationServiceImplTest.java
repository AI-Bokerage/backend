package com.company.ppmsvc.campaign.usecase;

import com.company.ppmsvc.campaign.model.Campaign;
import com.company.ppmsvc.campaign.model.CampaignStatus;
import com.company.ppmsvc.campaign.port.CampaignRepositoryPort;
import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
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
@DisplayName("CampaignApplicationServiceImpl")
class CampaignApplicationServiceImplTest {

    static final UUID ACTOR_ID    = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID CAMPAIGN_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    static final LocalDate VALID_FROM  = LocalDate.of(2025, 1, 1);
    static final LocalDate VALID_UNTIL = LocalDate.of(2025, 12, 31);

    @Mock CampaignRepositoryPort campaignRepository;

    @InjectMocks CampaignApplicationServiceImpl service;

    private Campaign buildCampaign(CampaignStatus status) {
        Instant now = Instant.now();
        return Campaign.builder()
            .id(CAMPAIGN_ID).version(0L)
            .name("Diwali Sale").description("Festival campaign")
            .status(status)
            .validFrom(VALID_FROM).validUntil(VALID_UNTIL)
            .createdAt(now).updatedAt(now)
            .createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
            .build();
    }

    @Nested
    @DisplayName("createCampaign()")
    class CreateCampaign {

        @Test
        @DisplayName("success — saves campaign and returns it")
        void create_success() {
            when(campaignRepository.save(any())).thenReturn(buildCampaign(CampaignStatus.DRAFT));

            Campaign result = service.createCampaign(ACTOR_ID, "Diwali Sale", "Festival campaign",
                VALID_FROM, VALID_UNTIL, null);

            assertThat(result.getId()).isEqualTo(CAMPAIGN_ID);
            verify(campaignRepository).save(any());
        }

        @Test
        @DisplayName("status defaults to DRAFT when null")
        void create_defaultsToDraft() {
            when(campaignRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<Campaign> captor = ArgumentCaptor.forClass(Campaign.class);
            service.createCampaign(ACTOR_ID, "Diwali Sale", null, VALID_FROM, VALID_UNTIL, null);

            verify(campaignRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(CampaignStatus.DRAFT);
        }

        @Test
        @DisplayName("validUntil before validFrom throws VALIDATION_ERROR")
        void create_invalidDateRange_throws() {
            assertThatThrownBy(() -> service.createCampaign(ACTOR_ID, "Bad", null,
                    LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 1), null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR));

            verify(campaignRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updateCampaign()")
    class UpdateCampaign {

        @Test
        @DisplayName("success — PATCH keeps existing values on null args")
        void update_keepsExistingOnNull() {
            Campaign existing = buildCampaign(CampaignStatus.DRAFT);
            when(campaignRepository.findById(CAMPAIGN_ID)).thenReturn(Optional.of(existing));
            when(campaignRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<Campaign> captor = ArgumentCaptor.forClass(Campaign.class);
            service.updateCampaign(ACTOR_ID, CAMPAIGN_ID, null, null, null, null, CampaignStatus.ACTIVE);

            verify(campaignRepository).save(captor.capture());
            Campaign saved = captor.getValue();
            assertThat(saved.getName()).isEqualTo(existing.getName());
            assertThat(saved.getStatus()).isEqualTo(CampaignStatus.ACTIVE);
        }

        @Test
        @DisplayName("not found — throws CAMPAIGN_NOT_FOUND")
        void update_notFound_throws() {
            when(campaignRepository.findById(CAMPAIGN_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateCampaign(ACTOR_ID, CAMPAIGN_ID, "X", null, null, null, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.CAMPAIGN_NOT_FOUND));

            verify(campaignRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getCampaign()")
    class GetCampaign {

        @Test
        @DisplayName("success — returns campaign when it exists")
        void get_success() {
            when(campaignRepository.findById(CAMPAIGN_ID)).thenReturn(Optional.of(buildCampaign(CampaignStatus.ACTIVE)));

            assertThat(service.getCampaign(CAMPAIGN_ID).getId()).isEqualTo(CAMPAIGN_ID);
        }

        @Test
        @DisplayName("not found — throws CAMPAIGN_NOT_FOUND")
        void get_notFound_throws() {
            when(campaignRepository.findById(CAMPAIGN_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getCampaign(CAMPAIGN_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.CAMPAIGN_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("listCampaigns()")
    class ListCampaigns {

        @Test
        @DisplayName("status filter narrows correctly")
        void list_statusFilter_narrowsCorrectly() {
            Campaign draft  = buildCampaign(CampaignStatus.DRAFT);
            Campaign active = buildCampaign(CampaignStatus.ACTIVE);
            when(campaignRepository.findAll()).thenReturn(java.util.List.of(draft, active));

            assertThat(service.listCampaigns(CampaignStatus.ACTIVE)).hasSize(1)
                .allMatch(c -> c.getStatus() == CampaignStatus.ACTIVE);
        }

        @Test
        @DisplayName("no filter — returns all")
        void list_noFilter_returnsAll() {
            Campaign draft  = buildCampaign(CampaignStatus.DRAFT);
            Campaign active = buildCampaign(CampaignStatus.ACTIVE);
            when(campaignRepository.findAll()).thenReturn(java.util.List.of(draft, active));

            assertThat(service.listCampaigns(null)).hasSize(2);
        }
    }

    @Nested
    @DisplayName("deleteCampaign()")
    class DeleteCampaign {

        @Test
        @DisplayName("success — delegates to softDelete with given actor")
        void delete_success() {
            service.deleteCampaign(ACTOR_ID, CAMPAIGN_ID);

            verify(campaignRepository).softDelete(CAMPAIGN_ID, ACTOR_ID);
        }
    }
}
