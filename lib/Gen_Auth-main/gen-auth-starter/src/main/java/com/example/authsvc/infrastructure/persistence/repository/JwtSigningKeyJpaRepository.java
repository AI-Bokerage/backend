package com.example.authsvc.infrastructure.persistence.repository;

import com.example.authsvc.infrastructure.persistence.entity.JwtSigningKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JwtSigningKeyJpaRepository extends JpaRepository<JwtSigningKeyEntity, String> {
}
