package com.antshorttv.operations.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.antshorttv.operations.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class OperationsProjectionTest {
    @Test void resolvedModelCorrectionUpdatesSameExecutionVersion() {
        var row=call(8);row.put("execution_version",1);row.put("status","RUNNING");row.put("model_id",1L);projections.projectExecutionSnapshot(row);
        row.put("model_id",42L);projections.projectExecutionSnapshot(row);
        assertThat(jdbc.queryForObject("select model_id from ops_execution_observation where execution_id=8",Long.class)).isEqualTo(42);
    }
    @Test void failedExecutionEmitsOneSanitizedPersistentDigestEventPerVersion() {
        var row=call(9);row.put("execution_version",1);row.put("status","FAILED");row.put("completed_at",LocalDateTime.now());row.put("error_code","AI_PROVIDER_TIMEOUT");row.put("error_message","password=secret");
        projections.projectExecutionSnapshot(row);projections.projectExecutionSnapshot(row);
        assertThat(jdbc.queryForObject("select count(*) from ops_event where source='AI_EXECUTION'",Long.class)).isEqualTo(1);
        row.put("execution_version",2);projections.projectExecutionSnapshot(row);
        assertThat(jdbc.queryForObject("select count(*) from ops_event where source='AI_EXECUTION'",Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForList("select message from ops_event",String.class)).allSatisfy(message->assertThat(message).doesNotContain("secret"));
    }
    JdbcTemplate jdbc;
    OperationsProjections projections;
    OperationsTelemetry telemetry;
    @BeforeEach void setup() {
        jdbc=OperationsTestDatabase.create();
        var settings=mock(OperationsSettingsService.class);when(settings.instanceId()).thenReturn("test");
        telemetry=new OperationsTelemetry(new OperationsDatabase(jdbc),settings,new ObjectMapper());
        projections=new OperationsProjections(telemetry.database,telemetry,new ObjectMapper());telemetry.projections(projections);
    }
    Map<String,Object> call(long id) {
        var row=new HashMap<String,Object>();row.put("id",id);row.put("created_at",LocalDateTime.now().withSecond(0).withNano(0));
        row.put("tenant_id",1L);row.put("project_id",2L);row.put("provider_id",3L);row.put("model_id",4L);
        row.put("business_scene","text");row.put("service_type","TEXT");row.put("transport_outcome","SUCCEEDED");
        row.put("business_outcome","SUCCEEDED");row.put("status","SUCCESS");row.put("duration_ms",10L);return row;
    }
    @Test void repeatedHintsAndBusinessCorrectionReplaceAbsoluteMinuteAndHourTotals() {
        var first=call(1);var stale=new HashMap<>(first);projections.projectCallSnapshot(first);projections.projectCallSnapshot(first);
        var second=call(2);second.put("duration_ms",10000L);projections.projectCallSnapshot(second);
        first.put("business_outcome","FAILED");projections.projectCallSnapshot(first);
        projections.projectCallSnapshot(stale);
        telemetry.flush();
        assertThat(jdbc.queryForObject("select count(*) from ops_ai_call_projection",Long.class)).isEqualTo(2);
        var rows=jdbc.queryForList("select count_value,business_count,error_count,histogram_json from ops_metric_bucket where family='AI' and dimensions_json=? order by granularity","{\"scope\":\"ALL\"}");
        assertThat(rows).hasSize(2);
        for(var row:rows) {assertThat(((Number)row.get("count_value")).longValue()).isEqualTo(2);assertThat(((Number)row.get("business_count")).longValue()).isEqualTo(1);assertThat(((Number)row.get("error_count")).longValue()).isZero();}
        assertThat(jdbc.queryForObject("select count(*) from ops_metric_bucket where family='AI' and provider_id=3 and model_id=4",Long.class)).isEqualTo(2);
    }
    @Test void failedVersionSurvivesRetryAndStaleRunningSnapshot() {
        var row=call(7);row.put("execution_version",1);row.put("status","FAILED");row.put("phase","GENERATE");
        projections.projectExecutionSnapshot(row);row.put("status","RUNNING");projections.projectExecutionSnapshot(row);
        row.put("execution_version",2);projections.projectExecutionSnapshot(row);
        assertThat(jdbc.queryForList("select status from ops_execution_observation where execution_id=7 order by execution_version",String.class)).containsExactly("FAILED","RUNNING");
    }
    @Test void httpGlobalAndRouteBucketsAreIdempotentAcrossFlushes() {
        telemetry.recordHttp(new OperationsTelemetry.HttpObservation(Instant.now(),"/api/task/{id}","GET","SUCCESS",10,"x",2,3));
        telemetry.flush();telemetry.flush();
        assertThat(jdbc.queryForObject("select count_value from ops_metric_bucket where family='HTTP' and granularity='MINUTE' and dimensions_json=?",Long.class,"{\"scope\":\"ALL\"}")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from ops_metric_bucket where family='HTTP'",Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from ops_metric_bucket where route='/api/task/{id}' and method='GET'",Long.class)).isEqualTo(2);
    }
    @Test void sameInstanceRestartPreservesPriorMinuteCountsAndHistogram() {
        telemetry.recordHttp(new OperationsTelemetry.HttpObservation(Instant.now(),"/api/task/{id}","GET","SUCCESS",10,"x",2,3));telemetry.flush();
        var restarted=new OperationsTelemetry(telemetry.database,telemetry.settings,new ObjectMapper());
        restarted.recordHttp(new OperationsTelemetry.HttpObservation(Instant.now(),"/api/task/{id}","GET","TECHNICAL_ERROR",5000,"y",1,2));restarted.flush();restarted.flush();
        var row=jdbc.queryForMap("select count_value,error_count,histogram_json from ops_metric_bucket where family='HTTP' and granularity='MINUTE' and dimensions_json=?","{\"scope\":\"ALL\"}");
        assertThat(((Number)row.get("count_value")).longValue()).isEqualTo(2);assertThat(((Number)row.get("error_count")).longValue()).isEqualTo(1);
    }
    @Test void slowSampleContainsNormalizedRouteAndRequestSqlEvidence() {
        telemetry.recordHttp(new OperationsTelemetry.HttpObservation(Instant.now(),"/api/task/{id}","GET","SUCCESS",3200,"request-123",7,12));
        telemetry.recordHttp(new OperationsTelemetry.HttpObservation(Instant.now(),"/api/task/{id}","GET","SUCCESS",100,"request-124",1,1));telemetry.flush();
        var row=jdbc.queryForMap("select code,location,request_id,duration_ms,sql_count,sql_ms from ops_event");
        assertThat(row).containsEntry("code","SLOW_REQUEST").containsEntry("location","GET /api/task/{id}").containsEntry("request_id","request-123");
        assertThat(((Number)row.get("duration_ms")).longValue()).isEqualTo(3200);assertThat(((Number)row.get("sql_count")).longValue()).isEqualTo(7);assertThat(((Number)row.get("sql_ms")).longValue()).isEqualTo(12);
    }
}
