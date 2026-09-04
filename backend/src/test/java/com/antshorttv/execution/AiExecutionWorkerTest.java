package com.antshorttv.execution;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AiExecutionWorkerTest {

    @Test
    void closesLeaseAfterSuccessfulHandlerCompletion() {
        Fixture fixture = new Fixture();
        when(fixture.handler.execute(org.mockito.ArgumentMatchers.any()))
            .thenReturn(new AiExecutionHandlerResult("TEST_RESULT", 99L));

        fixture.worker.run(1L);

        verify(fixture.lease).assertOwned();
        verify(fixture.lease).close();
        verify(fixture.claims).markSucceeded(
            org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(2L),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.eq("TEST_RESULT"), org.mockito.ArgumentMatchers.eq(99L),
            org.mockito.ArgumentMatchers.any());
    }

    @Test
    void lostLeasePreventsBothSuccessAndFailurePublication() {
        Fixture fixture = new Fixture();
        when(fixture.handler.execute(org.mockito.ArgumentMatchers.any()))
            .thenReturn(new AiExecutionHandlerResult("TEST_RESULT", 99L));
        org.mockito.Mockito.doThrow(new AiExecutionClaimLostException(1L))
            .when(fixture.lease).assertOwned();

        assertThatThrownBy(() -> fixture.worker.run(1L))
            .isInstanceOf(AiExecutionClaimLostException.class);

        verify(fixture.claims, never()).markSucceeded(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(fixture.claims, never()).markFailed(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
        verify(fixture.lease).close();
    }

    @Test
    void selectsRetryPolicyFromTheActualFailure() {
        Fixture fixture = new Fixture();
        RuntimeException failure = new RuntimeException("deterministic");
        AiExecutionRetryPolicy none = AiExecutionRetryPolicy.none();
        when(fixture.handler.execute(org.mockito.ArgumentMatchers.any())).thenThrow(failure);
        when(fixture.handler.retryPolicy(failure)).thenReturn(none);

        fixture.worker.run(1L);

        verify(fixture.claims).markFailed(
            org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(2L),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("HANDLER_FAILURE"),
            org.mockito.ArgumentMatchers.eq("deterministic"), org.mockito.ArgumentMatchers.eq(none),
            org.mockito.ArgumentMatchers.any());
    }

    private static final class Fixture {
        private final AiExecutionService executions = mock(AiExecutionService.class);
        private final AiExecutionClaimService claims = mock(AiExecutionClaimService.class);
        private final AiExecutionHandlerRegistry registry = mock(AiExecutionHandlerRegistry.class);
        private final AiExecutionLeaseKeeper leases = mock(AiExecutionLeaseKeeper.class);
        private final AiExecutionLeaseKeeper.Lease lease = mock(AiExecutionLeaseKeeper.Lease.class);
        private final AiExecutionHandler handler = mock(AiExecutionHandler.class);
        private final AiExecutionWorker worker;

        private Fixture() {
            AiExecutionTaskEntity task = new AiExecutionTaskEntity();
            task.id = 1L;
            task.scene = "TEST";
            when(claims.claim(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(10))))
                .thenAnswer(invocation -> new AiExecutionClaim(
                    1L, 2L, invocation.getArgument(1), 1, "SUBMIT"));
            when(executions.requireTask(1L)).thenReturn(task);
            when(registry.require("TEST")).thenReturn(handler);
            when(leases.keepAlive(org.mockito.ArgumentMatchers.any())).thenReturn(lease);
            worker = new AiExecutionWorker(
                executions, claims, registry, leases, Duration.ofMinutes(10));
        }
    }
}
