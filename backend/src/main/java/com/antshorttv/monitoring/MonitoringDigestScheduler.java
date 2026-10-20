package com.antshorttv.monitoring;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

@Component
public class MonitoringDigestScheduler {
    static final String CRON = "10 30 * * * *";
    private static final Logger LOG = LoggerFactory.getLogger(MonitoringDigestScheduler.class);
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private final MonitoringProperties properties;
    private final MonitoringBuffer buffer;
    private final MonitoringAiFailureSource aiFailures;
    private final MonitoringDigestFormatter formatter;
    private final FeishuWebhookClient client;
    private ThreadPoolTaskScheduler scheduler;
    private Instant lastWindow;

    public MonitoringDigestScheduler(MonitoringProperties properties, MonitoringBuffer buffer,
        MonitoringAiFailureSource aiFailures, MonitoringDigestFormatter formatter, FeishuWebhookClient client) {
        this.properties = properties;
        this.buffer = buffer;
        this.aiFailures = aiFailures;
        this.formatter = formatter;
        this.client = client;
    }

    @PostConstruct
    void start() {
        if (!properties.enabled()) return;
        if (properties.feishuWebhookUrl().isBlank()) {
            throw new IllegalArgumentException("MONITORING_FEISHU_WEBHOOK_URL is required when monitoring is enabled");
        }
        // Do not register this as a bean: otherwise it could become the default scheduler for all application jobs.
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("monitoring-digest-");
        scheduler.setErrorHandler(error -> LOG.warn("Monitoring digest failed: {}", error.getClass().getSimpleName()));
        scheduler.initialize();
        // Keep windows anchored at :30:00; allow ten seconds for terminal task transactions to commit.
        scheduler.schedule(() -> publish(Instant.now()), new CronTrigger(CRON, SHANGHAI));
    }

    static Instant windowEnd(Instant now) {
        var end = now.atZone(SHANGHAI).withMinute(30).withSecond(0).withNano(0);
        return (end.toInstant().isAfter(now) ? end.minusHours(1) : end).toInstant();
    }

    synchronized void publish(Instant now) {
        if (!properties.enabled()) return;
        Instant end = windowEnd(now);
        if (lastWindow != null && !end.isAfter(lastWindow)) return;
        Instant start = end.minusSeconds(3600);
        var batch = buffer.drain(start, end);
        var events = new ArrayList<>(batch.events());
        long dropped = batch.dropped();
        try {
            var ai = aiFailures.collect(start, end);
            if (ai.size() > properties.maxEvents()) {
                dropped += ai.size() - properties.maxEvents();
                ai = ai.subList(0, properties.maxEvents());
            }
            events.addAll(ai);
        } catch (RuntimeException exception) {
            LOG.warn("Monitoring AI failure query failed for window {}: {}", end, exception.getClass().getSimpleName());
            events.add(new MonitoringEvent(start, "MONITORING", "AI_FAILURE_QUERY_FAILED", "AI_TASK",
                "AI任务失败汇总查询异常，本批次统计不完整。", "", null, null, null, null));
        }
        lastWindow = end;
        var messages = formatter.format(start, end, events, dropped);
        for (int i = 0; i < messages.size(); i++) {
            boolean sent = false;
            for (int attempt = 0; attempt < 3 && !Thread.currentThread().isInterrupted(); attempt++) {
                if (attempt > 0) {
                    try { Thread.sleep(1000L * attempt); }
                    catch (InterruptedException exception) { Thread.currentThread().interrupt(); break; }
                }
                if (client.send(messages.get(i))) { sent = true; break; }
            }
            if (!sent) LOG.warn("Monitoring digest delivery failed: window={}, part={}/{}, events={}", end, i + 1, messages.size(), events.size());
            // Stay below the robot's per-second rate limit when sending split digests.
            if (i + 1 < messages.size()) {
                try { Thread.sleep(650); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); break; }
            }
        }
    }

    @PreDestroy
    void stop() {
        if (scheduler != null) scheduler.shutdown();
    }
}
