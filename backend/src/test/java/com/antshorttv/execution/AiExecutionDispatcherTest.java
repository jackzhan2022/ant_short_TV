package com.antshorttv.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AiExecutionDispatcherTest {
    @Test
    void publishesWhileGenerationPoolIsFullWithoutClaimingAnotherGeneration() throws Exception {
        var tasks = mock(AiExecutionTaskMapper.class);
        var claims = mock(AiExecutionClaimService.class);
        var worker = mock(AiExecutionWorker.class);
        var config = mock(AiConcurrencyConfigService.class);
        when(config.current()).thenReturn(new AiConcurrencySettings(5,5,3,4,4,1,1,1,1));
        var image = task(1L);
        var publication = task(2L);
        when(tasks.selectOne(any())).thenReturn(task(2L));
        when(tasks.selectList(any())).thenAnswer(call -> {
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AiExecutionTaskEntity> q = call.getArgument(0);
            String sql = q.getSqlSegment();
            if (q.getParamNameValuePairs().containsValue("TEXT") || q.getParamNameValuePairs().containsValue("VIDEO")) return List.of();
            return sql.contains("not ") ? List.of(image) : List.of(publication);
        });
        var claim = new AiExecutionClaim(2L,3L,"publication",1,"SUBMIT");
        when(claims.claim(eq(2L), anyString(), any(), any())).thenReturn(claim);
        var published = new CountDownLatch(1);
        doAnswer(call -> { published.countDown(); return null; }).when(worker).runClaimed(claim);
        try (var pools = new AiExecutionPools(config)) {
            try (var occupied = pools.tryAcquire(AiExecutionPoolType.IMAGE)) {
                assertThat(occupied).isNotNull();
                var dispatcher = new AiExecutionDispatcher(tasks,claims,worker,pools,20,Duration.ofMinutes(10));
                assertThat(dispatcher.dispatchOnce()).isEqualTo(1);
                assertThat(published.await(2,TimeUnit.SECONDS)).isTrue();
                verify(claims,never()).claim(eq(1L),anyString(),any(),any());
            }
        }
    }

    private AiExecutionTaskEntity task(long id) {
        var task = new AiExecutionTaskEntity();
        task.id=id;task.priority=100;task.createdAt=LocalDateTime.now();
        return task;
    }
}
