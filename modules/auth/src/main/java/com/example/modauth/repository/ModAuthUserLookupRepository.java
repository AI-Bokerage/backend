package com.example.modauth.repository;

import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * A second repository over gen-auth-starter's own {@code AuthUserEntity} /
 * {@code auth_users} table. gen-auth-starter's {@code AuthUserJpaRepository}
 * only exposes {@code findByEmailAndActiveTrue} (deliberately — login must
 * never distinguish "no such user" from "inactive user" before the password
 * check, to avoid account enumeration). The login flow this module adds
 * needs to tell those two apart, but only *after* the password has already
 * been verified — see {@code ModAuthLoginServiceImpl} — so a plain
 * {@code findByEmail} is safe to add here without touching the starter.
 */
@Repository
public interface ModAuthUserLookupRepository extends JpaRepository<AuthUserEntity, UUID> {

    Optional<AuthUserEntity> findByEmail(String email);
}
