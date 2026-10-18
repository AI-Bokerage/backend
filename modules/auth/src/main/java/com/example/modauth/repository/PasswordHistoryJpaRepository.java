package com.example.modauth.repository;

import com.example.modauth.entity.PasswordHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PasswordHistoryJpaRepository extends JpaRepository<PasswordHistoryEntity, UUID> {

    List<PasswordHistoryEntity> findTop5ByUserIdOrderByCreatedAtDesc(UUID userId);
}
