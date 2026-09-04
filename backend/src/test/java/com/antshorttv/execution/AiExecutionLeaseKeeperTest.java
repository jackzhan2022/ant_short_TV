package com.antshorttv.execution;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiExecutionLeaseKeeperTest {

    @Test
    void renewsClaimAndCancelsHeartbeatWhenLeaseCloses() {
        AiExecutionClaimService claims = mock(AiExecutionClaimService.class);
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        ArgumentCaptor<Runnable> heartbeat = ArgumentCaptor.forClass(Runnable.class);
        doReturn(future).when(scheduler).scheduleAtFixedRate(
            heartbeat.capture(), eq(Duration.ofMinutes(1).toNanos()),
            eq(Duration.ofMinutes(1).toNanos()), eq(TimeUnit.NANOSECONDS));
        when(claims.heartbeat(eq(41L), eq("claim-41"), any(), eq(Duration.ofMinutes(10))))
            .thenReturn(true);
        AiExecutionLeaseKeeper keeper = new AiExecutionLeaseKeeper(
            claims, scheduler, Duration.ofMinutes(10), Duration.ofMinutes(1));

        AiExecutionLeaseKeeper.Lease lease = keeper.keepAlive(
            new AiExecutionClaim(41L, 42L, "claim-41", 1, "SUBMIT"));
        heartbeat.getValue().run();
        lease.assertOwned();
        lease.close();

        verify(claims).heartbeat(eq(41L), eq("claim-41"), any(), eq(Duration.ofMinutes(10)));
        verify(future).cancel(false);
    }

    @Test
    void marksLeaseLostWhenHeartbeatCannotRenewClaim() {
        AiExecutionClaimService claims = mock(AiExecutionClaimService.class);
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        ArgumentCaptor<Runnable> heartbeat = ArgumentCaptor.forClass(Runnable.class);
        doReturn(future).when(scheduler).scheduleAtFixedRate(
            heartbeat.capture(), eq(Duration.ofMillis(100).toNanos()),
            eq(Duration.ofMillis(100).toNanos()), eq(TimeUnit.NANOSECONDS));
        when(claims.heartbeat(eq(51L), eq("claim-51"), any(), eq(Duration.ofSeconds(10))))
            .thenReturn(false);
        AiExecutionLeaseKeeper keeper = new AiExecutionLeaseKeeper(
            claims, scheduler, Duration.ofSeconds(10), Duration.ofMillis(100));

        AiExecutionLeaseKeeper.Lease lease = keeper.keepAlive(
            new AiExecutionClaim(51L, 52L, "claim-51", 1, "SUBMIT"));
        heartbeat.getValue().run();

        assertThatThrownBy(lease::assertOwned)
            .isInstanceOf(AiExecutionClaimLostException.class);
        verify(future).cancel(false);
    }

    @Test
    void rejectsHeartbeatIntervalsThatCannotRenewBeforeExpiry() {
        AiExecutionClaimService claims = mock(AiExecutionClaimService.class);
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);

        assertThatThrownBy(() -> new AiExecutionLeaseKeeper(
            claims, scheduler, Duration.ofMinutes(10), Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiExecutionLeaseKeeper(
            claims, scheduler, Duration.ofMinutes(10), Duration.ofMinutes(10)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
