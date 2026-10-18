package com.example.modauth.controller;

import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import com.example.authsvc.infrastructure.security.principal.AuthenticatedUser;
import com.example.modauth.dto.MyProfileResponse;
import com.example.modauth.dto.UpdateMyProfileRequest;
import com.example.modauth.entity.ModAuthUserRoleEntity;
import com.example.modauth.repository.ModAuthUserLookupRepository;
import com.example.modauth.repository.ModAuthUserRoleJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Flow 4.12 Settings → "My profile". Password and devices have their own endpoints. */
@Tag(name = "My profile", description = "The signed-in person's own name and phone")
@RestController
@RequestMapping("/api/v1/modauth/me")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class MyProfileController {

    private final ModAuthUserRoleJpaRepository roleRepo;
    private final ModAuthUserLookupRepository userLookupRepo;

    @Operation(summary = "Get my profile")
    @GetMapping
    public MyProfileResponse get(@AuthenticationPrincipal AuthenticatedUser caller) {
        return toResponse(requireRow(caller));
    }

    @Operation(summary = "Update my name and/or phone")
    @PatchMapping
    @Transactional
    public MyProfileResponse update(@AuthenticationPrincipal AuthenticatedUser caller,
                                    @Valid @RequestBody UpdateMyProfileRequest request) {
        ModAuthUserRoleEntity row = requireRow(caller);
        if (request.name() != null) row.setName(request.name().strip());
        if (request.phone() != null) row.setPhone(request.phone().isBlank() ? null : request.phone().strip());
        return toResponse(roleRepo.save(row));
    }

    private ModAuthUserRoleEntity requireRow(AuthenticatedUser caller) {
        if (caller == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in first");
        }
        return roleRepo.findById(caller.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No profile for this account"));
    }

    private MyProfileResponse toResponse(ModAuthUserRoleEntity row) {
        String email = userLookupRepo.findById(row.getUserId()).map(AuthUserEntity::getEmail).orElse(null);
        return new MyProfileResponse(row.getUserId(), email, row.getName(), row.getPhone(), row.getRole(),
                row.getTeamId(), row.getTeamName());
    }
}
