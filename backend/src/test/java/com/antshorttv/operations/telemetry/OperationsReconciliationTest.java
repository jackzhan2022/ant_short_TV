package com.antshorttv.operations.telemetry;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.antshorttv.operations.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import org.junit.jupiter.api.Test;

class OperationsReconciliationTest {
    @Test void sourceDatetimeUsesWallClockEvenWhenOperationsSessionIsUtcAndJvmIsShanghai() throws Exception {
        var original=java.util.TimeZone.getDefault();java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
        var base=OperationsTestDatabase.create();
        try(var connection=base.getDataSource().getConnection()) {
            var jdbc=new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection,true));jdbc.execute("set time zone 'UTC'");
            jdbc.execute("create table ai_execution_task(id bigint primary key,project_id bigint)");
            jdbc.execute("create table ai_call_log(id bigint primary key,created_at datetime(3),tenant_id bigint,provider_id bigint,model_id bigint,business_scene varchar(64),service_type varchar(32),trace_id varchar(128),execution_id bigint,attempt_id bigint,transport_outcome varchar(32),business_outcome varchar(32),status varchar(32),duration_ms bigint,prompt_tokens bigint,completion_tokens bigint,error_message varchar(1000))");
            var sourceAt=LocalDateTime.of(2026,11,5,10,0);jdbc.update("insert into ai_call_log(id,created_at,status,duration_ms) values(1,?,'SUCCESS',10)",sourceAt);
            var settings=mock(OperationsSettingsService.class);var database=new OperationsDatabase(jdbc);var telemetry=new OperationsTelemetry(database,settings,new ObjectMapper());
            var projections=new OperationsProjections(database,telemetry,new ObjectMapper());projections.projectCall(1);
            var actual=jdbc.queryForObject("select occurred_at from ops_ai_call_projection where call_log_id=1",(rs,n)->rs.getObject(1,LocalDateTime.class).toInstant(ZoneOffset.UTC));
            assertThat(actual).isEqualTo(sourceAt.atZone(ZoneId.of("Asia/Shanghai")).toInstant());
        } finally {java.util.TimeZone.setDefault(original);}
    }
    @Test void frozenKeysetsAdvanceInBoundedBatchesAndSkipOlderThan24Hours() {
        var jdbc=OperationsTestDatabase.create();var database=new OperationsDatabase(jdbc);var settings=mock(OperationsSettingsService.class);
        when(settings.instanceId()).thenReturn("test");var telemetry=new OperationsTelemetry(database,settings,new ObjectMapper());
        var projections=new OperationsProjections(database,telemetry,new ObjectMapper());telemetry.projections(projections);
        jdbc.execute("create table ai_execution_task(id bigint primary key,execution_version int,tenant_id bigint,project_id bigint,resolved_model_id bigint,requested_model_id bigint,scene varchar(64),task_type varchar(32),status varchar(32),phase varchar(64),created_at datetime(3),updated_at datetime(3),started_at datetime(3),completed_at datetime(3),next_run_at datetime(3),heartbeat_at datetime(3),claim_expires_at datetime(3),point_settlement_status varchar(32),error_code varchar(64),error_message varchar(1000),trace_id varchar(128))");
        jdbc.execute("create table ai_call_log(id bigint primary key,created_at datetime(3),tenant_id bigint,provider_id bigint,model_id bigint,business_scene varchar(64),service_type varchar(32),trace_id varchar(128),execution_id bigint,attempt_id bigint,transport_outcome varchar(32),business_outcome varchar(32),status varchar(32),duration_ms bigint,prompt_tokens bigint,completion_tokens bigint,error_message varchar(1000))");
        Instant now=Instant.now();var at=LocalDateTime.ofInstant(now.minusSeconds(60),ZoneId.systemDefault());
        for(int i=1;i<=126;i++) {
            jdbc.update("insert into ai_call_log(id,created_at,transport_outcome,business_outcome,status,duration_ms) values(?,?,'SUCCEEDED','SUCCEEDED','SUCCESS',10)",i,at);
            jdbc.update("insert into ai_execution_task(id,execution_version,status,created_at,updated_at) values(?,1,'RUNNING',?,?)",i,at,at);
        }
        jdbc.update("insert into ai_call_log(id,created_at,status,duration_ms) values(999,?,'SUCCESS',10)",at.minusDays(2));
        jdbc.update("update ai_call_log set status='FAILED',transport_outcome='FAILED',business_outcome='NOT_REACHED',error_message='AI_RATE_LIMIT redacted' where id=1");
        jdbc.update("update ai_call_log set status='FAILED',transport_outcome='FAILED',business_outcome='NOT_REACHED',error_message='AI_PROVIDER_TIMEOUT redacted' where id=2");
        jdbc.update("update ai_call_log set status='FAILED',transport_outcome='FAILED',business_outcome='NOT_REACHED',error_message='VALIDATION_ERROR redacted' where id=3");
        jdbc.update("insert into ai_execution_task(id,execution_version,status,created_at,updated_at) values(999,1,'RUNNING',?,?)",at.minusDays(2),at.minusDays(2));
        projections.reconcile(now);
        assertThat(jdbc.queryForObject("select count(*) from ops_ai_call_projection",Long.class)).isEqualTo(100);
        assertThat(jdbc.queryForObject("select count(*) from ops_execution_observation",Long.class)).isEqualTo(100);
        assertThat(jdbc.queryForObject("select value_json from ops_runtime_setting where setting_key='telemetry.executions.recent'",String.class)).contains("\"id\":100","\"at\":");
        projections.reconcile(now.plusSeconds(1));
        var coverage=mock(OperationsCoverageService.class);projections.publishCoverage(coverage,now.plusSeconds(1));
        verifyNoInteractions(coverage);
        telemetry.flush();projections.publishCoverage(coverage,now.plusSeconds(1));
        verify(coverage,times(2)).recordRange(eq("AI"),eq("GLOBAL"),eq("GLOBAL"),any(),eq(now),eq(now.plusSeconds(1)));
        assertThat(jdbc.queryForObject("select count(*) from ops_ai_call_projection",Long.class)).isEqualTo(126);
        assertThat(jdbc.queryForObject("select count(*) from ops_execution_observation",Long.class)).isEqualTo(127);
        assertThat(telemetry.pipelineSnapshot().get("activeSweepAt")).isNotNull();
        assertThat(jdbc.queryForObject("select value_json from ops_runtime_setting where setting_key='telemetry.executions.recent'",String.class)).isEqualTo("{}");
        assertThat(jdbc.queryForObject("select error_code from ops_ai_call_projection where call_log_id=1",String.class)).isEqualTo("AI_RATE_LIMIT");
        assertThat(jdbc.queryForObject("select error_code from ops_ai_call_projection where call_log_id=2",String.class)).isEqualTo("AI_PROVIDER_TIMEOUT");
        assertThat(jdbc.queryForObject("select transport_outcome from ops_ai_call_projection where call_log_id=3",String.class)).isEqualTo("LOCAL_REJECTION");
    }
}
