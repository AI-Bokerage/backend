package com.company.bsmsvc.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class InvoicePdfStatusResponse {
    private final String status;
    private final String pdfUrl;
    private final String preSignedUrl;
    private final String expiresAt;
}
