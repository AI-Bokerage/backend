package com.company.bsmsvc.infrastructure.external.adm;

import com.company.bsmsvc.domain.exception.UsageDataUnavailableException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdmUserUsageAdapterTest {

    @Mock  private AdmSvcClient admSvcClient;
    @InjectMocks private AdmUserUsageAdapter adapter;

    private static final UUID TENANT = UUID.randomUUID();

    private AdmUsageMetricsResponse response(long activeInternal, long activeClient) {
        return new AdmUsageMetricsResponse(TENANT, activeInternal, activeInternal + 2,
            activeClient, activeClient + 5);
    }

    @Test
    void getActiveInternalUserCount_returnsAdmValue() {
        when(admSvcClient.getUsageMetrics(TENANT)).thenReturn(response(12, 45));
        assertThat(adapter.getActiveInternalUserCount(TENANT)).isEqualTo(12);
    }

    @Test
    void getActiveClientUserCount_returnsAdmValue() {
        when(admSvcClient.getUsageMetrics(TENANT)).thenReturn(response(12, 45));
        assertThat(adapter.getActiveClientUserCount(TENANT)).isEqualTo(45);
    }

    @Test
    void getActiveInternalUserCount_whenAdmThrows_propagatesUnavailableException() {
        when(admSvcClient.getUsageMetrics(TENANT))
            .thenThrow(new UsageDataUnavailableException("ADM-SVC", "connection refused"));
        assertThatThrownBy(() -> adapter.getActiveInternalUserCount(TENANT))
            .isInstanceOf(UsageDataUnavailableException.class)
            .hasMessageContaining("ADM-SVC");
    }

    @Test
    void getActiveClientUserCount_whenAdmThrows_propagatesUnavailableException() {
        when(admSvcClient.getUsageMetrics(TENANT))
            .thenThrow(new UsageDataUnavailableException("ADM-SVC", "timeout"));
        assertThatThrownBy(() -> adapter.getActiveClientUserCount(TENANT))
            .isInstanceOf(UsageDataUnavailableException.class);
    }

    @Test
    void getActiveInternalUserCount_capsAtIntegerMax() {
        long huge = (long) Integer.MAX_VALUE + 1_000;
        when(admSvcClient.getUsageMetrics(TENANT))
            .thenReturn(new AdmUsageMetricsResponse(TENANT, huge, huge, 0, 0));
        assertThat(adapter.getActiveInternalUserCount(TENANT)).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void getActiveClientUserCount_zeroIsValid() {
        when(admSvcClient.getUsageMetrics(TENANT)).thenReturn(response(0, 0));
        assertThat(adapter.getActiveClientUserCount(TENANT)).isZero();
    }
}
