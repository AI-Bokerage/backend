package com.company.bsmsvc.infrastructure.persistence.mapper;

import com.company.bsmsvc.domain.model.InvoiceLineItem;
import com.company.bsmsvc.infrastructure.persistence.entity.InvoiceLineItemEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface InvoiceLineItemEntityMapper {

    @Mapping(target = "invoiceId", source = "invoice.id")
    InvoiceLineItem toDomain(InvoiceLineItemEntity entity);

    @Mapping(target = "invoice.id", source = "invoiceId")
    InvoiceLineItemEntity toEntity(InvoiceLineItem domain);
}
