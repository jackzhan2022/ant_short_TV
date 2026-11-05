package com.antshorttv.operations.alerts;

import com.antshorttv.monitoring.FeishuWebhookClient;
import com.antshorttv.operations.OperationsDatabase;
import com.antshorttv.operations.OperationsSettingsService;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import static com.antshorttv.operations.alerts.OperationsAlertService.ts;

/** Claim and finish in separate short fenced transactions; never hold a connection during HTTP. */
@Service
public class OperationsNotificationWorker {
    private final OperationsDatabase database;
    private final OperationsSettingsService settings;
    private final FeishuWebhookClient client;
    private final OperationsLease leases;
    public OperationsNotificationWorker(OperationsDatabase database,OperationsSettingsService settings,FeishuWebhookClient client) {
        this.database=database; this.settings=settings; this.client=client; leases=new OperationsLease(database);
    }

    public void deliver(Instant now) {
        long started=System.nanoTime();
        var lease=leases.acquire("notifications-deliver",settings.instanceId(),now,Duration.ofSeconds(120));
        if(lease==null) return;
        try {
            boolean immediate=settings.notificationsEnabled(now);
            var due=database.jdbc().queryForList("select n.id from ops_notification_outbox n where n.attempts<5 and ((n.status='PENDING' and n.next_attempt_at<=?) or (n.status='SENDING' and n.lease_expires_at<=?)) and (n.transition_type='DIGEST' or ?=true) and (n.event_id is null or exists(select 1 from ops_alert_event e where e.id=n.event_id and (e.silence_until is null or e.silence_until<=?))) order by n.id limit 10",
                ts(now),ts(now),immediate,ts(now));
            for(var row:due) {
                Instant claimAt=now.plusNanos(System.nanoTime()-started);
                String owner=settings.instanceId()+":"+UUID.randomUUID();
                Long id=((Number)row.get("id")).longValue();
                var claim=leases.fenced(lease,claimAt,() -> {
                    int updated=database.jdbc().update("update ops_notification_outbox set status='SENDING',attempts=attempts+1,lease_owner=?,lease_expires_at=?,updated_at=? where id=? and attempts<5 and ((status='PENDING' and next_attempt_at<=?) or (status='SENDING' and lease_expires_at<=?)) and (event_id is null or exists(select 1 from ops_alert_event e where e.id=ops_notification_outbox.event_id and (e.silence_until is null or e.silence_until<=?)))",
                        owner,ts(claimAt.plusSeconds(30)),ts(claimAt),id,ts(claimAt),ts(claimAt),ts(claimAt));
                    return updated==0?null:database.jdbc().queryForMap("select message,attempts from ops_notification_outbox where id=?",id);
                });
                if(claim==null) continue;
                boolean sent;
                try { sent=client.sendOperational(String.valueOf(claim.get("message"))); }
                catch(RuntimeException exception) { sent=false; }
                boolean success=sent;
                int attempts=((Number)claim.get("attempts")).intValue();
                Instant finished=now.plusNanos(System.nanoTime()-started);
                long delay=attempts==1?60:attempts==2?300:900;
                // Do not let a delayed response overwrite a reclaimed row or an expired claim.
                leases.fenced(lease,finished,() -> database.jdbc().update("update ops_notification_outbox set status=?,next_attempt_at=?,lease_owner=null,lease_expires_at=null,delivered_at=?,last_error=?,updated_at=? where id=? and lease_owner=? and status='SENDING' and lease_expires_at>?",
                    success?"DELIVERED":attempts>=5?"FAILED":"PENDING",success?null:ts(now.plusSeconds(delay)),success?ts(finished):null,
                    success?null:"Feishu delivery unsuccessful; remote delivery may be uncertain",ts(finished),id,owner,ts(finished)));
            }
            Instant finished=now.plusNanos(System.nanoTime()-started);
            leases.fenced(lease,finished,() -> database.jdbc().update("update ops_notification_outbox set status='FAILED',lease_owner=null,lease_expires_at=null,last_error='Delivery attempts exhausted after expired claim',updated_at=? where status='SENDING' and attempts>=5 and lease_expires_at<=?",ts(finished),ts(finished)));
        } finally { leases.release(lease,now); }
    }
}
