package com.antshorttv.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

class AiExecutionDispatcherTest {
    private final AiExecutionTaskMapper tasks = mock(AiExecutionTaskMapper.class);
    private final AiExecutionClaimService claims = mock(AiExecutionClaimService.class);
    private final AiExecutionWorker worker = mock(AiExecutionWorker.class);

    @Test
    void publishesWhileGenerationWorkerIsStillWaitingForTheProvider() throws Exception {
        var configuration = new AiExecutionExecutorConfiguration();
        var generation = configuration.aiExecutionTaskExecutor(1, 1);
        var publication = configuration.aiImagePublicationExecutor(1, 1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch published = new CountDownLatch(1);
        doAnswer(call -> { started.countDown(); release.await(5, TimeUnit.SECONDS); return null; })
            .when(worker).run(1L);
        doAnswer(call -> { published.countDown(); return null; }).when(worker).run(2L);
        when(tasks.selectList(any())).thenReturn(List.of(), List.of(task(1L)),
            List.of(task(2L)), List.of(task(1L)));
        var dispatcher = new AiExecutionDispatcher(tasks, claims, worker, generation, publication, 20);
        try {
            assertThat(dispatcher.dispatchOnce()).isEqualTo(1);
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(dispatcher.dispatchOnce()).isEqualTo(1);
            assertThat(published.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(release.getCount()).isEqualTo(1);
            assertThat(generation.getThreadPoolExecutor().getQueue()).isEmpty();
        } finally {
            release.countDown();
            generation.shutdown();
            publication.shutdown();
        }
    }

    @Test
    void doesNotQueueTheSamePendingExecutionOnEveryScanAndReleasesItAfterWork() {
        List<Runnable> queued = new ArrayList<>();
        when(tasks.selectList(any())).thenReturn(List.of(), List.of(task(1L)),
            List.of(), List.of(task(1L)), List.of(), List.of(task(1L)));
        var dispatcher = new AiExecutionDispatcher(tasks, claims, worker, queued::add, Runnable::run, 20);
        assertThat(dispatcher.dispatchOnce()).isEqualTo(1);
        assertThat(dispatcher.dispatchOnce()).isZero();
        assertThat(queued).hasSize(1);
        queued.remove(0).run();
        verify(worker).run(1L);
        assertThat(dispatcher.dispatchOnce()).isEqualTo(1);
    }

    @Test
    void aFullGenerationQueueDoesNotPreventPublicationAndCanRetryNextScan() {
        List<Runnable> queued = new ArrayList<>();
        AtomicBoolean full = new AtomicBoolean(true);
        TaskExecutor generation = task -> {
            if (full.get()) throw new RejectedExecutionException("full");
            queued.add(task);
        };
        when(tasks.selectList(any())).thenReturn(List.of(task(2L)), List.of(task(1L)),
            List.of(), List.of(task(1L)));
        var dispatcher = new AiExecutionDispatcher(tasks, claims, worker, generation, Runnable::run, 20);
        assertThat(dispatcher.dispatchOnce()).isEqualTo(1);
        verify(worker).run(2L);
        full.set(false);
        assertThat(dispatcher.dispatchOnce()).isEqualTo(1);
        assertThat(queued).hasSize(1);
    }

    @Test
    void aFailedWorkerDoesNotLeaveItsExecutionPermanentlyDeduplicated() {
        List<Runnable> queued = new ArrayList<>();
        when(tasks.selectList(any())).thenReturn(List.of(), List.of(task(1L)),
            List.of(), List.of(task(1L)));
        doAnswer(call -> { throw new IllegalStateException("claim failed"); }).when(worker).run(1L);
        var dispatcher = new AiExecutionDispatcher(tasks, claims, worker, queued::add, Runnable::run, 20);
        dispatcher.dispatchOnce();
        assertThatThrownBy(() -> queued.remove(0).run()).isInstanceOf(IllegalStateException.class);
        assertThat(dispatcher.dispatchOnce()).isEqualTo(1);
    }

    @Test
    void saturatedExecutorsRejectInsteadOfExecutingProviderCallsOnTheScheduler() throws Exception {
        var executor = new AiExecutionExecutorConfiguration().aiExecutionTaskExecutor(1, 1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            executor.execute(() -> {
                started.countDown();
                try { release.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            });
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            executor.execute(() -> {});
            assertThatThrownBy(() -> executor.execute(() -> {})).isInstanceOf(RejectedExecutionException.class);
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    private AiExecutionTaskEntity task(long id) {
        var task = new AiExecutionTaskEntity();
        task.id = id;
        return task;
    }
}
