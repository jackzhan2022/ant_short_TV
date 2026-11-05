package com.antshorttv.operations;

import static org.assertj.core.api.Assertions.*;
import com.antshorttv.monitoring.MonitoringProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OperationsQueryServiceTest {
    @Test void resourcesAloneDoNotInventHealthyBusinessTraffic(){
        var jdbc=OperationsTestDatabase.create();var db=new OperationsDatabase(jdbc);var json=new ObjectMapper();
        var service=new OperationsQueryService(db,new OperationsSettingsService(db,json,new MonitoringProperties(false,"","","test","one",10,true)),json);
        Instant now=Instant.now();
        jdbc.update("insert into ops_metric_bucket(bucket_at,granularity,instance_id,family,dimensions_key,dimensions_json,count_value,values_json,updated_at) values(?,'MINUTE','one','RESOURCE','r','{}',1,'{}',?)",Timestamp.from(now.minusSeconds(60)),Timestamp.from(now));
        assertThat(((Map<?,?>)service.snapshot(OperationsQuery.parse(Map.of(),now)).get("health")).get("status")).isEqualTo("NO_DATA");
    }
    @Test void mergesHistogramCountsAndUsesClosedTimeBoundsWithoutInventingHealthyData() {
        var jdbc=OperationsTestDatabase.create();var db=new OperationsDatabase(jdbc);var json=new ObjectMapper();
        var settings=new OperationsSettingsService(db,json,new MonitoringProperties(false,"","","test","one",10,true));
        var service=new OperationsQueryService(db,settings,json);
        Instant end=Instant.parse("2026-11-05T01:00:00Z");
        var query=OperationsQuery.parse(Map.of("to",end.toString()),end);
        assertThat(service.snapshot(query).get("health").toString()).contains("NO_DATA");
        String histogram="[100,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]";
        jdbc.update("insert into ops_metric_bucket(bucket_at,granularity,instance_id,family,dimensions_key,dimensions_json,count_value,histogram_json,updated_at) values(?,'MINUTE','one','HTTP',?,'{\"scope\":\"ALL\"}',100,?,?)",Timestamp.from(end.minusSeconds(60)),globalKey(),histogram,Timestamp.from(end));
        var snapshot=service.snapshot(query);
        assertThat(((Map<?,?>)snapshot.get("overview")).get("p95Ms")).isEqualTo(10d);
        assertThatThrownBy(() -> service.requests(OperationsQuery.parse(Map.of("tenantId","5","to",end.toString()),end))).isInstanceOf(com.antshorttv.common.BusinessException.class);
    }
    private String globalKey() {try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest("{\"scope\":\"ALL\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    @Test void unchangedOldRunnableWorkAndDeadInstanceNeverBecomeHealthyZeros() {
        var jdbc=OperationsTestDatabase.create();var db=new OperationsDatabase(jdbc);var json=new ObjectMapper();
        var service=new OperationsQueryService(db,new OperationsSettingsService(db,json,new MonitoringProperties(false,"","","test","one",10,true)),json);
        Instant now=Instant.now(),old=now.minusSeconds(3*86400);var at=Timestamp.from(old);
        jdbc.update("insert into ops_execution_observation(execution_id,execution_version,status,created_at,observed_at) values(1,1,'PENDING',?,?)",at,at);
        jdbc.update("insert into ops_job_heartbeat(instance_id,boot_id,job_code,enabled,schedule_type,interval_ms,expected_at,updated_at) values('dead','old','dispatcher',true,'FIXED_DELAY',1000,?,?)",at,at);
        var snapshot=service.snapshot(OperationsQuery.parse(Map.of(),now));
        var queue=(Map<?,?>)snapshot.get("queue");assertThat(queue.get("runnable")).isEqualTo(1L);assertThat(((Number)queue.get("oldestRunnableSeconds")).doubleValue()).isGreaterThan(86400);
        var jobs=service.sample("jobs.stalled","{}",1,now.truncatedTo(java.time.temporal.ChronoUnit.MINUTES));assertThat(jobs.value()).isEqualTo(1d);
    }
    @Test void keysetPaginationDoesNotReturnSecretsOrAcceptFiltersForUnrelatedSources() {
        var jdbc=OperationsTestDatabase.create();var db=new OperationsDatabase(jdbc);var json=new ObjectMapper();
        var service=new OperationsQueryService(db,new OperationsSettingsService(db,json,new MonitoringProperties(false,"","","test","one",10,true)),json);
        jdbc.update("insert into ops_event(event_key,occurred_at,source,message) values('a',current_timestamp,'API','failure'),('b',current_timestamp,'API','second')");
        var query=OperationsQuery.parse(Map.of("pageSize","1"),Instant.now().plusSeconds(1));
        var page=service.list("events",query,null);
        assertThat((java.util.List<?>)page.get("items")).hasSize(1);
        assertThat(page.get("nextCursor")).isNotNull();
        assertThat((java.util.List<?>)page.get("groups")).hasSize(1);
        assertThat(((Map<?,?>)((java.util.List<?>)page.get("groups")).get(0)).get("count")).isEqualTo(2L);
        assertThatThrownBy(() -> service.list("jobs",OperationsQuery.parse(Map.of("modelId","5"),Instant.now()),null)).isInstanceOf(com.antshorttv.common.BusinessException.class);
    }
    @Test void executionDetailLoadsOnlyLightFieldsAndRedactsProviderEvidence() {
        var jdbc=OperationsTestDatabase.create();var db=new OperationsDatabase(jdbc);var json=new ObjectMapper();
        var service=new OperationsQueryService(db,new OperationsSettingsService(db,json,new MonitoringProperties(false,"","","test","one",10,true)),json);
        jdbc.execute("create table ai_execution_attempt(id bigint,execution_id bigint,execution_version int,phase varchar(64),attempt_no int,status varchar(32),provider_id bigint,model_id bigint,transport_outcome varchar(32),business_outcome varchar(32),error_code varchar(64),error_message varchar(1000),started_at datetime,finished_at datetime)");
        jdbc.update("insert into ai_execution_attempt(id,execution_id,execution_version,phase,attempt_no,status,error_message,started_at) values(1,42,1,'TEXT',1,'FAILED','Bearer sensitive-provider-secret',current_timestamp)");
        jdbc.update("insert into ops_execution_observation(execution_id,execution_version,status,observed_at) values(42,1,'FAILED',current_timestamp)");
        var result=service.execution(42,OperationsQuery.parse(Map.of(),Instant.now().plusSeconds(1)));
        assertThat((java.util.List<?>)result.get("attempts")).hasSize(1);
        assertThat(result.toString()).doesNotContain("sensitive-provider-secret");
        assertThat(result).containsEntry("coverage","PARTIAL");
        var history=service.execution(42,new OperationsQuery(Instant.now().minusSeconds(7200),Instant.now().minusSeconds(3600),20,null,Map.of()));
        assertThat((java.util.List<?>)history.get("versions")).hasSize(1);
        assertThat((java.util.List<?>)history.get("attempts")).isEmpty();
        assertThat(history).containsEntry("versionScope","LATEST_RETAINED_CONTEXT");
    }
    @Test void phaseAgeCountsOnlyActiveStartedAttemptsOfTheCurrentExecutionVersion() throws Exception {
        var jdbc=OperationsTestDatabase.create();var db=new OperationsDatabase(jdbc);var json=new ObjectMapper();
        var service=new OperationsQueryService(db,new OperationsSettingsService(db,json,new MonitoringProperties(false,"","","test","one",10,true)),json);
        jdbc.execute("create table ai_execution_attempt(id bigint,execution_id bigint,execution_version int,phase varchar(64),status varchar(32),started_at datetime,finished_at datetime)");
        Instant end=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        jdbc.update("insert into ops_runtime_setting(setting_key,value_json,version,updated_at) values('telemetry.executions.active',?,1,?)",json.writeValueAsString(Map.of("completedAt",end.toString())),Timestamp.from(end));
        jdbc.update("insert into ops_execution_observation(execution_id,execution_version,status,observed_at) values(1,2,'RUNNING',?),(2,1,'PENDING',?),(3,1,'SUCCEEDED',?)",Timestamp.from(end),Timestamp.from(end),Timestamp.from(end));
        var wall=java.time.LocalDateTime.ofInstant(end.minusSeconds(180),java.time.ZoneId.systemDefault());
        jdbc.update("insert into ai_execution_attempt values(1,1,2,'TEXT','STARTED',?,null),(2,1,1,'TEXT','STARTED',?,null),(3,2,1,'TEXT','DEFERRED',?,null),(4,3,1,'TEXT','STARTED',?,null)",wall,wall.minusHours(2),wall.minusHours(2),wall.minusHours(2));
        var sample=service.sample("execution.phaseAgeSeconds","{\"phase\":\"TEXT\"}",1,end);
        assertThat(sample.value()).isEqualTo(180d);assertThat(sample.samples()).isEqualTo(1);assertThat(sample.complete()).isTrue();
        assertThat(service.sample("execution.phaseAgeSeconds","{}",1,end).complete()).isFalse();
    }
}
