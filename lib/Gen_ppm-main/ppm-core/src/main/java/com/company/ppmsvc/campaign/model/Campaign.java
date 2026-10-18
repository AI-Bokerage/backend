package com.company.ppmsvc.campaign.model;

import com.company.ppmsvc.common.AuditableEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * A marketing campaign — a grouping primitive for {@link
 * com.company.ppmsvc.promotion.model.Promotion}s (e.g. "Diwali Sale" owning
 * several promo/discount promotions).
 *
 * <p><strong>Organizational grouping, not a quote-time gate.</strong> A
 * promotion's own status/validity/conditions govern its evaluation in the
 * pricing pipeline — never the campaign's. A promotion belonging to an
 * archived or expired campaign is still individually evaluable.
 *
 * <p>References promotions via their {@code campaignId} field but never owns
 * or embeds promotion state — mirrors the {@code ReferralProgram} invariant.
 * Promotions are created/managed via the existing Promotion CRUD; a
 * promotion's {@code campaignId} is set there, not here.
 *
 * <p><strong>Framework independence:</strong> no JPA, Spring, or other
 * infrastructure annotations appear in this class.
 */
@Getter
public class Campaign extends AuditableEntity {

    private final String name;
    private final String description;
    private final CampaignStatus status;
    private final LocalDate validFrom;
    private final LocalDate validUntil;

    @Builder
    private Campaign(UUID id, Long version,
                     Instant createdAt, Instant updatedAt, UUID createdBy, UUID updatedBy,
                     String name, String description, CampaignStatus status,
                     LocalDate validFrom, LocalDate validUntil) {
        super(id, createdAt, updatedAt, createdBy, updatedBy);
        this.version     = version;
        this.name        = name;
        this.description = description;
        this.status      = status;
        this.validFrom   = validFrom;
        this.validUntil  = validUntil;
    }
}
