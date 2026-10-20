package com.antshorttv.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class MonitoringBufferTest {
    private final Instant start = Instant.parse("2026-10-20T01:30:00Z");
    private final Instant end = start.plusSeconds(3600);

    @Test
    void drainsHalfOpenWindowOnceAndKeepsBoundaryForNextBatch() {
        var buffer = new MonitoringBuffer(new MonitoringProperties(true, "", "", "test", "one", 10, true));
        buffer.record(event(start.minusSeconds(1)));
        buffer.record(event(start));
        buffer.record(event(end.minusMillis(1)));
        buffer.record(event(end));
        assertThat(buffer.drain(start, end).events()).hasSize(2);
        assertThat(buffer.drain(start, end).events()).isEmpty();
        assertThat(buffer.drain(end, end.plusSeconds(3600)).events()).hasSize(1);
    }

    @Test
    void boundsMemoryAndReportsOverflowWithoutCollectingWhenDisabled() {
        var buffer = new MonitoringBuffer(new MonitoringProperties(true, "", "", "test", "one", 1, true));
        buffer.record(event(start));
        buffer.record(event(start.plusSeconds(1)));
        var batch = buffer.drain(start, end);
        assertThat(batch.events()).hasSize(1);
        assertThat(batch.dropped()).isEqualTo(1);
        var disabled = new MonitoringBuffer(new MonitoringProperties(false, "", "", "test", "one", 1, true));
        disabled.record(event(start));
        assertThat(disabled.drain(start, end).events()).isEmpty();
    }

    @Test
    void stripsSensitiveValuesAndQueriesBeforeBuffering() {
        var buffer = new MonitoringBuffer(new MonitoringProperties(true, "", "", "test", "one", 10, true));
        buffer.record(new MonitoringEvent(start, "API", "ERROR", "/api/test?token=secret#private",
            "Authorization: Bearer topsecret password=abc mobile=13800138000", "at https://app.test/a.js?key=private:1:2",
            1L, 2L, null, null));
        var event = buffer.drain(start, end).events().get(0);
        assertThat(event.location()).isEqualTo("/api/test");
        assertThat(event.message()).doesNotContain("topsecret", "abc", "13800138000");
        assertThat(event.stack()).doesNotContain("private");
    }

    @Test
    void redactsProviderEchoedCredentialsAndEntireCookieHeaders() {
        assertThat(MonitoringText.clean("Incorrect API key provided: sk-proj-1234567890abcdef", 500))
            .doesNotContain("1234567890abcdef");
        assertThat(MonitoringText.clean("Cookie: session=first; auth=second\nerror", 500))
            .doesNotContain("first", "second").contains("error");
    }

    @Test
    void aggregatesRepeatsAndSplitsMessagesWithinFeishuLimit() {
        var properties = new MonitoringProperties(true, "", "", "prod", "one", 100, true);
        var formatter = new MonitoringDigestFormatter(properties);
        var messages = formatter.format(start, end, java.util.List.of(event(start), event(start.plusSeconds(1))), 0);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).contains("次数：2", "影响用户：1", "09:30", "10:30", "prod", "one");
        var events = java.util.stream.IntStream.range(0, 100).mapToObj(i ->
            new MonitoringEvent(start, "API", "CODE_" + i, "/api/" + i, "错".repeat(2000), "", (long) i, 2L, null, null)).toList();
        assertThat(formatter.format(start, end, events, 2)).hasSizeGreaterThan(1).allSatisfy(message ->
            assertThat(message.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThan(18000));
        assertThat(formatter.format(start, end, java.util.List.of(), 0)).isEmpty();
    }

    @Test
    void capsDigestPartsAndDisclosesOmittedGroupsDuringErrorStorms() {
        var formatter = new MonitoringDigestFormatter(new MonitoringProperties(true, "", "", "test", "one", 10000, true));
        var events = java.util.stream.IntStream.range(0, 1000).mapToObj(i ->
            new MonitoringEvent(start, "API", "CODE_" + i, "/api/" + i, "错".repeat(500), "", (long) i, null, null, null)).toList();
        var messages = formatter.format(start, end, events, 0);
        assertThat(messages.size()).isLessThanOrEqualTo(20);
        assertThat(messages.get(messages.size() - 1)).contains("其余", "类错误明细省略");
    }

    private MonitoringEvent event(Instant time) {
        return new MonitoringEvent(time, "API", "ERROR", "/api/test", "broken", "", 1L, 2L, null, null);
    }
}
