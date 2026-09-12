package com.antshorttv.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.video.AiVideoTaskScheduler;
import com.antshorttv.video.AiVideoTaskService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.TaskExecutor;

class AlwaysOnAiSchedulingTest {
    @Test
    void retiredDispatcherSwitchCannotStopRecoveryAndDispatch() {
        var tasks = mock(AiExecutionTaskMapper.class);
        var claims = mock(AiExecutionClaimService.class);
        var worker = mock(AiExecutionWorker.class);
        var pending = new AiExecutionTaskEntity();
        pending.id = 42L;
        when(tasks.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(pending));
        new ApplicationContextRunner()
            .withPropertyValues("ai.execution.dispatcher.enabled=false")
            .withBean(AiExecutionTaskMapper.class, () -> tasks)
            .withBean(AiExecutionClaimService.class, () -> claims)
            .withBean(AiExecutionWorker.class, () -> worker)
            .withBean("aiExecutionTaskExecutor", TaskExecutor.class, () -> Runnable::run)
            .withUserConfiguration(AiExecutionDispatcher.class)
            .run(context -> {
                context.getBean(AiExecutionDispatcher.class).dispatchScheduled();
                verify(claims).recoverExpiredClaims(org.mockito.ArgumentMatchers.any());
                verify(worker).run(42L);
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
