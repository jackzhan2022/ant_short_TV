package com.antshorttv.operations.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.antshorttv.operations.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class OperationsTelemetryCoverageTest {
    private static final Instant START=Instant.parse("2026-11-05T10:00:00Z");
    private JdbcTemplate jdbc;
    private OperationsTelemetry telemetry;
    private OperationsCoverageService coverage;

    @BeforeEach void setup() {
        jdbc=OperationsTestDatabase.create();
        var settings=mock(OperationsSettingsService.class);when(settings.instanceId()).thenReturn("test");
        telemetry=new OperationsTelemetry(new OperationsDatabase(jdbc),settings,new ObjectMapper());
        coverage=mock(OperationsCoverageService.class);telemetry.coverage(coverage);
        for(String field:List.of("coverageFrom","httpSeededThrough","resourceCoverageFrom","resourceThrough"))
            ReflectionTestUtils.setField(telemetry,field,START);
    }

    @Test void zeroTrafficCertifiesOnlyCommittedClosedMinutes() {
        Instant now=START.plusSeconds(70);telemetry.flush(now);
        verify(coverage).recordRange(eq("HTTP"),eq("test"),anyString(),eq(START),eq(START.plusSeconds(60)),eq(now));
        assertThat(jdbc.queryForObject("select count_value from ops_metric_bucket where family='HTTP' and granularity='MINUTE'",Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from ops_metric_bucket where family='HTTP' and granularity='MINUTE' and bucket_at>=?",Long.class,OperationsTelemetry.ts(START.plusSeconds(60)))).isZero();
    }

    @Test void newCollectorRegistersBeforeItCanCertifyAnyClosedMinute() {
        Instant now=START.plusSeconds(30);telemetry.flush(now);
        for(String family:List.of("HTTP","RESOURCE"))
            verify(coverage).recordRange(eq(family),eq("test"),anyString(),eq(START),eq(START),eq(now));
    }

    @Test void queuedBacklogAndDirtyRouteBucketsPreventCertification() {
        Instant now=START.plusSeconds(70);
        for(int i=0;i<101;i++)telemetry.queue.add(new OperationsTelemetry.QueuedWrite(now,()->{}));
        for(int i=0;i<130;i++)telemetry.recordHttp(new OperationsTelemetry.HttpObservation(START,"/api/route/"+i,"GET","SUCCESS",1,"r",0,0));
        telemetry.flush(now);
        verify(coverage,never()).recordRange(eq("HTTP"),anyString(),anyString(),any(),eq(START.plusSeconds(60)),any());
        telemetry.flush(now.plusSeconds(1));
        verify(coverage,never()).recordRange(eq("HTTP"),anyString(),anyString(),any(),eq(START.plusSeconds(60)),any());
        telemetry.flush(now.plusSeconds(2));
        verify(coverage).recordRange(eq("HTTP"),eq("test"),anyString(),eq(START),eq(START.plusSeconds(60)),eq(now.plusSeconds(2)));
    }

    @Test void shortDisableAndReenableInvalidatesPreviouslyContinuousCoverage() {
        telemetry.flush(START.plusSeconds(70));clearInvocations(coverage);
        telemetry.setEnabled(false);telemetry.setEnabled(true);
        Instant now=START.plusSeconds(80);telemetry.flush(now);
        verify(coverage).invalidate(eq("HTTP"),eq("test"),anyString(),eq(now));
        verify(coverage).invalidate(eq("RESOURCE"),eq("test"),anyString(),eq(now));
        verify(coverage,never()).recordRange(eq("HTTP"),anyString(),anyString(),any(),eq(START.plusSeconds(60)),any());
    }

    @Test void lossDuringWriteCannotCertifyTheSameInterval() {
        Instant now=START.plusSeconds(70);
        telemetry.queue.add(new OperationsTelemetry.QueuedWrite(now,()->telemetry.dropped.incrementAndGet()));
        telemetry.flush(now);
        verify(coverage).invalidate(eq("HTTP"),eq("test"),anyString(),eq(now));
        verify(coverage,never()).recordRange(eq("HTTP"),anyString(),anyString(),any(),eq(START.plusSeconds(60)),any());
    }

    @Test void resourceMinuteMustBePersistedAndClosedBeforeCertification() {
        Instant first=START.plusSeconds(30);telemetry.flush(first);
        verify(coverage,never()).recordRange(eq("RESOURCE"),anyString(),anyString(),any(),eq(START.plusSeconds(60)),any());
        telemetry.queue.add(new OperationsTelemetry.QueuedWrite(first,()->telemetry.recordResourceMinute(START)));
        telemetry.flush(first);
        verify(coverage,never()).recordRange(eq("RESOURCE"),anyString(),anyString(),any(),eq(START.plusSeconds(60)),any());
        Instant closed=START.plusSeconds(70);telemetry.flush(closed);
        verify(coverage).recordRange(eq("RESOURCE"),eq("test"),anyString(),eq(START),eq(START.plusSeconds(60)),eq(closed));
    }
}
