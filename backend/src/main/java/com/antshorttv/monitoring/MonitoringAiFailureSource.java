package com.antshorttv.monitoring;

import com.antshorttv.execution.AiExecutionTaskEntity;
import com.antshorttv.execution.AiExecutionTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MonitoringAiFailureSource {
    private final AiExecutionTaskMapper mapper;
    private final MonitoringProperties properties;

    public MonitoringAiFailureSource(AiExecutionTaskMapper mapper, MonitoringProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
    }

    public List<MonitoringEvent> collect(Instant start, Instant end) {
        if (!properties.enabled() || !properties.includeAiTasks()) return List.of();
        // Existing execution timestamps are written with LocalDateTime.now() in the JVM zone.
        ZoneId databaseZone = ZoneId.systemDefault();
        return mapper.selectList(new QueryWrapper<AiExecutionTaskEntity>()
                .select("id", "user_id", "tenant_id", "project_id", "scene", "error_code", "error_message", "completed_at")
                .in("status", "FAILED", "TIMED_OUT")
                .ge("completed_at", LocalDateTime.ofInstant(start, databaseZone))
                .lt("completed_at", LocalDateTime.ofInstant(end, databaseZone))
                .orderByAsc("completed_at", "id")
                .last("LIMIT " + (properties.maxEvents() + 1)))
            .stream().map(task -> new MonitoringEvent(task.completedAt.atZone(databaseZone).toInstant(),
                "AI_TASK", task.errorCode, task.scene, task.errorMessage, "",
                task.userId, task.tenantId, task.projectId, task.id).sanitized()).toList();
    }
}
