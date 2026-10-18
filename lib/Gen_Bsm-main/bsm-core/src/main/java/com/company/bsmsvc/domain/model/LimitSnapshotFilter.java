package com.company.bsmsvc.domain.model;

import java.util.UUID;

public record LimitSnapshotFilter(
    UUID subscriptionId,
    Boolean overLimit
) {
}
