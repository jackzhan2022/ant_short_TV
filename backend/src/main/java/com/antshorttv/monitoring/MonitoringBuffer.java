package com.antshorttv.monitoring;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

@Component
public class MonitoringBuffer {
    public record Batch(List<MonitoringEvent> events, long dropped) {}

    private final MonitoringProperties properties;
    private final List<MonitoringEvent> events = new ArrayList<>();
    private final TreeMap<Instant, Long> dropped = new TreeMap<>();
    private Instant cleanupWindow;

    public MonitoringBuffer(MonitoringProperties properties) {
        this.properties = properties;
    }

    public synchronized void record(MonitoringEvent event) {
        if (!properties.enabled()) return;
        Instant windowEnd = MonitoringDigestScheduler.windowEnd(event.occurredAt()).plusSeconds(3600);
        // Expire stale data even if a previous scheduler run was skipped.
        if (cleanupWindow == null || windowEnd.isAfter(cleanupWindow)) {
            events.removeIf(item -> item.occurredAt().isBefore(windowEnd.minusSeconds(7200)));
            dropped.headMap(windowEnd.minusSeconds(3600), false).clear();
            cleanupWindow = windowEnd;
        }
        if (events.size() >= properties.maxEvents()) {
            dropped.merge(windowEnd, 1L, Long::sum);
            return;
        }
        events.add(event.sanitized());
    }

    public synchronized Batch drain(Instant start, Instant end) {
        List<MonitoringEvent> selected = new ArrayList<>();
        events.removeIf(event -> {
            if (!event.occurredAt().isBefore(end)) return false;
            if (!event.occurredAt().isBefore(start)) selected.add(event);
            return true;
        });
        long overflow = dropped.getOrDefault(end, 0L);
        dropped.headMap(end, true).clear();
        return new Batch(List.copyOf(selected), overflow);
    }
}
