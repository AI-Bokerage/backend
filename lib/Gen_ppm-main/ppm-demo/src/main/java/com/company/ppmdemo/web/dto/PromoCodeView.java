package com.company.ppmdemo.web.dto;

import java.util.UUID;

public record PromoCodeView(UUID id, String code, boolean active) {}
