package com.example.authsvc.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "jwt_signing_key")
public class JwtSigningKeyEntity {

    @Id
    @Column(name = "kid", updatable = false, nullable = false)
    private String kid;

    // ponytail: no @Lob — Hibernate 6/7 maps @Lob byte[] to BLOB (Postgres OID),
    // but the V3 migration column is BYTEA. Plain byte[] maps to VARBINARY/BYTEA,
    // matching the migration. (Found via real Postgres schema validation, not
    // caught by unit tests that mock this repository.)
    @Column(name = "private_key_ciphertext", nullable = false)
    private byte[] privateKeyCiphertext;

    @Column(name = "public_key_pem", nullable = false, columnDefinition = "TEXT")
    private String publicKeyPem;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
