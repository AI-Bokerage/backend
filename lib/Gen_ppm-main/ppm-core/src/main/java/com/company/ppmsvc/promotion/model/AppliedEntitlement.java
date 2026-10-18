package com.company.ppmsvc.promotion.model;

import java.util.UUID;

/**
 * The result of applying an {@link EntitlementAction}. Carries the grant
 * details in a flat, caller-friendly shape so checkout doesn't need to
 * deserialize the polymorphic {@link PromotionAction} to know what was
 * granted.
 *
 * <p>{@code targetId} is {@code null} for {@code FREE_PERIOD} — the target
 * is the plan itself, not a specific module/add-on.
 */
public record AppliedEntitlement(EntitlementType type, UUID targetId, Integer durationMonths) {

    /** Packages an {@link EntitlementAction} into a flat grant record. */
    public static AppliedEntitlement from(EntitlementAction action) {
        return switch (action) {
            case FreePeriodDiscount fp -> new AppliedEntitlement(EntitlementType.FREE_PERIOD, null, fp.durationMonths());
            case FreeModuleDiscount fm -> new AppliedEntitlement(EntitlementType.FREE_MODULE, fm.moduleId(), fm.durationMonths());
            case FreeAddOnDiscount  fa -> new AppliedEntitlement(EntitlementType.FREE_ADDON, fa.addOnId(), fa.durationMonths());
        };
    }
}
