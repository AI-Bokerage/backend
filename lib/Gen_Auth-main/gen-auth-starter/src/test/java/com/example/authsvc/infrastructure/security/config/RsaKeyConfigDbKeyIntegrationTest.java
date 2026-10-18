package com.example.authsvc.infrastructure.security.config;

import com.example.authsvc.config.properties.AwsProperties;
import com.example.authsvc.config.properties.JwtProperties;
import com.example.authsvc.infrastructure.persistence.entity.JwtActiveSigningKeyEntity;
import com.example.authsvc.infrastructure.persistence.entity.JwtSigningKeyEntity;
import com.example.authsvc.infrastructure.persistence.repository.JwtActiveSigningKeyJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.JwtSigningKeyJpaRepository;
import com.example.authsvc.infrastructure.security.jwt.jwks.JwtKeyRegistry;
import com.example.authsvc.infrastructure.security.jwt.jwks.KeyEncryptionUtil;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Proves the exact round-trip that the manual verification pass (README, 2026-07-15)
 * caught a real bug in: a rotated key persisted to a real Postgres instance (encrypted
 * private key + active-kid row) is decrypted, PKCS8/X509-reconstructed, keypair-validated,
 * and merged into {@link JwtKeyRegistry} at boot with the DB-tracked kid promoted active —
 * without touching {@code JwtKeyRotationService} or mocking the JPA repositories.
 *
 * <p>First use of Testcontainers in this suite. Deliberately bypasses
 * {@code @SpringBootTest}/{@code @DataJpaTest}: this project's global test
 * {@code src/test/resources/application.yaml} disables DataSource/JPA/Flyway
 * autoconfiguration for every other test (no live infra needed), and re-enabling just
 * those three for one test class via the full Spring Boot test-slice machinery pulls in
 * a lot of unrelated autoconfiguration (Redis/RabbitMQ/OAuth2) that would then need
 * mocking for no benefit here. A hand-built {@link EntityManagerFactory} +
 * {@link JpaRepositoryFactory} against the same entities/migrations the app uses in
 * production is a smaller, more focused way to prove the same thing.
 *
 * <p>Container lifecycle is managed manually (no {@code @Testcontainers}/{@code @Container})
 * so that Docker availability can be checked with {@link Assumptions#assumeTrue} <em>before</em>
 * anything tries to start a container — a machine/CI runner with no reachable Docker skips this
 * class cleanly instead of failing the whole build.
 */
class RsaKeyConfigDbKeyIntegrationTest {

    private static PostgreSQLContainer<?> postgres;
    private static EntityManagerFactory   emf;

    @BeforeAll
    static void migrateSchemaAndBuildEntityManagerFactory() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available in this environment — skipping the Testcontainers-backed "
                        + "RsaKeyConfig DB-key integration test");

        postgres = new PostgreSQLContainer<>("postgres:15");
        postgres.start();

        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(postgres.getJdbcUrl());
        dataSource.setUsername(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");

        LocalContainerEntityManagerFactoryBean emfBean = new LocalContainerEntityManagerFactoryBean();
        emfBean.setDataSource((DataSource) dataSource);
        emfBean.setPackagesToScan("com.example.authsvc.infrastructure.persistence.entity");
        emfBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        Properties jpaProps = new Properties();
        // Validate against the real Flyway-applied schema rather than letting Hibernate
        // create it — this is exactly the check that caught the @Lob/BYTEA mismatch.
        jpaProps.setProperty("hibernate.hbm2ddl.auto", "validate");
        emfBean.setJpaProperties(jpaProps);
        emfBean.afterPropertiesSet();
        emf = emfBean.getObject();
    }

    @AfterAll
    static void tearDown() {
        if (emf != null) {
            emf.close();
        }
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void bootMergesDbRotatedKeyAndPromotesItActive() throws Exception {
        EntityManager em = emf.createEntityManager();
        JpaRepositoryFactory repositoryFactory = new JpaRepositoryFactory(em);
        JwtSigningKeyJpaRepository       signingKeyRepository =
                repositoryFactory.getRepository(JwtSigningKeyJpaRepository.class);
        JwtActiveSigningKeyJpaRepository activeKeyRepository =
                repositoryFactory.getRepository(JwtActiveSigningKeyJpaRepository.class);

        KeyEncryptionUtil encryptionUtil =
                new KeyEncryptionUtil(Base64.getEncoder().encodeToString(new byte[32]));

        KeyPair       rotatedPair = generateRsaKeyPair();
        RSAPrivateKey rotatedPriv = (RSAPrivateKey) rotatedPair.getPrivate();
        RSAPublicKey  rotatedPub  = (RSAPublicKey)  rotatedPair.getPublic();

        // Persist directly via the real repositories against real Postgres — the same
        // JwtSigningKeyEntity/JwtActiveSigningKeyEntity round-trip JwtKeyRotationService
        // performs, but exercised here independently of that service/its mocks.
        em.getTransaction().begin();
        signingKeyRepository.save(JwtSigningKeyEntity.builder()
                .kid("db-rotated-kid")
                .privateKeyCiphertext(encryptionUtil.encrypt(rotatedPriv.getEncoded()))
                .publicKeyPem(pem("PUBLIC KEY", rotatedPub.getEncoded()))
                .build());
        activeKeyRepository.save(new JwtActiveSigningKeyEntity((short) 1, "db-rotated-kid"));
        em.getTransaction().commit();

        // Force genuine SELECTs against Postgres rather than serving these rows back
        // out of the first-level (persistence-context) cache.
        em.clear();

        // YAML/legacy branch: one file-backed key, "auth-key-v1" — proves the DB key is
        // *additive* to (not a replacement for) the YAML-configured key, and that the DB
        // active-kid overrides jwt.active-kid/jwt.key-id.
        KeyPair yamlPair = generateRsaKeyPair();
        File privatePem = writeTempPem("PRIVATE KEY", yamlPair.getPrivate().getEncoded());
        File publicPem  = writeTempPem("PUBLIC KEY",  yamlPair.getPublic().getEncoded());

        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSigningMode("local");
        jwtProperties.setKeyId("auth-key-v1");
        jwtProperties.getRsa().setPrivateKeyPath("file:" + privatePem.getAbsolutePath());
        jwtProperties.getRsa().setPublicKeyPath("file:" + publicPem.getAbsolutePath());

        RsaKeyConfig rsaKeyConfig = new RsaKeyConfig(
                jwtProperties, new AwsProperties(), signingKeyRepository, activeKeyRepository, encryptionUtil);

        JwtKeyRegistry registry = rsaKeyConfig.jwtKeyRegistry(null);

        assertEquals("db-rotated-kid", registry.getActiveKid(),
                "DB active-kid row must override jwt.key-id/jwt.active-kid");
        assertEquals(2, registry.allEntries().size(), "YAML key + DB-rotated key must both be present");
        assertNotNull(registry.getPrivateKey("db-rotated-kid"));
        assertEquals(rotatedPub, registry.getPublicKey("db-rotated-kid"));
        assertNotNull(registry.getPrivateKey("auth-key-v1"), "YAML-sourced key must be untouched");

        em.close();
    }

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String pem(String type, byte[] der) {
        String base64 = Base64.getEncoder().encodeToString(der);
        StringBuilder sb = new StringBuilder("-----BEGIN " + type + "-----\n");
        for (int i = 0; i < base64.length(); i += 64) {
            sb.append(base64, i, Math.min(i + 64, base64.length())).append('\n');
        }
        return sb.append("-----END " + type + "-----\n").toString();
    }

    private static File writeTempPem(String type, byte[] der) throws Exception {
        File file = File.createTempFile("rsakeyconfig-test-", ".pem");
        file.deleteOnExit();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(pem(type, der));
        }
        return file;
    }
}
