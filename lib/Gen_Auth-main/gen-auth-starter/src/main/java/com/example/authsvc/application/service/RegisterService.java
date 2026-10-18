package com.example.authsvc.application.service;

import com.example.authsvc.domain.enums.UserType;

import java.util.UUID;

public interface RegisterService {

    /**
     * Creates a new user.
     *
     * @param email    must be unique (case as submitted — matches existing auth_users.email uniqueness)
     * @param password raw password, hashed before storage
     * @param tenantId nullable — null resolves to {@code TenantConstants.PLATFORM_TENANT_ID}
     * @param roleId   nullable — only ever non-null when called from the internal admin-provisioning endpoint
     * @param id       nullable — null generates a random id; if a user with this id already exists,
     *                 creation is a no-op and that user's id is returned (idempotent under event
     *                 redelivery from an upstream system)
     * @param userType nullable — null resolves to {@link UserType#TENANT_USER}
     * @return the user's id (new, or the existing one when {@code id} already exists)
     * @throws com.example.authsvc.common.exception.EmailAlreadyExistsException if the email is already
     *         registered under a different id
     */
    UUID register(String email, String password, UUID tenantId, UUID roleId, UUID id, UserType userType);
}
