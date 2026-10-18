package com.example.authsvc.infrastructure.persistence.repository;

import com.example.authsvc.infrastructure.persistence.entity.AuthUserOAuthIdentityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthUserOAuthIdentityJpaRepository extends JpaRepository<AuthUserOAuthIdentityEntity, UUID> {

    Optional<AuthUserOAuthIdentityEntity> findByProviderAndProviderSubject(String provider, String providerSubject);
}
