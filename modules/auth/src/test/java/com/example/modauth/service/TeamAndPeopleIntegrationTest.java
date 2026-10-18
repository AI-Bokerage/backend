package com.example.modauth.service;

import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthRefreshTokenJpaRepository;
import com.example.authsvc.infrastructure.security.principal.AuthenticatedUser;
import com.example.modauth.domain.Role;
import com.example.modauth.dto.CreateTeamRequest;
import com.example.modauth.dto.PersonResponse;
import com.example.modauth.dto.TeamResponse;
import com.example.modauth.entity.ModAuthUserRoleEntity;
import com.example.modauth.repository.ModAuthUserLookupRepository;
import com.example.modauth.repository.ModAuthUserRoleJpaRepository;
import com.example.modauth.repository.TeamJpaRepository;
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
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the "People"/"Teams" flow diagrams against a real Postgres — both
 * Flyway migration sets (gen-auth-starter's {@code genauth} and this
 * module's own {@code modauth}, see ModAuthFlywayInitializer), real JPA
 * repositories, and the real service classes. The Mockito-based
 * TeamServiceImplTest/PeopleServiceImplTest already cover every branch in
 * isolation — this proves the V1-V3 migrations and entity mappings those
 * mocks can't: real column types, real constraints, a real round trip.
 *
 * <p>Follows the same manual-lifecycle Testcontainers convention as
 * gen-auth-starter's own AuthOutboxRelayJobIntegrationTest, not
 * {@code @Testcontainers}/{@code @Container} — no Spring context, so each
 * write is wrapped in its own explicit transaction.
 */
class TeamAndPeopleIntegrationTest {

    private static PostgreSQLContainer<?> postgres;
    private static EntityManagerFactory emf;

    private EntityManager em;
    private ModAuthUserRoleJpaRepository roleRepo;
    private TeamJpaRepository teamRepo;
    private ModAuthUserLookupRepository userLookupRepo;
    private AuthRefreshTokenJpaRepository refreshTokenRepo;
    private TeamServiceImpl teamService;
    private PeopleServiceImpl peopleService;
    private final UUID tenantId = UUID.randomUUID();
    private AuthenticatedUser admin;

    @BeforeAll
    static void migrateSchemaAndBuildEntityManagerFactory() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available in this environment — skipping the Team/People integration test");

        postgres = new PostgreSQLContainer<>("postgres:16");
        postgres.start();

        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .table("flyway_schema_history_auth")
                .baselineOnMigrate(true).baselineVersion("0").validateOnMigrate(true)
                .locations("classpath:db/migration/genauth")
                .load()
                .migrate();
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .baselineOnMigrate(true).baselineVersion("0").validateOnMigrate(true)
                .locations("classpath:db/migration/modauth")
                .load()
                .migrate();

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(postgres.getJdbcUrl());
        dataSource.setUsername(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setMaximumPoolSize(4);

        LocalContainerEntityManagerFactoryBean emfBean = new LocalContainerEntityManagerFactoryBean();
        emfBean.setDataSource((DataSource) dataSource);
        emfBean.setPackagesToScan(
                "com.example.authsvc.infrastructure.persistence.entity",
                "com.example.modauth.entity");
        emfBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        Properties jpaProps = new Properties();
        jpaProps.setProperty("hibernate.hbm2ddl.auto", "validate");
        emfBean.setJpaProperties(jpaProps);
        emfBean.afterPropertiesSet();
        emf = emfBean.getObject();
    }

    @AfterAll
    static void tearDown() {
        if (emf != null) { emf.close(); }
        if (postgres != null) { postgres.stop(); }
    }

    private <T> T inTransaction(Supplier<T> work) {
        em.getTransaction().begin();
        try {
            T result = work.get();
            em.getTransaction().commit();
            return result;
        } catch (RuntimeException e) {
            em.getTransaction().rollback();
            throw e;
        }
    }

    private void setUpForTest() {
        Assumptions.assumeTrue(emf != null, "Testcontainers Postgres unavailable");
        em = emf.createEntityManager();
        JpaRepositoryFactory factory = new JpaRepositoryFactory(em);
        roleRepo = factory.getRepository(ModAuthUserRoleJpaRepository.class);
        teamRepo = factory.getRepository(TeamJpaRepository.class);
        userLookupRepo = factory.getRepository(ModAuthUserLookupRepository.class);
        refreshTokenRepo = factory.getRepository(AuthRefreshTokenJpaRepository.class);
        RoleResolver roleResolver = new RoleResolver(roleRepo);
        PersonMapper personMapper = new PersonMapper(userLookupRepo);
        teamService = new TeamServiceImpl(teamRepo, roleRepo, roleResolver, personMapper);
        // No Lead model exists on this Postgres — this test is about the
        // People/Teams round trip, not the cross-service CRM check, which
        // TeamAndPeopleIntegrationTest's Mockito-based siblings already cover.
        CrmClient noAssignedClients = new CrmClient() {
            @Override
            public int activeAssignedLeadCount(UUID tenantId, UUID userId) {
                return 0;
            }

            @Override
            public boolean isBrokerageProfileComplete(UUID tenantId) {
                return true;
            }
        };
        peopleService = new PeopleServiceImpl(roleRepo, userLookupRepo, refreshTokenRepo, roleResolver, personMapper, noAssignedClients);
        admin = new AuthenticatedUser(UUID.randomUUID(), tenantId, "acme", List.of(), UserType.TENANT_USER,
                "session", null, "jti");
    }

    private AuthUserEntity seedActiveUser() {
        AuthUserEntity user = AuthUserEntity.builder()
                .id(UUID.randomUUID()).tenantId(tenantId)
                .email("person-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hash").userType(UserType.TENANT_USER).active(true)
                .build();
        return inTransaction(() -> userLookupRepo.save(user));
    }

    private ModAuthUserRoleEntity seedRole(UUID userId, Role role, String name) {
        ModAuthUserRoleEntity row = ModAuthUserRoleEntity.builder()
                .userId(userId).tenantId(tenantId).role(role).name(name).acceptedTermsVersion(1)
                .build();
        return inTransaction(() -> roleRepo.save(row));
    }

    @Test
    void fullPeopleAndTeamsLifecycleAgainstRealPostgres() {
        setUpForTest();
        seedRole(admin.getUserId(), Role.TENANT_ADMIN, "Priya Admin");

        AuthUserEntity leadUser = seedActiveUser();
        seedRole(leadUser.getId(), Role.TEAM_LEAD, "Leah Lead");
        AuthUserEntity brokerUser = seedActiveUser();
        seedRole(brokerUser.getId(), Role.BROKER, "Bob Broker");

        // "People" list shows both, no team yet.
        List<PersonResponse> people = inTransaction(() -> peopleService.list(admin));
        assertThat(people).extracting(PersonResponse::name).containsExactlyInAnyOrder("Leah Lead", "Bob Broker");
        assertThat(people).allSatisfy(p -> assertThat(p.teamId()).isNull());

        // Create a team with the lead + initial broker in one call — real FK-by-
        // convention columns, real defaults (created_at), real round trip.
        TeamResponse team = inTransaction(() -> teamService.create(
                admin, new CreateTeamRequest("North Region", leadUser.getId(), List.of(brokerUser.getId()))));
        assertThat(team.teamLead().userId()).isEqualTo(leadUser.getId());
        assertThat(team.members()).extracting(PersonResponse::userId).containsExactly(brokerUser.getId());

        // Delete blocked while members exist — real query against modauth_user_roles.team_id.
        assertThatThrownBy(() -> inTransaction(() -> { teamService.delete(admin, team.id()); return null; }))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Remove all members");

        // Remove the broker — keeps their role row, just clears team_id.
        inTransaction(() -> teamService.removeMember(admin, team.id(), brokerUser.getId()));
        ModAuthUserRoleEntity brokerAfterRemoval = inTransaction(() -> roleRepo.findById(brokerUser.getId()).orElseThrow());
        assertThat(brokerAfterRemoval.getTeamId()).isNull();
        assertThat(brokerAfterRemoval.getRole()).isEqualTo(Role.BROKER);

        // Deactivate the broker — real UPDATE on auth_users.active, real refresh-token revocation query.
        inTransaction(() -> { peopleService.deactivate(admin, brokerUser.getId()); return null; });
        AuthUserEntity brokerAfterDeactivate = inTransaction(() -> userLookupRepo.findById(brokerUser.getId()).orElseThrow());
        assertThat(brokerAfterDeactivate.isActive()).isFalse();

        // Team now has only the lead as a member — delete still blocked.
        assertThatThrownBy(() -> inTransaction(() -> { teamService.delete(admin, team.id()); return null; }))
                .isInstanceOf(ResponseStatusException.class);

        // Remove the lead via the member-remove path is refused; delete the team must be used instead.
        assertThatThrownBy(() -> inTransaction(() -> teamService.removeMember(admin, team.id(), leadUser.getId())))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("delete the team instead");
    }
}
