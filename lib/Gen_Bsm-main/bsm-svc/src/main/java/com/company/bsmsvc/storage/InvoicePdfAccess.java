package com.company.bsmsvc.storage;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class InvoicePdfAccess {
    private final String preSignedUrl;
    private final String expiresAt;
}
