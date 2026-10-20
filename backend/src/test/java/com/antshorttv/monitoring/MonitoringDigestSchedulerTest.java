package com.antshorttv.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MonitoringDigestSchedulerTest {
    @Test
    void usesShanghaiMinuteThirtyAndNeverResendsSuccessfulPartsOrSameWindow() {
        var props = new MonitoringProperties(true, "", "", "test", "one", 100, true);
        var buffer = new MonitoringBuffer(props);
        var source = mock(MonitoringAiFailureSource.class);
        when(source.collect(any(), any())).thenReturn(List.of());
        var formatter = mock(MonitoringDigestFormatter.class);
        when(formatter.format(any(), any(), any(), anyLong())).thenReturn(List.of("part 1", "part 2"));
        var client = mock(FeishuWebhookClient.class);
        when(client.send("part 1")).thenReturn(true);
        when(client.send("part 2")).thenReturn(false, true);
        var scheduler = new MonitoringDigestScheduler(props, buffer, source, formatter, client);
        Instant end = Instant.parse("2026-10-20T02:30:00Z");
        assertThat(MonitoringDigestScheduler.windowEnd(end.minusMillis(1))).isEqualTo(end.minusSeconds(3600));
        assertThat(MonitoringDigestScheduler.windowEnd(end.plusSeconds(3))).isEqualTo(end);
        scheduler.publish(end.plusSeconds(3));
        scheduler.publish(end.plusSeconds(5));
        verify(client, times(1)).send("part 1");
        verify(client, times(2)).send("part 2");
        verify(source).collect(end.minusSeconds(3600), end);
    }
}
