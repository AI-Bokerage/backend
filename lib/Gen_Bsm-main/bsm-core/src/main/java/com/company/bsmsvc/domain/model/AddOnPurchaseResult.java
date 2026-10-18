package com.company.bsmsvc.domain.model;

import java.util.UUID;

/** Result of purchasing a PPM-backed subscription add-on. */
public record AddOnPurchaseResult(
    UUID subscriptionAddOnId,
    UUID invoiceId,
    String checkoutUrl,
    String sessionId,
    UUID ppmAddOnId,
    UUID ppmAddOnPriceId,
    Long ppmResolvedPriceMinor,
    String currency
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID subscriptionAddOnId;
        private UUID invoiceId;
        private String checkoutUrl;
        private String sessionId;
        private UUID ppmAddOnId;
        private UUID ppmAddOnPriceId;
        private Long ppmResolvedPriceMinor;
        private String currency;

        public Builder subscriptionAddOnId(UUID v) { this.subscriptionAddOnId = v; return this; }
        public Builder invoiceId(UUID v) { this.invoiceId = v; return this; }
        public Builder checkoutUrl(String v) { this.checkoutUrl = v; return this; }
        public Builder sessionId(String v) { this.sessionId = v; return this; }
        public Builder ppmAddOnId(UUID v) { this.ppmAddOnId = v; return this; }
        public Builder ppmAddOnPriceId(UUID v) { this.ppmAddOnPriceId = v; return this; }
        public Builder ppmResolvedPriceMinor(Long v) { this.ppmResolvedPriceMinor = v; return this; }
        public Builder currency(String v) { this.currency = v; return this; }

        public AddOnPurchaseResult build() {
            return new AddOnPurchaseResult(subscriptionAddOnId, invoiceId, checkoutUrl, sessionId,
                ppmAddOnId, ppmAddOnPriceId, ppmResolvedPriceMinor, currency);
        }
    }
}
