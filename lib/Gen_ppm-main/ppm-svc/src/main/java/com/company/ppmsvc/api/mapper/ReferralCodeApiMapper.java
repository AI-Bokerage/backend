package com.company.ppmsvc.api.mapper;

import com.company.ppmsvc.api.dto.response.ReferralCodeResponse;
import com.company.ppmsvc.referral.model.ReferralCode;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReferralCodeApiMapper {

    ReferralCodeResponse toResponse(ReferralCode code);

    List<ReferralCodeResponse> toResponseList(List<ReferralCode> codes);
}
