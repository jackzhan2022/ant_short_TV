package com.antshorttv.operations.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.antshorttv.monitoring.MonitoringEvent;
import com.antshorttv.operations.OperationsDatabase;
import com.antshorttv.operations.OperationsSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class OperationsTelemetryTest {
    @Test void queuedCaptureExpiresAfterSixtySecondsWithoutWaitingForDatabase() {
        var database=mock(OperationsDatabase.class);var settings=mock(OperationsSettingsService.class);
        var telemetry=new OperationsTelemetry(database,settings,new ObjectMapper(),1);
        telemetry.captureEvent(new MonitoringEvent(Instant.now(),"API","FAILURE","/api/test","error","",null,null,null,null));
        telemetry.flush(Instant.now().plusSeconds(61));
        assertThat(telemetry.pipelineSnapshot().get("queueDepth")).isEqualTo(0);
        assertThat(telemetry.pipelineSnapshot().get("dropped")).isEqualTo(1L);
        verifyNoInteractions(database);
    }
    @Test void histogramMergesCountsInsteadOfAveragingPercentiles() {
        var fast = new LatencyHistogram();
        for (int i=0;i<100;i++) fast.record(10);
        var slow = new LatencyHistogram(); slow.record(10000); fast.merge(slow);
        assertThat(fast.percentile(.95)).isEqualTo(10d);
        assertThat(new LatencyHistogram().percentile(.95)).isNull();
        var overflow = new LatencyHistogram(); overflow.record(1800001);
        assertThat(overflow.percentile(.99)).isNull();
        assertThat(overflow.exceedsBound(.99)).isTrue();
    }

    @Test void fullQueueAndDatabaseOutageDoNotEscapeToBusinessCaller() {
        var database = mock(OperationsDatabase.class);
        var jdbc = mock(JdbcTemplate.class);
        when(database.jdbc()).thenReturn(jdbc);
        when(jdbc.update(anyString(), any(Object[].class))).thenThrow(new IllegalStateException("offline"));
        var settings = mock(OperationsSettingsService.class);
        when(settings.telemetryEnabled()).thenReturn(true);
        when(settings.instanceId()).thenReturn("test");
        var telemetry = new OperationsTelemetry(database, settings, new ObjectMapper(), 1);
        var event = new MonitoringEvent(Instant.now(), "FRONTEND_RUNTIME", "FAILED", "/test?token=secret", "password=secret", "", 1L, 2L, null, null);
        telemetry.captureEvent(event); telemetry.captureEvent(event);
        assertThat(telemetry.pipelineSnapshot().get("queueDepth")).isEqualTo(1);
        assertThat(telemetry.pipelineSnapshot().get("dropped")).isEqualTo(1L);
        telemetry.flush();
        assertThat(telemetry.pipelineSnapshot().get("queueDepth")).isEqualTo(1);
    }
}
