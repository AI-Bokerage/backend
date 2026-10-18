package com.company.ppmsvc.promotion.usecase;

import com.company.ppmsvc.addon.model.AddOn;
import com.company.ppmsvc.addon.port.AddOnRepositoryPort;
import com.company.ppmsvc.campaign.model.Campaign;
import com.company.ppmsvc.campaign.model.CampaignStatus;
import com.company.ppmsvc.campaign.port.CampaignRepositoryPort;
import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.module.model.Module;
import com.company.ppmsvc.module.port.ModuleRepositoryPort;
import com.company.ppmsvc.promotion.model.FixedPriceDiscount;
import com.company.ppmsvc.promotion.model.FlatDiscount;
import com.company.ppmsvc.promotion.model.FreeAddOnDiscount;
import com.company.ppmsvc.promotion.model.FreeModuleDiscount;
import com.company.ppmsvc.promotion.model.FreePeriodDiscount;
import com.company.ppmsvc.promotion.model.PercentageDiscount;
import com.company.ppmsvc.promotion.model.Promotion;
import com.company.ppmsvc.promotion.model.PromotionAction;
import com.company.ppmsvc.promotion.model.PromotionStatus;
import com.company.ppmsvc.promotion.port.PromotionRepositoryPort;
import java.math.BigDecimal;
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
@DisplayName("PromotionApplicationServiceImpl")
class PromotionApplicationServiceImplTest {

    static final UUID ACTOR_ID     = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID PROMOTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID CAMPAIGN_ID  = UUID.fromString("00000000-0000-0000-0000-000000000003");

    static final LocalDate VALID_FROM  = LocalDate.of(2025, 1, 1);
    static final LocalDate VALID_UNTIL = LocalDate.of(2025, 12, 31);

    @Mock PromotionRepositoryPort promotionRepository;
    @Mock CampaignRepositoryPort  campaignRepository;
    @Mock ModuleRepositoryPort    moduleRepository;
    @Mock AddOnRepositoryPort     addOnRepository;

    @InjectMocks PromotionApplicationServiceImpl service;

    private Promotion buildPromotion(PromotionAction action, PromotionStatus status) {
        Instant now = Instant.now();
        return Promotion.builder()
            .id(PROMOTION_ID).version(0L)
            .name("Diwali Sale").description("20% off")
            .action(action)
            .validFrom(VALID_FROM).validUntil(VALID_UNTIL)
            .status(status)
            .createdAt(now).updatedAt(now)
            .createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
            .build();
    }

    private Campaign buildCampaign() {
        Instant now = Instant.now();
        return Campaign.builder()
            .id(CAMPAIGN_ID).version(0L)
            .name("Diwali Sale Campaign")
            .status(CampaignStatus.ACTIVE)
            .validFrom(VALID_FROM).validUntil(VALID_UNTIL)
            .createdAt(now).updatedAt(now)
            .createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
            .build();
    }

    @Nested
    @DisplayName("createPromotion()")
    class CreatePromotion {

        @Test
        @DisplayName("success — saves promotion and returns it")
        void create_success() {
            Promotion saved = buildPromotion(new PercentageDiscount(new BigDecimal("20"), new BigDecimal("200"), null),
                PromotionStatus.ACTIVE);
            when(promotionRepository.save(any())).thenReturn(saved);

            Promotion result = service.createPromotion(ACTOR_ID, "Diwali Sale", "20% off",
                new PercentageDiscount(new BigDecimal("20"), new BigDecimal("200"), null),
                VALID_FROM, VALID_UNTIL, PromotionStatus.ACTIVE, null, null, null, null);

            assertThat(result.getId()).isEqualTo(PROMOTION_ID);
            verify(promotionRepository).save(any());
        }

        @Test
        @DisplayName("DRAFT status is accepted on create")
        void create_draftStatus_accepted() {
            when(promotionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<Promotion> captor = ArgumentCaptor.forClass(Promotion.class);
            service.createPromotion(ACTOR_ID, "Staged", null, new FlatDiscount(new BigDecimal("10")),
                VALID_FROM, VALID_UNTIL, PromotionStatus.DRAFT, null, null, null, null);

            verify(promotionRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(PromotionStatus.DRAFT);
        }

        @Test
        @DisplayName("validUntil before validFrom throws VALIDATION_ERROR")
        void create_invalidDateRange_throws() {
            assertThatThrownBy(() -> service.createPromotion(ACTOR_ID, "Bad", null,
                    new FlatDiscount(new BigDecimal("10")),
                    LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 1), PromotionStatus.ACTIVE, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR));

            verify(promotionRepository, never()).save(any());
        }

        @Test
        @DisplayName("PercentageDiscount with maxDiscountValue < minDiscountValue throws VALIDATION_ERROR")
        void create_maxLessThanMin_throws() {
            assertThatThrownBy(() -> service.createPromotion(ACTOR_ID, "Bad", null,
                    new PercentageDiscount(new BigDecimal("20"), new BigDecimal("10"), new BigDecimal("50")),
                    VALID_FROM, VALID_UNTIL, PromotionStatus.ACTIVE, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR));

            verify(promotionRepository, never()).save(any());
        }

        @Test
        @DisplayName("PercentageDiscount value of exactly 100 is accepted")
        void create_percentage100_accepted() {
            when(promotionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.createPromotion(ACTOR_ID, "Full off", null,
                new PercentageDiscount(new BigDecimal("100"), null, null),
                VALID_FROM, VALID_UNTIL, PromotionStatus.ACTIVE, null, null, null, null);

            verify(promotionRepository).save(any());
        }

        @Test
        @DisplayName("PercentageDiscount value > 100 throws VALIDATION_ERROR")
        void create_percentageOver100_throws() {
            assertThatThrownBy(() -> service.createPromotion(ACTOR_ID, "Bad", null,
                    new PercentageDiscount(new BigDecimal("101"), null, null),
                    VALID_FROM, VALID_UNTIL, PromotionStatus.ACTIVE, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR));

            verify(promotionRepository, never()).save(any());
        }

        @Test
        @DisplayName("FlatDiscount amount = 0 throws VALIDATION_ERROR")
        void create_flatZero_throws() {
            assertThatThrownBy(() -> service.createPromotion(ACTOR_ID, "Bad", null,
                    new FlatDiscount(BigDecimal.ZERO),
                    VALID_FROM, VALID_UNTIL, PromotionStatus.ACTIVE, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR));

            verify(promotionRepository, never()).save(any());
        }

        @Test
        @DisplayName("null campaignId — accepted, no campaign existence check")
        void create_nullCampaignId_accepted() {
            when(promotionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.createPromotion(ACTOR_ID, "No Campaign", null, new FlatDiscount(new BigDecimal("10")),
                VALID_FROM, VALID_UNTIL, PromotionStatus.ACTIVE, null, null, null, null);

            verify(campaignRepository, never()).findById(any());
            verify(promotionRepository).save(any());
        }

        @Test
        @DisplayName("non-null campaignId — verified to exist, then saved")
        void create_withCampaignId_verifiesExistence() {
            when(campaignRepository.findById(CAMPAIGN_ID)).thenReturn(Optional.of(buildCampaign()));
            when(promotionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<Promotion> captor = ArgumentCaptor.forClass(Promotion.class);
            service.createPromotion(ACTOR_ID, "In Campaign", null, new FlatDiscount(new BigDecimal("10")),
                VALID_FROM, VALID_UNTIL, PromotionStatus.ACTIVE, null, CAMPAIGN_ID, null, null);

            verify(promotionRepository).save(captor.capture());
            assertThat(captor.getValue().getCampaignId()).isEqualTo(CAMPAIGN_ID);
        }

        @Test
        @DisplayName("non-existent campaignId — throws CAMPAIGN_NOT_FOUND")
        void create_unknownCampaignId_throws() {
            when(campaignRepository.findById(CAMPAIGN_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createPromotion(ACTOR_ID, "Bad", null,
                    new FlatDiscount(new BigDecimal("10")),
                    VALID_FROM, VALID_UNTIL, PromotionStatus.ACTIVE, null, CAMPAIGN_ID, null, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.CAMPAIGN_NOT_FOUND));

            verify(promotionRepository, never()).save(any());
        }

        @Test
        @DisplayName("FixedPriceDiscount(499) — succeeds")
        void create_fixedPriceDiscount_succeeds() {
            when(promotionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.createPromotion(ACTOR_ID, "Fixed Price", null, new FixedPriceDiscount(new BigDecimal("499")),
                VALID_FROM, VALID_UNTIL, PromotionStatus.ACTIVE, null, null, null, null);

            verify(promotionRepository).save(any());
        }

        @Test
        @DisplayName("FixedPriceDiscount(0) — throws VALIDATION_ERROR")
        void create_fixedPriceZero_throws() {
            assertThatThrownBy(() -> service.createPromotion(ACTOR_ID, "Bad", null,
                    new FixedPriceDiscount(BigDecimal.ZERO),
                    VALID_FROM, VALID_UNTIL, PromotionStatus.ACTIVE, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR));

            verify(promotionRepository, never()).save(any());
        }

        @Test
        @DisplayName("FixedPriceDiscount(negative) — throws VALIDATION_ERROR")
        void create_fixedPriceNegative_throws() {
            assertThatThrownBy(() -> service.createPromotion(ACTOR_ID, "Bad", null,
                    new FixedPriceDiscount(new BigDecimal("-10")),
                    VALID_FROM, VALID_UNTIL, PromotionStatus.ACTIVE, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR));

            verify(promotionRepository, never()).save(any());
        }

        @Test
        @DisplayName("FreePeriodDiscount(3) — succeeds")
        void create_freePeriodDiscount_succeeds() {
            when(promotionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.createPromotion(ACTOR_ID, "Free Period", null, new FreePeriodDiscount(3),
                VALID_FROM, VALID_UNTIL, PromotionStatus.ACTIVE, null, null, null, null);

            verify(promotionRepository).save(any());
        }

        @Test
        @DisplayName("FreePeriodDiscount(0) — throws VALIDATION_ERROR")
        void create_freePeriodZero_throws() {
            assertThatThrownBy(() -> service.createPromotion(ACTOR_ID, "Bad", null,
                    new FreePeriodDiscount(0),
                    VALID_FROM, VALID_UNTIL, PromotionStatus.ACTIVE, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR));

            verify(promotionRepository, never()).save(any());
        }

        @Test
        @DisplayName("FreePeriodDiscount(null) — throws VALIDATION_ERROR")
        void create_freePeriodNull_throws() {
            assertThatThrownBy(() -> service.createPromotion(ACTOR_ID, "Bad", null,
                    new FreePeriodDiscount(null),
                    VALID_FROM, VALID_UNTIL, PromotionStatus.ACTIVE, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR));

            verify(promotionRepository, never()).save(any());
        }

        @Test
        @DisplayName("FreePeriodDiscount(13) — throws VALIDATION_ERROR")
        void create_freePeriodOver12_throws() {
            assertThatThrownBy(() -> service.createPromotion(ACTOR_ID, "Bad", null,
                    new FreePeriodDiscount(13),
                    VALID_FROM, VALID_UNTIL, PromotionStatus.ACTIVE, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR));

            verify(promotionRepository, never()).save(any());
        }

        @Test
        @DisplayName("FreeModuleDiscount referencing existing module — succeeds")
        void create_freeModuleDiscount_existingModule_succeeds() {
            UUID moduleId = UUID.randomUUID();
            when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(org.mockito.Mockito.mock(Module.class)));
            when(promotionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.createPromotion(ACTOR_ID, "Free Module", null, new FreeModuleDiscount(moduleId, 3),
                VALID_FROM, VALID_UNTIL, PromotionStatus.ACTIVE, null, null, null, null);

            verify(promotionRepository).save(any());
        }

        @Test
        @DisplayName("FreeModuleDiscount referencing non-existent module — throws MODULE_NOT_FOUND")
        void create_freeModuleDiscount_unknownModule_throws() {
            UUID moduleId = UUID.randomUUID();
            when(moduleRepository.findById(moduleId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createPromotion(ACTOR_ID, "Bad", null,
                    new FreeModuleDiscount(moduleId, 3),
                    VALID_FROM, VALID_UNTIL, PromotionStatus.ACTIVE, null, null, null, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.MODULE_NOT_FOUND));

            verify(promotionRepository, never()).save(any());
        }

        @Test
        @DisplayName("FreeAddOnDiscount referencing existing add-on — succeeds")
        void create_freeAddOnDiscount_existingAddOn_succeeds() {
            UUID addOnId = UUID.randomUUID();
            when(addOnRepository.findById(addOnId)).thenReturn(Optional.of(org.mockito.Mockito.mock(AddOn.class)));
            when(promotionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.createPromotion(ACTOR_ID, "Free AddOn", null, new FreeAddOnDiscount(addOnId, 3),
                VALID_FROM, VALID_UNTIL, PromotionStatus.ACTIVE, null, null, null, null);

            verify(promotionRepository).save(any());
        }

        @Test
        @DisplayName("FreeAddOnDiscount referencing non-existent add-on — throws ADD_ON_NOT_FOUND")
        void create_freeAddOnDiscount_unknownAddOn_throws() {
            UUID addOnId = UUID.randomUUID();
            when(addOnRepository.findById(addOnId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createPromotion(ACTOR_ID, "Bad", null,
                    new FreeAddOnDiscount(addOnId, 3),
                    VALID_FROM, VALID_UNTIL, PromotionStatus.ACTIVE, null, null, null, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.ADD_ON_NOT_FOUND));

            verify(promotionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updatePromotion()")
    class UpdatePromotion {

        @Test
        @DisplayName("success — updates mutable fields and preserves keep-existing on null args")
        void update_success_keepsExistingOnNull() {
            Promotion existing = buildPromotion(new FlatDiscount(new BigDecimal("10")), PromotionStatus.ACTIVE);
            when(promotionRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(existing));
            when(promotionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<Promotion> captor = ArgumentCaptor.forClass(Promotion.class);
            service.updatePromotion(ACTOR_ID, PROMOTION_ID, "New Name", null, null, null, null, null, null, null, null, null);

            verify(promotionRepository).save(captor.capture());
            Promotion saved = captor.getValue();
            assertThat(saved.getName()).isEqualTo("New Name");
            assertThat(saved.getDescription()).isEqualTo(existing.getDescription());
            assertThat(saved.getAction()).isEqualTo(existing.getAction());
            assertThat(saved.getStatus()).isEqualTo(existing.getStatus());
        }

        @Test
        @DisplayName("not found — throws PROMOTION_NOT_FOUND")
        void update_notFound_throws() {
            when(promotionRepository.findById(PROMOTION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updatePromotion(ACTOR_ID, PROMOTION_ID, "X", null, null, null, null, null, null, null, null, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PROMOTION_NOT_FOUND));

            verify(promotionRepository, never()).save(any());
        }

        @Test
        @DisplayName("replacing action re-validates the new action")
        void update_invalidNewAction_throws() {
            Promotion existing = buildPromotion(new FlatDiscount(new BigDecimal("10")), PromotionStatus.ACTIVE);
            when(promotionRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> service.updatePromotion(ACTOR_ID, PROMOTION_ID, null, null,
                    new PercentageDiscount(new BigDecimal("200"), null, null), null, null, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR));

            verify(promotionRepository, never()).save(any());
        }

        @Test
        @DisplayName("BR — id, createdAt, createdBy always preserved; updatedBy refreshed")
        void update_immutableFieldsPreserved() {
            Promotion existing = buildPromotion(new FlatDiscount(new BigDecimal("10")), PromotionStatus.ACTIVE);
            when(promotionRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(existing));
            when(promotionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<Promotion> captor = ArgumentCaptor.forClass(Promotion.class);
            service.updatePromotion(ACTOR_ID, PROMOTION_ID, "Renamed", null, null, null, null, null, null, null, null, null);

            verify(promotionRepository).save(captor.capture());
            Promotion saved = captor.getValue();
            assertThat(saved.getId()).isEqualTo(existing.getId());
            assertThat(saved.getCreatedAt()).isEqualTo(existing.getCreatedAt());
            assertThat(saved.getCreatedBy()).isEqualTo(existing.getCreatedBy());
            assertThat(saved.getUpdatedBy()).isEqualTo(ACTOR_ID);
        }

        @Test
        @DisplayName("null campaignId — clears any existing campaign, no existence check")
        void update_nullCampaignId_clearsCampaign() {
            Promotion existing = Promotion.builder()
                .id(PROMOTION_ID).version(0L)
                .name("Diwali Sale").action(new FlatDiscount(new BigDecimal("10")))
                .validFrom(VALID_FROM).validUntil(VALID_UNTIL)
                .status(PromotionStatus.ACTIVE)
                .campaignId(CAMPAIGN_ID)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
                .build();
            when(promotionRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(existing));
            when(promotionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<Promotion> captor = ArgumentCaptor.forClass(Promotion.class);
            service.updatePromotion(ACTOR_ID, PROMOTION_ID, null, null, null, null, null, null, null, null, null, null);

            verify(promotionRepository).save(captor.capture());
            assertThat(captor.getValue().getCampaignId()).isNull();
            verify(campaignRepository, never()).findById(any());
        }

        @Test
        @DisplayName("non-existent campaignId — throws CAMPAIGN_NOT_FOUND")
        void update_unknownCampaignId_throws() {
            Promotion existing = buildPromotion(new FlatDiscount(new BigDecimal("10")), PromotionStatus.ACTIVE);
            when(promotionRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(existing));
            when(campaignRepository.findById(CAMPAIGN_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updatePromotion(ACTOR_ID, PROMOTION_ID, null, null, null,
                    null, null, null, null, CAMPAIGN_ID, null, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.CAMPAIGN_NOT_FOUND));

            verify(promotionRepository, never()).save(any());
        }

        @Test
        @DisplayName("changing action from FlatDiscount to FixedPriceDiscount — revalidates and applies")
        void update_actionChangedToFixedPrice_revalidatesAndApplies() {
            Promotion existing = buildPromotion(new FlatDiscount(new BigDecimal("10")), PromotionStatus.ACTIVE);
            when(promotionRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(existing));
            when(promotionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<Promotion> captor = ArgumentCaptor.forClass(Promotion.class);
            service.updatePromotion(ACTOR_ID, PROMOTION_ID, null, null, new FixedPriceDiscount(new BigDecimal("499")),
                null, null, null, null, null, null, null);

            verify(promotionRepository).save(captor.capture());
            assertThat(captor.getValue().getAction()).isEqualTo(new FixedPriceDiscount(new BigDecimal("499")));
        }
    }

    @Nested
    @DisplayName("getPromotion()")
    class GetPromotion {

        @Test
        @DisplayName("success — returns promotion when it exists")
        void get_success() {
            Promotion promotion = buildPromotion(new FlatDiscount(new BigDecimal("10")), PromotionStatus.ACTIVE);
            when(promotionRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(promotion));

            assertThat(service.getPromotion(PROMOTION_ID).getId()).isEqualTo(PROMOTION_ID);
        }

        @Test
        @DisplayName("not found — throws PROMOTION_NOT_FOUND")
        void get_notFound_throws() {
            when(promotionRepository.findById(PROMOTION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getPromotion(PROMOTION_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PROMOTION_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("listPromotions()")
    class ListPromotions {

        @Test
        @DisplayName("status filter narrows correctly")
        void list_statusFilter_narrowsCorrectly() {
            Promotion active = buildPromotion(new FlatDiscount(new BigDecimal("10")), PromotionStatus.ACTIVE);
            Promotion draft  = buildPromotion(new FlatDiscount(new BigDecimal("5")), PromotionStatus.DRAFT);
            when(promotionRepository.findAll()).thenReturn(java.util.List.of(active, draft));

            assertThat(service.listPromotions(PromotionStatus.DRAFT)).hasSize(1)
                .allMatch(p -> p.getStatus() == PromotionStatus.DRAFT);
        }

        @Test
        @DisplayName("no filter — returns all")
        void list_noFilter_returnsAll() {
            Promotion active = buildPromotion(new FlatDiscount(new BigDecimal("10")), PromotionStatus.ACTIVE);
            Promotion draft  = buildPromotion(new FlatDiscount(new BigDecimal("5")), PromotionStatus.DRAFT);
            when(promotionRepository.findAll()).thenReturn(java.util.List.of(active, draft));

            assertThat(service.listPromotions(null)).hasSize(2);
        }
    }

    @Nested
    @DisplayName("deletePromotion()")
    class DeletePromotion {

        @Test
        @DisplayName("success — delegates to softDelete with given actor")
        void delete_success() {
            service.deletePromotion(ACTOR_ID, PROMOTION_ID);

            verify(promotionRepository).softDelete(PROMOTION_ID, ACTOR_ID);
        }
    }
}
