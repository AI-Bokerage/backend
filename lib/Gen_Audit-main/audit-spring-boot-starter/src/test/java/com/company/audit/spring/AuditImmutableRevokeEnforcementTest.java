package com.company.audit.spring;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.audit.spring.support.AbstractPostgresIntegrationTest;
import com.company.audit.spring.support.TestApplication;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Proves that {@code REVOKE UPDATE, DELETE ON audit_immutable FROM PUBLIC} (from {@code V001})
 * is actually enforced by Postgres.
 *
 * <p>This must run as a dedicated non-superuser role. Superusers bypass all {@code REVOKE}
 * rules, so testing as the Testcontainers image's default superuser would produce a false pass
 * that proves nothing about the migration's actual effect.
 */
@SpringBootTest(classes = TestApplication.class)
class AuditImmutableRevokeEnforcementTest extends AbstractPostgresIntegrationTest {

    @Test
    void nonSuperuserRoleCannotUpdateOrDeleteAuditImmutableRows() throws Exception {
        String role = "audit_restricted_" + UUID.randomUUID().toString().replace("-", "");
        String password = "test-password";

        try (Connection admin = adminConnection()) {
            try (Statement statement = admin.createStatement()) {
                statement.execute("CREATE ROLE " + role + " LOGIN PASSWORD '" + password + "'");
                statement.execute("GRANT INSERT, SELECT ON audit_immutable TO " + role);
            }

            try (Connection restricted = DriverManager.getConnection(POSTGRES.getJdbcUrl(), role, password)) {
                assertThatThrownBy(() -> {
                    try (Statement statement = restricted.createStatement()) {
                        statement.execute("UPDATE audit_immutable SET event_type = 'HACKED' WHERE true");
                    }
                }).isInstanceOf(SQLException.class).hasMessageContaining("permission denied");

                assertThatThrownBy(() -> {
                    try (Statement statement = restricted.createStatement()) {
                        statement.execute("DELETE FROM audit_immutable WHERE true");
                    }
                }).isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
            } finally {
                try (Statement statement = admin.createStatement()) {
                    statement.execute("REVOKE ALL ON audit_immutable FROM " + role);
                    statement.execute("DROP ROLE " + role);
                }
            }
        }
    }

    private Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
