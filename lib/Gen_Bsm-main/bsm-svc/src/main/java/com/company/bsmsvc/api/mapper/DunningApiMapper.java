package com.company.bsmsvc.api.mapper;

import com.company.bsmsvc.api.dto.response.DunningAttemptResponse;
import com.company.bsmsvc.api.dto.response.DunningStatusResponse;
import com.company.bsmsvc.domain.model.DunningAttempt;
import com.company.bsmsvc.domain.model.Subscription;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DunningApiMapper {

    public DunningAttemptResponse toAttemptResponse(DunningAttempt a) {
        return new DunningAttemptResponse(
            a.getId(), a.getSubscriptionId(), a.getInvoiceId(), a.getAttemptNumber(),
            a.getStatus(), a.getFailureCode(), a.getFailureMessage(), a.getExternalPaymentId(),
            a.getNextRetryAt(), a.getAttemptedAt(), a.getCreatedAt()
        );
    }

    public DunningStatusResponse toDunningStatus(Subscription sub, List<DunningAttempt> attempts) {
        return new DunningStatusResponse(
            sub.getId(), sub.getTenantId(), sub.getDunningStatus(),
            sub.getDunningStartedAt(), sub.getDunningNextActionAt(),
            attempts.size(),
            attempts.stream().map(this::toAttemptResponse).toList()
        );
    }
}
