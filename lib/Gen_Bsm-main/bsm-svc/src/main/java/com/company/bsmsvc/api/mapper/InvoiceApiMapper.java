package com.company.bsmsvc.api.mapper;

import com.company.bsmsvc.api.dto.request.CreateInvoiceLineItemRequest;
import com.company.bsmsvc.api.dto.request.CreateInvoiceRequest;
import com.company.bsmsvc.api.dto.response.InvoiceLineItemResponse;
import com.company.bsmsvc.api.dto.response.InvoiceResponse;
import com.company.bsmsvc.domain.model.InvoiceLineItem;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface InvoiceApiMapper {

    // Only tenantId and subscriptionId come from the request; everything else is derived
    // from the subscription in InvoiceServiceImpl before invoice generation.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "invoiceNumber", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "amountDue", ignore = true)
    @Mapping(target = "amountPaid", ignore = true)
    @Mapping(target = "paidAt", ignore = true)
    @Mapping(target = "currency", ignore = true)
    @Mapping(target = "periodStart", ignore = true)
    @Mapping(target = "periodEnd", ignore = true)
    @Mapping(target = "dueDate", ignore = true)
    @Mapping(target = "lineItems", ignore = true)
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "domainEvents", ignore = true)
    @Mapping(target = "pdfUrl", ignore = true)
    @Mapping(target = "pdfGeneratedAt", ignore = true)
    @Mapping(target = "pdfGenerationStatus", ignore = true)
    PlatformInvoice toDomain(CreateInvoiceRequest request);

    InvoiceResponse toResponse(PlatformInvoice invoice);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "invoiceId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    InvoiceLineItem toDomain(CreateInvoiceLineItemRequest request);

    InvoiceLineItemResponse toResponse(InvoiceLineItem lineItem);
}
