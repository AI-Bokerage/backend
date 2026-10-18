package com.company.bsmsvc.infrastructure.persistence.mapper;

import com.company.bsmsvc.domain.model.PlatformInvoice;
import com.company.bsmsvc.infrastructure.persistence.entity.PlatformInvoiceEntity;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", uses = {InvoiceLineItemEntityMapper.class})
public interface PlatformInvoiceEntityMapper {

    @Mapping(target = "subscriptionId", source = "subscription.id")
    @Mapping(target = "domainEvents", ignore = true)
    @Mapping(target = "pdfUrl", source = "pdfUrl")
    @Mapping(target = "pdfGeneratedAt", source = "pdfGeneratedAt")
    @Mapping(target = "pdfGenerationStatus", source = "pdfGenerationStatus")
    PlatformInvoice toDomain(PlatformInvoiceEntity entity);

    @Mapping(target = "subscription.id", source = "subscriptionId")
    @Mapping(target = "pdfUrl", source = "pdfUrl")
    @Mapping(target = "pdfGeneratedAt", source = "pdfGeneratedAt")
    @Mapping(target = "pdfGenerationStatus", source = "pdfGenerationStatus")
    PlatformInvoiceEntity toEntity(PlatformInvoice domain);

    /**
     * JPA parent-child link helper.
     * In Hibernate, saving bidirectional children cascadingly requires explicitly 
     * setting the parent reference on each child entity before saving.
     */
    @AfterMapping
    default void linkLineItems(@MappingTarget PlatformInvoiceEntity entity) {
        if (entity.getLineItems() != null) {
            entity.getLineItems().forEach(lineItem -> lineItem.setInvoice(entity));
        }
    }
}
