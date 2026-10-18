package com.company.bsmsvc.api.mapper;

import com.company.bsmsvc.api.dto.response.SubscriptionEventResponse;
import com.company.bsmsvc.api.dto.response.SubscriptionHistoryResponse;
import com.company.bsmsvc.api.dto.response.SubscriptionScheduleResponse;
import com.company.bsmsvc.domain.model.SubscriptionEvent;
import com.company.bsmsvc.domain.model.SubscriptionHistory;
import com.company.bsmsvc.domain.model.SubscriptionSchedule;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SubscriptionQueryApiMapper {

    SubscriptionEventResponse toResponse(SubscriptionEvent event);

    SubscriptionHistoryResponse toResponse(SubscriptionHistory history);

    SubscriptionScheduleResponse toResponse(SubscriptionSchedule schedule);
}
