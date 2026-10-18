package com.example.authsvc.infrastructure.persistence.repository;

import com.example.authsvc.infrastructure.persistence.entity.JwtActiveSigningKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JwtActiveSigningKeyJpaRepository extends JpaRepository<JwtActiveSigningKeyEntity, Short> {

    /** The one row (id=1) tracking the current DB-sourced active signing kid, if any. */
    default Optional<JwtActiveSigningKeyEntity> findSingleton() {
        return findById((short) 1);
    }
}
