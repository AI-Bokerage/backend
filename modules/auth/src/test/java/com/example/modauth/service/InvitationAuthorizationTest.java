package com.example.modauth.service;

import com.example.modauth.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Locks in the "who can invite whom" hierarchy from the "Who uses the
 * platform" diagram: Super Admin -> Tenant Admin -> Team Lead -> Broker,
 * invites only ever flow one level down.
 */
class InvitationAuthorizationTest {

    @Test
    void superAdminCanOnlyInviteTenantAdmin() {
        InvitationServiceImpl.requireCanInvite(Role.SUPER_ADMIN, Role.TENANT_ADMIN);
        assertThatThrownBy(() -> InvitationServiceImpl.requireCanInvite(Role.SUPER_ADMIN, Role.TEAM_LEAD))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> InvitationServiceImpl.requireCanInvite(Role.SUPER_ADMIN, Role.BROKER))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void tenantAdminCanInviteTeamLeadOrBrokerOrAssistant() {
        InvitationServiceImpl.requireCanInvite(Role.TENANT_ADMIN, Role.TEAM_LEAD);
        InvitationServiceImpl.requireCanInvite(Role.TENANT_ADMIN, Role.BROKER);
        InvitationServiceImpl.requireCanInvite(Role.TENANT_ADMIN, Role.ASSISTANT);
        assertThatThrownBy(() -> InvitationServiceImpl.requireCanInvite(Role.TENANT_ADMIN, Role.TENANT_ADMIN))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void teamLeadCanOnlyInviteBroker() {
        InvitationServiceImpl.requireCanInvite(Role.TEAM_LEAD, Role.BROKER);
        assertThatThrownBy(() -> InvitationServiceImpl.requireCanInvite(Role.TEAM_LEAD, Role.TEAM_LEAD))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> InvitationServiceImpl.requireCanInvite(Role.TEAM_LEAD, Role.ASSISTANT))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void brokerCanInviteNoOne() {
        assertThatThrownBy(() -> InvitationServiceImpl.requireCanInvite(Role.BROKER, Role.BROKER))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void assistantCanInviteNoOne() {
        assertThatThrownBy(() -> InvitationServiceImpl.requireCanInvite(Role.ASSISTANT, Role.BROKER))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void dashboardKeyMatchesEachRole() {
        assertThat(Role.SUPER_ADMIN.dashboardKey()).isEqualTo("platform_console");
        assertThat(Role.TENANT_ADMIN.dashboardKey()).isEqualTo("brokerage_dashboard");
        assertThat(Role.TEAM_LEAD.dashboardKey()).isEqualTo("team_dashboard");
        assertThat(Role.BROKER.dashboardKey()).isEqualTo("broker_dashboard");
        assertThat(Role.ASSISTANT.dashboardKey()).isEqualTo("broker_dashboard");
    }
}
