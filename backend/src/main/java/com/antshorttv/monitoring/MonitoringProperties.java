package com.antshorttv.monitoring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("monitoring")
public record MonitoringProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("") String feishuWebhookUrl,
    @DefaultValue("") String feishuSecret,
    @DefaultValue("unknown") String environment,
    @DefaultValue("local") String instanceId,
    @DefaultValue("10000") int maxEvents,
    @DefaultValue("true") boolean includeAiTasks
) {
    public MonitoringProperties {
        if (maxEvents < 1 || maxEvents > 100000) {
            throw new IllegalArgumentException("monitoring.max-events must be between 1 and 100000");
        }
    }
}
