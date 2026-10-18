package com.example.authsvc.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "jwt_active_signing_key")
public class JwtActiveSigningKeyEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private short id;

    @Column(name = "kid", nullable = false)
    private String kid;
}
