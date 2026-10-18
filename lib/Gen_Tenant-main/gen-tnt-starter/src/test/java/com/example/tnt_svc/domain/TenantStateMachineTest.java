package com.example.tnt_svc.domain;

import com.example.tnt_svc.domain.exception.InvalidTenantStateTransitionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantStateMachineTest {

    @Test
    void allowsProvisioningToActive() {
        assertThat(TenantStateMachine.isTransitionAllowed(TenantStatus.PROVISIONING, TenantStatus.ACTIVE)).isTrue();
    }

    @Test
    void allowsProvisioningToCancelled() {
        assertThat(TenantStateMachine.isTransitionAllowed(TenantStatus.PROVISIONING, TenantStatus.CANCELLED)).isTrue();
    }

    @Test
    void allowsActiveToSuspendedAndBack() {
        assertThat(TenantStateMachine.isTransitionAllowed(TenantStatus.ACTIVE, TenantStatus.SUSPENDED)).isTrue();
        assertThat(TenantStateMachine.isTransitionAllowed(TenantStatus.SUSPENDED, TenantStatus.ACTIVE)).isTrue();
    }

    @Test
    void allowsCancelledToPurged() {
        assertThat(TenantStateMachine.isTransitionAllowed(TenantStatus.CANCELLED, TenantStatus.PURGED)).isTrue();
    }

    @Test
    void rejectsAnyTransitionOutOfPurged() {
        assertThat(TenantStateMachine.isTransitionAllowed(TenantStatus.PURGED, TenantStatus.ACTIVE)).isFalse();
    }

    @Test
    void rejectsSkippingStraightToSuspendedFromProvisioning() {
        assertThat(TenantStateMachine.isTransitionAllowed(TenantStatus.PROVISIONING, TenantStatus.SUSPENDED)).isFalse();
    }

    @Test
    void validateTransitionThrowsOnIllegalTransition() {
        assertThatThrownBy(() -> TenantStateMachine.validateTransition(TenantStatus.PURGED, TenantStatus.ACTIVE))
            .isInstanceOf(InvalidTenantStateTransitionException.class)
            .hasMessageContaining("PURGED")
            .hasMessageContaining("ACTIVE");
    }

    @Test
    void validateTransitionDoesNotThrowOnLegalTransition() {
        TenantStateMachine.validateTransition(TenantStatus.ACTIVE, TenantStatus.CANCELLED);
    }
}
