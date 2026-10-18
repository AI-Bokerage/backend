package com.company.ppmsvc.infrastructure.persistence.mapper;

import com.company.ppmsvc.infrastructure.persistence.entity.ReferralProgramEntity;
import com.company.ppmsvc.referral.model.ReferralProgram;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** MapStruct mapper between {@link ReferralProgram} and {@link ReferralProgramEntity}. No JSONB — plain interface. */
@Mapper(componentModel = "spring")
public interface ReferralProgramPersistenceMapper {

    @Mapping(target = "status", expression = "java(com.company.ppmsvc.promotion.model.ReferralProgramStatus.fromValue(entity.getStatus()))")
    ReferralProgram toDomain(ReferralProgramEntity entity);

    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "status", expression = "java(program.getStatus().getValue())")
    ReferralProgramEntity toEntity(ReferralProgram program);

    List<ReferralProgram> toDomainList(List<ReferralProgramEntity> entities);
}
