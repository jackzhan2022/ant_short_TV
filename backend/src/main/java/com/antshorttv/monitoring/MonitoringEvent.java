package com.antshorttv.monitoring;

import java.time.Instant;

public record MonitoringEvent(
    Instant occurredAt, String source, String code, String location, String message, String stack,
    Long userId, Long tenantId, Long projectId, Long taskId
) {
    public MonitoringEvent sanitized() {
        return new MonitoringEvent(occurredAt, MonitoringText.clean(source, 40), MonitoringText.clean(code, 100),
            MonitoringText.path(location), MonitoringText.clean(message, 500), MonitoringText.clean(stack, 800),
            userId, tenantId, projectId, taskId);
    }
}
