package com.antshorttv.monitoring;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public class MonitoringDigestFormatter {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Shanghai"));
    private final MonitoringProperties properties;

    public MonitoringDigestFormatter(MonitoringProperties properties) {
        this.properties = properties;
    }

    public List<String> format(Instant start, Instant end, List<MonitoringEvent> events, long dropped) {
        if (events.isEmpty() && dropped == 0) return List.of();
        String header = "系统监测｜" + MonitoringText.clean(properties.environment(), 60) + "｜" + MonitoringText.clean(properties.instanceId(), 60)
            + "\n北京时间 [" + TIME.format(start) + ", " + TIME.format(end) + ")"
            + "\n报错：" + events.size() + "｜影响用户：" + events.stream().map(MonitoringEvent::userId).filter(Objects::nonNull).distinct().count()
            + (dropped > 0 ? "\n容量限制，至少丢弃：" + dropped + " 条（超限后未完整统计）" : "") + "\n";
        record Key(String source, String code, String location, String message) {}
        var groups = new LinkedHashMap<Key, List<MonitoringEvent>>();
        for (MonitoringEvent original : events) {
            MonitoringEvent event = original.sanitized();
            groups.computeIfAbsent(new Key(event.source(), event.code(), event.location(), event.message()), key -> new ArrayList<>()).add(event);
        }
        List<String> messages = new ArrayList<>();
        StringBuilder current = new StringBuilder(header);
        int includedGroups = 0;
        for (var group : groups.values()) {
            MonitoringEvent event = group.get(0);
            String text = "\n[" + event.source() + "] " + event.code() + "\n位置：" + event.location()
                + "\n摘要：" + event.message() + "\n次数：" + group.size()
                + "｜影响用户：" + group.stream().map(MonitoringEvent::userId).filter(Objects::nonNull).distinct().count()
                + "\n用户样例：" + sample(group, MonitoringEvent::userId)
                + "｜团队：" + sample(group, MonitoringEvent::tenantId)
                + "｜项目：" + sample(group, MonitoringEvent::projectId)
                + "｜任务：" + sample(group, MonitoringEvent::taskId)
                + (event.stack().isBlank() ? "" : "\n堆栈：" + MonitoringText.clean(event.stack(), 300)) + "\n";
            if (current.length() + text.length() > 3000) {
                if (messages.size() == 19) {
                    current.append("\n其余 ").append(groups.size() - includedGroups).append(" 类错误明细省略（本批最多20个分片，汇总总数已包含）。\n");
                    break;
                }
                messages.add(current.toString());
                current = new StringBuilder(header);
            }
            current.append(text);
            includedGroups++;
        }
        messages.add(current.toString());
        List<String> numbered = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) numbered.add(messages.get(i) + "\n分片 " + (i + 1) + "/" + messages.size());
        return numbered;
    }

    private String sample(List<MonitoringEvent> events, Function<MonitoringEvent, Long> field) {
        return events.stream().map(field).filter(Objects::nonNull).distinct().limit(5).map(String::valueOf)
            .collect(java.util.stream.Collectors.joining(","));
    }
}
