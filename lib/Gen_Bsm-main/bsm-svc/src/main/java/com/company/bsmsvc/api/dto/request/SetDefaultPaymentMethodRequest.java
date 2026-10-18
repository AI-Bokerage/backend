package com.company.bsmsvc.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Set default payment method request (empty body — method ID is in path)")
public record SetDefaultPaymentMethodRequest() {}
