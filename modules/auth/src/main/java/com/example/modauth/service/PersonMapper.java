package com.example.modauth.service;

import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import com.example.modauth.dto.PersonResponse;
import com.example.modauth.entity.ModAuthUserRoleEntity;
import com.example.modauth.repository.ModAuthUserLookupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Shared by Teams and People — a role row plus its auth_users email/active, in one display shape. */
@Component
@RequiredArgsConstructor
public class PersonMapper {

    private final ModAuthUserLookupRepository userLookupRepo;

    public PersonResponse toResponse(ModAuthUserRoleEntity roleRow) {
        AuthUserEntity user = userLookupRepo.findById(roleRow.getUserId()).orElse(null);
        return new PersonResponse(
                roleRow.getUserId(),
                roleRow.getName(),
                user != null ? user.getEmail() : null,
                roleRow.getRole(),
                roleRow.getTeamId(),
                roleRow.getTeamName(),
                user != null && user.isActive());
    }
}
