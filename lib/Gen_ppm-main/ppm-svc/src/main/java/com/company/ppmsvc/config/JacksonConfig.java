package com.company.ppmsvc.config;

import com.company.ppmsvc.promotion.model.EligibilityCondition;
import com.company.ppmsvc.promotion.model.FixedPriceDiscount;
import com.company.ppmsvc.promotion.model.FlatDiscount;
import com.company.ppmsvc.promotion.model.FreeAddOnDiscount;
import com.company.ppmsvc.promotion.model.FreeModuleDiscount;
import com.company.ppmsvc.promotion.model.FreePeriodDiscount;
import com.company.ppmsvc.promotion.model.PercentageDiscount;
import com.company.ppmsvc.promotion.model.PlanRestrictionCondition;
import com.company.ppmsvc.promotion.model.UsageLimitCondition;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Jackson {@link ObjectMapper} configuration shared across HTTP responses
 * and RabbitMQ message serialisation.
 *
 * <p>Java 8 date/time types are serialised as ISO-8601 strings.
 * Unknown JSON properties are ignored on deserialisation.
 *
 * <p>{@code PromotionAction}'s sealed subtypes are registered explicitly —
 * {@code @JsonTypeName} alone does not auto-register a NAME-based
 * discriminator; the mapper needs {@code registerSubtypes} to resolve
 * {@code "type": "percentage"/"flat"/...} to a concrete record. This
 * includes both {@code PriceAction} and {@code EntitlementAction} concrete
 * types (Phase 4) — registration is flat regardless of the two-level sealed
 * hierarchy the records sit under.
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.registerSubtypes(
            PercentageDiscount.class, FlatDiscount.class, FixedPriceDiscount.class,
            FreePeriodDiscount.class, FreeModuleDiscount.class, FreeAddOnDiscount.class,
            PlanRestrictionCondition.class, EligibilityCondition.class, UsageLimitCondition.class);
        return mapper;
    }
}
