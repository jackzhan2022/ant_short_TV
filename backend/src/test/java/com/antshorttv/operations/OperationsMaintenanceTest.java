package com.antshorttv.operations;

import static org.assertj.core.api.Assertions.*;
import com.antshorttv.monitoring.MonitoringProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OperationsMaintenanceTest {
    @Test void retainsActiveSettlementEvidenceAndLastBootOfADeadInstance(){
        var jdbc=OperationsTestDatabase.create();var db=new OperationsDatabase(jdbc);
        var settings=new OperationsSettingsService(db,new ObjectMapper(),new MonitoringProperties(false,"","","test","one",10,true));settings.current();
        Instant now=Instant.now();var old=Timestamp.from(now.minusSeconds(100L*86400));
        jdbc.update("insert into ops_execution_observation(execution_id,execution_version,status,point_settlement_status,observed_at) values(1,1,'PENDING','NONE',?),(2,1,'SUCCEEDED','RESERVED',?),(3,1,'FAILED','NONE',?),(4,1,'RUNNING','NONE',?)",old,old,old,old);
        jdbc.update("insert into ops_job_heartbeat(instance_id,boot_id,job_code,enabled,updated_at) values('same','old','job',true,?),('same','new','job',true,?),('dead','last','job',true,?)",old,Timestamp.from(now),old);
        new OperationsMaintenance(db,settings).cleanup(now);
        assertThat(jdbc.queryForList("select execution_id from ops_execution_observation order by execution_id",Long.class)).containsExactly(1L,2L,4L);
        assertThat(jdbc.queryForList("select boot_id from ops_job_heartbeat order by boot_id",String.class)).containsExactly("last","new");
    }
    @Test void cleanupOnlyOperationsAndPreservesActiveIncidentsAndTheirActions() {
        var jdbc=OperationsTestDatabase.create();var db=new OperationsDatabase(jdbc);
        var settings=new OperationsSettingsService(db,new ObjectMapper(),new MonitoringProperties(false,"","","test","one",10,true));settings.current();
        Instant now=Instant.now(),old=now.minusSeconds(100L*86400);var at=Timestamp.from(old);
        jdbc.execute("create table business_evidence(id bigint)");jdbc.update("insert into business_evidence values(1)");
        jdbc.update("insert into ops_event(event_key,occurred_at,source) values('old',?,'API')",at);
        jdbc.update("insert into ops_alert_event(event_key,rule_id,rule_name,severity,status,sample_count,fired_at,updated_at) values('active',1,'active','WARNING','FIRING',1,?,?)",at,at);
        long active=jdbc.queryForObject("select id from ops_alert_event",Long.class);
        jdbc.update("insert into ops_alert_action(event_id,action,created_at) values(?,'ACKNOWLEDGE',?)",active,at);
        var maintenance=new OperationsMaintenance(db,settings);maintenance.cleanup(now);
        assertThat(jdbc.queryForObject("select count(*) from ops_event",Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from ops_alert_event",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from ops_alert_action",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from business_evidence",Long.class)).isEqualTo(1);
    }
}
