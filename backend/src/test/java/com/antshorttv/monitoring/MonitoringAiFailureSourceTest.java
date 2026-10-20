package com.antshorttv.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.antshorttv.execution.AiExecutionTaskEntity;
import com.antshorttv.execution.AiExecutionTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MonitoringAiFailureSourceTest {
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void queriesOnlyCompletedTerminalFailuresWithinHalfOpenWindow() {
        var mapper = mock(AiExecutionTaskMapper.class);
        var task = new AiExecutionTaskEntity();
        task.id = 5L; task.userId = 1L; task.tenantId = 2L; task.projectId = 3L;
        task.errorCode = "AI_PROVIDER_ERROR"; task.errorMessage = "provider failed"; task.scene = "IMAGE";
        task.completedAt = LocalDateTime.now();
        when(mapper.selectList(any())).thenReturn(List.of(task));
        var source = new MonitoringAiFailureSource(mapper, new MonitoringProperties(true, "", "", "test", "one", 10, true));
        var start = Instant.parse("2026-10-20T01:30:00Z");
        assertThat(source.collect(start, start.plusSeconds(3600))).singleElement().satisfies(event -> {
            assertThat(event.source()).isEqualTo("AI_TASK");
            assertThat(event.taskId()).isEqualTo(5);
            assertThat(event.userId()).isEqualTo(1);
        });
        ArgumentCaptor<QueryWrapper<AiExecutionTaskEntity>> query = ArgumentCaptor.forClass((Class) QueryWrapper.class);
        verify(mapper).selectList(query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("status IN", "completed_at >=", "completed_at <", "LIMIT 11");
        assertThat(query.getValue().getParamNameValuePairs().values()).contains("FAILED", "TIMED_OUT").doesNotContain("PENDING", "RUNNING");
    }
}
