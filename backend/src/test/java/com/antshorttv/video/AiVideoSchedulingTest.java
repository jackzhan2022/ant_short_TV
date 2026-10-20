package com.antshorttv.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.antshorttv.execution.AiExecutionClaim;
import com.antshorttv.execution.AiExecutionClaimService;
import com.antshorttv.execution.AiExecutionPoolType;
import com.antshorttv.execution.AiExecutionPools;
import com.antshorttv.execution.AiExecutionTaskMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class AiVideoSchedulingTest {
    private final AiVideoTaskService service = mock(AiVideoTaskService.class, CALLS_REAL_METHODS);
    private final AiVideoTaskMapper tasks = mock(AiVideoTaskMapper.class);
    private final AiExecutionTaskMapper executions = mock(AiExecutionTaskMapper.class);
    private final AiExecutionClaimService claims = mock(AiExecutionClaimService.class);
    private final AiExecutionPools pools = mock(AiExecutionPools.class);
    private final AiTaskExecutionSupport domains = mock(AiTaskExecutionSupport.class);
    private final AiExecutionPools.Slot slot = mock(AiExecutionPools.Slot.class);
    private final AiVideoTaskEntity task = new AiVideoTaskEntity();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "aiVideoTaskMapper", tasks);
        ReflectionTestUtils.setField(service, "executionTaskMapper", executions);
        ReflectionTestUtils.setField(service, "claimService", claims);
        ReflectionTestUtils.setField(service, "executionPools", pools);
        ReflectionTestUtils.setField(service, "executionSupport", domains);
        ReflectionTestUtils.setField(service, "claimTimeout", Duration.ofMinutes(10));
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        ReflectionTestUtils.setField(service, "transactions", new TransactionTemplate(manager));
        task.id = 1L;
        task.executionId = 2L;
        task.status = "PENDING";
        when(tasks.update(any(), any(Wrapper.class))).thenReturn(1);
        when(slot.canContinue()).thenReturn(true);
        when(tasks.selectById(1L)).thenReturn(task);
        when(domains.claimAiVideoTask(anyLong(), anyString(), anyString(), any()))
            .thenAnswer(invocation -> {
                task.executionToken = "domain-token";
                task.executionVersion = 1;
                return new AiTaskExecutionSupport.ClaimResult(true, "domain-token", 1, "key");
            });
    }

    @Test
    void exhaustedLocalPoolDoesNotClaimDatabaseTask() {
        schedule();
        verifyNoInteractions(claims, domains);
        assertThat(task.status).isEqualTo("PENDING");
    }

    @Test
    void quotaWaitReleasesLocalReservationWithoutSubmitting() {
        when(pools.tryAcquire(AiExecutionPoolType.VIDEO_SUBMIT)).thenReturn(slot);
        schedule();
        var order = inOrder(pools, claims);
        order.verify(pools).tryAcquire(AiExecutionPoolType.VIDEO_SUBMIT);
        order.verify(claims).claim(eq(2L), anyString(), any(), any());
        verify(slot).close();
        verify(slot, never()).submit(any());
        verify(domains).claimAiVideoTask(eq(1L), eq("PENDING"), eq("VIDEO_SUBMIT"), any());
    }

    @Test
    void rejectedSubmissionDefersClaimWithoutCallingProvider() {
        var claim = new AiExecutionClaim(2L, 3L, "unified-token", 1, "VIDEO_SUBMIT");
        when(pools.tryAcquire(AiExecutionPoolType.VIDEO_SUBMIT)).thenReturn(slot);
        when(claims.claim(eq(2L), anyString(), any(), any())).thenReturn(claim);
        doThrow(new RejectedExecutionException()).when(slot).submit(any());
        schedule();
        verify(claims).defer(eq(claim), contains("executor"), any());
        assertThat(task.status).isEqualTo("PENDING");
        verify(slot).close();
    }

    @Test
    void queryingAcceptedVideoUsesQueryPoolWithoutReclaimingQuota() {
        task.externalTaskId = "provider-task";
        task.status = "GENERATING";
        when(pools.tryAcquire(AiExecutionPoolType.VIDEO_QUERY)).thenReturn(slot);
        schedule();
        verifyNoInteractions(claims);
        var order = inOrder(pools, domains, tasks, slot);
        order.verify(pools).tryAcquire(AiExecutionPoolType.VIDEO_QUERY);
        order.verify(domains).claimAiVideoTask(eq(1L), eq("GENERATING"), eq("VIDEO_QUERY"), any());
        order.verify(tasks).update(isNull(), any(Wrapper.class));
        order.verify(slot).submit(any());
    }

    @Test
    void pendingTaskWithoutExternalIdCannotEnterQueryPath() {
        task.status = "SUBMITTING";
        schedule();
        verifyNoInteractions(pools, claims, domains);
    }

    @Test
    @SuppressWarnings("unchecked")
    void reservedWorkerDrainsCandidatesWithoutWaitingForAnotherSchedulerTick() {
        AiVideoTaskEntity second = new AiVideoTaskEntity();
        second.id = 11L;
        var work = (java.util.function.Consumer<AiVideoTaskEntity>) mock(java.util.function.Consumer.class);
        when(pools.tryAcquire(AiExecutionPoolType.VIDEO_QUERY)).thenReturn(slot, null);
        ReflectionTestUtils.invokeMethod(service, "scheduleBatch", List.of(task, second),
            AiExecutionPoolType.VIDEO_QUERY, work);
        verifyNoInteractions(work);
        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
        verify(slot).submit(callback.capture());
        callback.getValue().run();
        verify(work).accept(task);
        verify(work).accept(second);
        verifyNoMoreInteractions(work);
    }

    @Test
    @SuppressWarnings("unchecked")
    void loweredPoolLimitStopsBatchBeforeClaimingAnotherCandidate() {
        var work = (java.util.function.Consumer<AiVideoTaskEntity>) mock(java.util.function.Consumer.class);
        when(pools.tryAcquire(AiExecutionPoolType.VIDEO_QUERY)).thenReturn(slot);
        when(slot.canContinue()).thenReturn(false);
        ReflectionTestUtils.invokeMethod(service, "scheduleBatch", List.of(task), AiExecutionPoolType.VIDEO_QUERY, work);
        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
        verify(slot).submit(callback.capture());
        callback.getValue().run();
        verifyNoInteractions(work, claims, domains);
    }

    @Test
    void overlappingBatchSkipsCandidateWhoseFreshPollTimeIsInTheFuture() {
        task.status = "GENERATING";
        task.externalTaskId = "external";
        AiVideoTaskEntity fresh = new AiVideoTaskEntity();
        fresh.id = task.id;
        fresh.executionToken = "domain-token";
        fresh.executionVersion = 1;
        fresh.nextPollAt = LocalDateTime.now().plusMinutes(1);
        when(tasks.selectById(1L)).thenReturn(fresh);
        AiVideoTaskEntity claimed = ReflectionTestUtils.invokeMethod(service, "claimQueryTask", task, true);
        assertThat(claimed).isNull();
        verify(tasks).update(isNull(), any(Wrapper.class));
        verifyNoInteractions(claims, slot);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void dueQueriesAreBoundedAndOrderedByNextPollRatherThanCreationTime() {
        AiVideoTaskMapper mapper = mock(AiVideoTaskMapper.class, CALLS_REAL_METHODS);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        mapper.selectDueQueries(LocalDateTime.now(), 7);
        ArgumentCaptor<Wrapper> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(query.capture());
        assertThat(query.getValue().getSqlSegment())
            .contains("external_task_id IS NOT NULL", "execution_token IS NULL",
                "ORDER BY next_poll_at ASC,id ASC", "limit 7")
            .doesNotContain("created_at");
    }

    private void schedule() {
        ReflectionTestUtils.invokeMethod(service, "scheduleTask", task, 9L);
    }
}
