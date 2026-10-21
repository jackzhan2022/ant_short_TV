package com.antshorttv.querycache;

import com.antshorttv.ai.AiSecretCodec;
import com.antshorttv.execution.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.antshorttv.querycache.QueryResultCache.Region.*;
import static com.antshorttv.querycache.QueryResultCache.Scope.*;

class ScopedExecutionCacheTest {
    final QueryResultCache cache=new QueryResultCache(new QueryResultCacheTest.Store(),new ObjectMapper(),new AiSecretCodec("execution-scope"),new SimpleMeterRegistry(),true);
    final AiExecutionTaskMapper tasks=mock(AiExecutionTaskMapper.class);
    final AiExecutionAttemptMapper attempts=mock(AiExecutionAttemptMapper.class);
    final AtomicInteger loads=new AtomicInteger();
    String read(QueryResultCache.Region region,QueryResultCache.Scope scope) { return cache.read(region,scope,"same",String.class,()->"v"+loads.incrementAndGet()); }
    AiExecutionClaimService service() {
        var target=new AiExecutionClaimService(tasks,attempts,mock(JdbcTemplate.class),mock(AiConcurrencyConfigService.class));
        ReflectionTestUtils.setField(target,"queryCache",cache);
        var aspect=new QueryCacheInvalidationAspect(cache);ReflectionTestUtils.setField(aspect,"taskMapper",tasks);
        var factory=new AspectJProxyFactory(target);factory.addAspect(aspect);
        return factory.getProxy();
    }
    AiExecutionTaskEntity task(long id,long tenant,long project) {
        var task=new AiExecutionTaskEntity();task.id=id;task.tenantId=tenant;task.projectId=project;task.status="RUNNING";task.retryable=true;task.claimToken="claim";
        return task;
    }
    @Test void stateChangeInvalidatesOnlyOwningTenantAndProject() {
        read(TASKS,tenant(1));read(TASKS,tenant(2));read(ANALYSIS,project(1,10));read(ANALYSIS,project(1,11));
        when(tasks.update(isNull(),any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1);
        when(tasks.selectById(100L)).thenReturn(task(100,1,10));
        service().defer(new AiExecutionClaim(100L,200L,"claim",1,"TEXT"),"wait",LocalDateTime.now());
        assertThat(read(TASKS,tenant(1))).isEqualTo("v5");assertThat(read(TASKS,tenant(2))).isEqualTo("v2");
        assertThat(read(ANALYSIS,project(1,10))).isEqualTo("v6");assertThat(read(ANALYSIS,project(1,11))).isEqualTo("v4");
        verify(tasks,times(1)).selectById(100L);
    }
    @Test void recoveryOnlyInvalidatesSuccessfullyRecoveredTasks() {
        read(TASKS,tenant(1));read(TASKS,tenant(2));
        when(tasks.selectList(any())).thenReturn(List.of(task(100,1,10),task(101,2,20)));
        when(tasks.update(isNull(),any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1,0);
        assertThat(service().recoverExpiredClaims(LocalDateTime.now())).isEqualTo(1);
        assertThat(read(TASKS,tenant(1))).isEqualTo("v3");assertThat(read(TASKS,tenant(2))).isEqualTo("v2");
        verify(tasks,never()).selectById(any(java.io.Serializable.class));
    }
    @Test void failedAdmissionDoesNotInvalidateOrResolveScopeAgain() {
        read(TASKS,tenant(1));when(tasks.selectById(100L)).thenReturn(null);
        assertThat(service().claim(100L,"token",LocalDateTime.now(),java.time.Duration.ofSeconds(10))).isNull();
        assertThat(read(TASKS,tenant(1))).isEqualTo("v1");verify(tasks,times(1)).selectById(100L);
    }
}
