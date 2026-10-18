package com.company.bsmsvc.domain.model.payment;

import java.util.UUID;

public record CreateCustomerCommand(UUID tenantId, String name, String email) {}
