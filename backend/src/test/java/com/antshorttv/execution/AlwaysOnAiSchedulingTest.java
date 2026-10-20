package com.antshorttv.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.video.AiVideoTaskScheduler;
import com.antshorttv.video.AiVideoTaskService;
import java.util.List;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AlwaysOnAiSchedulingTest {
    @Test
    void retiredDispatcherSwitchCannotStopRecoveryAndDispatch() {
        var tasks = mock(AiExecutionTaskMapper.class);
        var claims = mock(AiExecutionClaimService.class);
        var worker = mock(AiExecutionWorker.class);
        var pools = mock(AiExecutionPools.class);
        var slot = mock(AiExecutionPools.Slot.class);
        var claim = new AiExecutionClaim(42L, 43L, "token", 1, "SUBMIT");
        when(pools.tryAcquire(AiExecutionPoolType.TEXT)).thenReturn(slot);
        when(claims.claim(org.mockito.ArgumentMatchers.eq(42L), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(10)))).thenReturn(claim);
        org.mockito.Mockito.doAnswer(invocation -> { ((Runnable) invocation.getArgument(0)).run(); return null; })
            .when(slot).submit(org.mockito.ArgumentMatchers.any());
        var pending = new AiExecutionTaskEntity();
        pending.id = 42L;
        when(tasks.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(pending), List.of());
        new ApplicationContextRunner()
            .withInitializer(context -> context.getBeanFactory().setConversionService(
                new org.springframework.boot.convert.ApplicationConversionService()))
            .withPropertyValues("ai.execution.dispatcher.enabled=false")
            .withBean(AiExecutionTaskMapper.class, () -> tasks)
            .withBean(AiExecutionClaimService.class, () -> claims)
            .withBean(AiExecutionWorker.class, () -> worker)
            .withBean(AiExecutionPools.class, () -> pools)
            .withUserConfiguration(AiExecutionDispatcher.class)
            .run(context -> {
                context.getBean(AiExecutionDispatcher.class).dispatchScheduled();
                verify(claims).recoverExpiredClaims(org.mockito.ArgumentMatchers.any());
                verify(worker).runClaimed(claim);
            });
    }

    @Test
    void retiredVideoSwitchCannotRemoveScheduler() {
        var service = mock(AiVideoTaskService.class);
        new ApplicationContextRunner()
            .withPropertyValues("ai.video.scheduler.enabled=false")
            .withBean(AiVideoTaskService.class, () -> service)
            .withUserConfiguration(AiVideoTaskScheduler.class)
            .run(context -> {
                assertThat(context).hasSingleBean(AiVideoTaskScheduler.class);
                context.getBean(AiVideoTaskScheduler.class).pollDueTasks();
                verify(service).pollDueTasks();
            });
    }
}
