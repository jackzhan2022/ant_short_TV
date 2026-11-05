package com.antshorttv.operations;

import com.antshorttv.operations.alerts.OperationsLease;
import com.antshorttv.operations.telemetry.OperationsJobObserver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OperationsMaintenance {
    private final OperationsDatabase database;
    private final OperationsSettingsService settings;
    private final OperationsLease leases;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"operations-cleanup");t.setDaemon(true);return t;});
    private OperationsJobObserver jobs;
    public OperationsMaintenance(OperationsDatabase database,OperationsSettingsService settings){this.database=database;this.settings=settings;leases=new OperationsLease(database);}
    @Autowired void jobs(OperationsJobObserver jobs){this.jobs=jobs;}
    @PostConstruct void start(){worker.scheduleWithFixedDelay(()->{if(jobs!=null)jobs.started("monitoring.cleanup");try{cleanup(Instant.now());if(jobs!=null)jobs.finished("monitoring.cleanup",null);}catch(RuntimeException e){if(jobs!=null)jobs.finished("monitoring.cleanup",e);}},60,3600,TimeUnit.SECONDS);}
    private record Retention(String table,String id,String predicate,Instant cutoff){}
    public int cleanup(Instant now){
        var lease=leases.acquire("operations-cleanup",settings.instanceId(),now,Duration.ofSeconds(30));if(lease==null)return 0;
        try{
            var config=settings.current();var rules=List.of(
                retention("ops_metric_bucket","id","granularity='MINUTE' and bucket_at<?",config,"minuteRetentionDays",now),
                retention("ops_metric_bucket","id","granularity='HOUR' and bucket_at<?",config,"hourlyRetentionDays",now),
                retention("ops_event","id","occurred_at<?",config,"eventRetentionDays",now),
                retention("ops_ai_call_projection","call_log_id","occurred_at<?",config,"projectionRetentionDays",now),
                retention("ops_execution_observation","id","observed_at<? and status not in ('PENDING','RUNNING') and coalesce(point_settlement_status,'')<>'RESERVED'",config,"projectionRetentionDays",now),
                retention("ops_job_heartbeat","id","updated_at<? and exists (select 1 from ops_job_heartbeat newer where newer.instance_id=ops_job_heartbeat.instance_id and newer.boot_id<>ops_job_heartbeat.boot_id and newer.updated_at>ops_job_heartbeat.updated_at)",config,"minuteRetentionDays",now),
                retention("ops_notification_outbox","id","created_at<? and (event_id is null or event_id not in (select id from ops_alert_event where resolved_at is null))",config,"notificationRetentionDays",now),
                retention("ops_alert_action","id","created_at<? and event_id not in (select id from ops_alert_event where resolved_at is null)",config,"alertRetentionDays",now),
                retention("ops_alert_event","id","resolved_at is not null and resolved_at<? and id not in (select active_event_id from ops_alert_state where active_event_id is not null)",config,"alertRetentionDays",now));
            int deleted=0,batches=0;boolean more=true;long started=System.nanoTime();
            while(more&&batches<20){more=false;for(var rule:rules){if(batches>=20)break;
                Instant at=now.plusNanos(System.nanoTime()-started);
                int count=leases.fenced(lease,at,()->{
                    var ids=database.jdbc().queryForList("select "+rule.id()+" from "+rule.table()+" where "+rule.predicate()+" order by "+rule.id()+" limit 500",Long.class,Timestamp.from(rule.cutoff()));
                    if(ids.isEmpty())return 0;
                    String placeholders=String.join(",",Collections.nCopies(ids.size(),"?"));
                    return database.jdbc().update("delete from "+rule.table()+" where "+rule.id()+" in ("+placeholders+")",ids.toArray());
                });batches++;deleted+=count;if(count==500)more=true;
            }}return deleted;
        }finally{leases.release(lease,now);}
    }
    private Retention retention(String table,String id,String predicate,Map<String,Object> config,String field,Instant now){return new Retention(table,id,predicate,now.minus(Duration.ofDays(((Number)config.get(field)).intValue())));}
    @PreDestroy void stop(){worker.shutdownNow();}
}
