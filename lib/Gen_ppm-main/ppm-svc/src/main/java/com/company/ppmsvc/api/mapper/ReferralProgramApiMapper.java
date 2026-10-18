package com.company.ppmsvc.api.mapper;

import com.company.ppmsvc.api.dto.response.ReferralProgramResponse;
import com.company.ppmsvc.referral.model.ReferralProgram;
import java.util.List;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReferralProgramApiMapper {

    ReferralProgramResponse toResponse(ReferralProgram program);

    List<ReferralProgramResponse> toResponseList(List<ReferralProgram> programs);
}
