package com.antshorttv.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;

class AiExecutionDispatcherAdmissionTest {
    private final AiExecutionTaskMapper tasks = mock(AiExecutionTaskMapper.class);
    private final AiExecutionClaimService claims = mock(AiExecutionClaimService.class);
    private final AiExecutionWorker worker = mock(AiExecutionWorker.class);
    private final AiExecutionPools pools = mock(AiExecutionPools.class);
    private final AiExecutionDispatcher dispatcher = new AiExecutionDispatcher(tasks, claims, worker, pools, 20,
        Duration.ofMinutes(10));

    @Test
    void fullLocalPoolDoesNotClaimDatabaseTask() {
        candidates();
        assertThat(dispatcher.dispatchOnce()).isZero();
        verify(claims, never()).claim(anyLong(), anyString(), any(), any());
    }

    @Test
    void reservesThenClaimsThenHandsOffAndRejectionDefersWithoutFailure() {
        candidates();
        var slot = mock(AiExecutionPools.Slot.class);
        var claim = new AiExecutionClaim(42L, 43L, "token", 1, "SUBMIT");
        when(pools.tryAcquire(AiExecutionPoolType.TEXT)).thenReturn(slot);
        when(claims.claim(eq(42L), anyString(), any(), any())).thenReturn(claim);
        doThrow(new RejectedExecutionException()).when(slot).submit(any());
        assertThat(dispatcher.dispatchOnce()).isZero();
        var order = inOrder(pools, claims, slot);
        order.verify(claims).recoverExpiredClaims(any());
        order.verify(pools).tryAcquire(AiExecutionPoolType.TEXT);
        order.verify(claims).claim(eq(42L), anyString(), any(), any());
        order.verify(slot).submit(any());
        order.verify(claims).defer(eq(claim), anyString(), any());
        verify(slot).close();
        verifyNoInteractions(worker);
    }

    @Test
    void eachSweepKeepsItsUpperBoundUntilReturningToSkippedTasks() {
        var first = candidate(1L);
        var second = candidate(2L);
        var upper = candidate(2L);
        var laterUpper = candidate(1000L);
        var textSnapshots = new java.util.concurrent.atomic.AtomicInteger();
        when(tasks.selectOne(any())).thenAnswer(invocation -> {
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AiExecutionTaskEntity> query = invocation.getArgument(0);
            query.getSqlSegment();
            if (query.getParamNameValuePairs().containsValue("TEXT")) {
                return textSnapshots.incrementAndGet() == 1 ? upper : laterUpper;
            }
            return null;
        });
        var textPages = new java.util.concurrent.atomic.AtomicInteger();
        when(tasks.selectList(any())).thenAnswer(invocation -> {
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AiExecutionTaskEntity> query = invocation.getArgument(0);
            query.getSqlSegment();
            if (!query.getParamNameValuePairs().containsValue("TEXT")) return List.of();
            return switch(textPages.getAndIncrement()) {
                case 0, 3 -> List.of(first);
                case 1 -> List.of(second);
                default -> List.of();
            };
        });
        when(pools.tryAcquire(AiExecutionPoolType.TEXT)).thenAnswer(ignored -> mock(AiExecutionPools.Slot.class));
        dispatcher.dispatchOnce();
        dispatcher.dispatchOnce();
        assertThat(textSnapshots.get()).isEqualTo(1);
        dispatcher.dispatchOnce();
        assertThat(textSnapshots.get()).isEqualTo(2);
        verify(claims, times(2)).claim(eq(1L), anyString(), any(), any());
        verify(claims).claim(eq(2L), anyString(), any(), any());
    }

    private void candidates() {
        var task = candidate(42L);
        when(tasks.selectList(any())).thenReturn(List.of(task), List.of());
    }

    private AiExecutionTaskEntity candidate(long id) {
        var task = new AiExecutionTaskEntity();
        task.id = id;
        task.priority = 100;
        task.createdAt = LocalDateTime.now();
        return task;
    }
}
