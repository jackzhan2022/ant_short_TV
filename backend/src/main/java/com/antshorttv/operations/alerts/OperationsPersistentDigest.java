package com.antshorttv.operations.alerts;

import com.antshorttv.monitoring.MonitoringDigestFormatter;
import com.antshorttv.monitoring.MonitoringEvent;
import com.antshorttv.monitoring.MonitoringProperties;
import com.antshorttv.operations.OperationsDatabase;
import com.antshorttv.operations.OperationsSettingsService;
import com.antshorttv.operations.OperationsTime;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import static com.antshorttv.operations.alerts.OperationsAlertService.ts;

/** Replaces the old drain/AI scan together, using a durable window cursor and outbox. */
@Service
@DependsOnDatabaseInitialization
public class OperationsPersistentDigest {
    private final OperationsDatabase database;
    private final OperationsSettingsService settings;
    private final MonitoringProperties properties;
    private final MonitoringDigestFormatter formatter;
    private final OperationsLease leases;
    public OperationsPersistentDigest(OperationsDatabase database,OperationsSettingsService settings,
        MonitoringProperties properties,MonitoringDigestFormatter formatter) {
        this.database=database; this.settings=settings; this.properties=properties; this.formatter=formatter;
        leases=new OperationsLease(database);
    }
    public boolean enabled() {
        return settings.persistentDigestEnabled();
    }
    public boolean published(Instant end) {
        var cursor=database.jdbc().queryForList("select value_json from ops_runtime_setting where setting_key='digest:last-window'");
        return !cursor.isEmpty() && !end.isAfter(Instant.parse(String.valueOf(cursor.get(0).get("value_json"))));
    }
    public void publish(Instant now) {
        if(!enabled() || !properties.enabled()) return;
        Instant end=windowEnd(now),start=end.minusSeconds(3600);
        var lease=leases.acquire("monitoring-hourly-digest",settings.instanceId(),now,Duration.ofSeconds(20));
        if(lease==null) return;
        long started=System.nanoTime();
        try {
            String eventScope="occurred_at>=? and occurred_at<?"+(properties.includeAiTasks()?"":" and source<>'AI_EXECUTION'");
            // All fields are already sanitized on capture; format sanitizes again at delivery boundaries.
            var rows=database.jdbc().queryForList("select occurred_at,source,code,location,message,stack,user_id,tenant_id,project_id,execution_id from ops_event where "+eventScope+" order by id limit ?",
                ts(start),ts(end),Math.min(properties.maxEvents(),1000));
            List<MonitoringEvent> events=rows.stream().map(row -> new MonitoringEvent(OperationsTime.utc(row.get("occurred_at")),
                string(row,"source"),string(row,"code"),string(row,"location"),string(row,"message"),string(row,"stack"),
                id(row,"user_id"),id(row,"tenant_id"),id(row,"project_id"),id(row,"execution_id"))).toList();
            long total=database.jdbc().queryForObject("select count(*) from ops_event where "+eventScope,Long.class,ts(start),ts(end));
            var messages=formatter.format(start,end,events,Math.max(0,total-events.size()));
            leases.fenced(lease,now.plusNanos(System.nanoTime()-started),() -> {
                var cursor=database.jdbc().queryForList("select value_json from ops_runtime_setting where setting_key='digest:last-window' for update");
                if(!cursor.isEmpty() && !end.isAfter(Instant.parse(String.valueOf(cursor.get(0).get("value_json"))))) return null;
                for(int part=0;part<messages.size();part++) {
                    String key="digest:"+end.getEpochSecond()+":"+(part+1)+":feishu";
                    database.jdbc().update("insert into ops_notification_outbox(notification_key,transition_type,message,status,attempts,next_attempt_at,created_at,updated_at) values(?,'DIGEST',?,'PENDING',0,?,?,?)",
                        key,"[OPS-DIGEST-"+end.getEpochSecond()+"/"+(part+1)+"]\n"+messages.get(part),ts(now),ts(now),ts(now));
                }
                if(cursor.isEmpty()) database.jdbc().update("insert into ops_runtime_setting(setting_key,value_json,version,updated_at) values('digest:last-window',?,0,?)",end.toString(),ts(now));
                else database.jdbc().update("update ops_runtime_setting set value_json=?,version=version+1,updated_at=? where setting_key='digest:last-window'",end.toString(),ts(now));
                return null;
            });
        } finally { leases.release(lease,now); }
    }
    public static Instant windowEnd(Instant now) {
        var end=now.atZone(ZoneId.of("Asia/Shanghai")).withMinute(30).withSecond(0).withNano(0);
        return(end.toInstant().isAfter(now)?end.minusHours(1):end).toInstant();
    }
    private static String string(Map<String,Object> row,String name) { return row.get(name)==null?"":String.valueOf(row.get(name)); }
    private static Long id(Map<String,Object> row,String name) { return row.get(name)==null?null:((Number)row.get(name)).longValue(); }
}
