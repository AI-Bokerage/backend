package com.company.bsmsvc.domain.model;

import com.company.bsmsvc.domain.enums.PpmPlanChangeType;
import java.util.UUID;

/** Result of applying a PPM plan change atomically. */
public record PlanChangeApplyResult(
    UUID subscriptionId,
    UUID invoiceId,
    String checkoutUrl,
    String sessionId,
    Long netAmountMinor,
    String currency,
    PpmPlanChangeType changeType,
    UUID ppmPlanVersionId,
    Long promoDiscountMinor,
    Long discountedNetAmountMinor
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID subscriptionId;
        private UUID invoiceId;
        private String checkoutUrl;
        private String sessionId;
        private Long netAmountMinor;
        private String currency;
        private PpmPlanChangeType changeType;
        private UUID ppmPlanVersionId;
        private Long promoDiscountMinor;
        private Long discountedNetAmountMinor;

        public Builder subscriptionId(UUID v) { this.subscriptionId = v; return this; }
        public Builder invoiceId(UUID v) { this.invoiceId = v; return this; }
        public Builder checkoutUrl(String v) { this.checkoutUrl = v; return this; }
        public Builder sessionId(String v) { this.sessionId = v; return this; }
        public Builder netAmountMinor(Long v) { this.netAmountMinor = v; return this; }
        public Builder currency(String v) { this.currency = v; return this; }
        public Builder changeType(PpmPlanChangeType v) { this.changeType = v; return this; }
        public Builder ppmPlanVersionId(UUID v) { this.ppmPlanVersionId = v; return this; }
        public Builder promoDiscountMinor(Long v) { this.promoDiscountMinor = v; return this; }
        public Builder discountedNetAmountMinor(Long v) { this.discountedNetAmountMinor = v; return this; }

        public PlanChangeApplyResult build() {
            return new PlanChangeApplyResult(subscriptionId, invoiceId, checkoutUrl, sessionId,
                netAmountMinor, currency, changeType, ppmPlanVersionId, promoDiscountMinor, discountedNetAmountMinor);
        }
    }
}
