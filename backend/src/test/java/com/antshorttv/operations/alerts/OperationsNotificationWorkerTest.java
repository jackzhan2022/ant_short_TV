package com.antshorttv.operations.alerts;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.antshorttv.operations.*;
import com.antshorttv.monitoring.FeishuWebhookClient;
import java.sql.Timestamp;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;

class OperationsNotificationWorkerTest {
    JdbcTemplate jdbc; OperationsDatabase database; OperationsSettingsService settings;
    FeishuWebhookClient client; OperationsNotificationWorker worker;
    Instant now = Instant.parse("2026-11-05T01:00:00Z");
    @BeforeEach void setup() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:outbox" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc = new JdbcTemplate(ds);
        OperationsAlertTestSchema.permissions(jdbc);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V174__platform_operations_monitoring.sql")).execute(ds);
        database = new OperationsDatabase(jdbc); settings=mock(OperationsSettingsService.class);
        when(settings.instanceId()).thenReturn("one"); when(settings.notificationsEnabled(any())).thenReturn(true);
        client=mock(FeishuWebhookClient.class);
        worker=new OperationsNotificationWorker(database,settings,client);
        jdbc.update("insert into ops_notification_outbox(notification_key,transition_type,message,status,attempts,next_attempt_at,created_at,updated_at) values(?,?,?,'PENDING',0,?,?,?)", "event:12:FIRING:feishu", "FIRING", "[OPS-12/FIRING] Fault", Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
    }
    @Test void failureUsesBoundedOneFiveFifteenMinuteRetryAndStopsAfterFiveAttempts() {
        when(client.sendOperational(anyString())).thenReturn(false);
        worker.deliver(now);
        assertThat(next()).isEqualTo(now.plusSeconds(60));
        worker.deliver(now.plusSeconds(59)); verify(client,times(1)).sendOperational(anyString());
        worker.deliver(now.plusSeconds(60)); assertThat(next()).isEqualTo(now.plusSeconds(360));
        worker.deliver(now.plusSeconds(360)); assertThat(next()).isEqualTo(now.plusSeconds(1260));
        worker.deliver(now.plusSeconds(1260)); worker.deliver(now.plusSeconds(2160));
        assertThat(jdbc.queryForObject("select status from ops_notification_outbox",String.class)).isEqualTo("FAILED");
        worker.deliver(now.plusSeconds(5000)); verify(client,times(5)).sendOperational("[OPS-12/FIRING] Fault");
    }
    @Test void expiredClaimIsReclaimedAfterRestartUsingTheSameMessage() {
        jdbc.update("update ops_notification_outbox set status='SENDING',lease_owner='crashed',lease_expires_at=?",Timestamp.from(now.plusSeconds(30)));
        when(client.sendOperational(anyString())).thenReturn(true);
        worker.deliver(now); verifyNoInteractions(client);
        new OperationsNotificationWorker(database,settings,client).deliver(now.plusSeconds(31));
        verify(client).sendOperational("[OPS-12/FIRING] Fault");
        assertThat(jdbc.queryForObject("select status from ops_notification_outbox",String.class)).isEqualTo("DELIVERED");
    }
    @Test void remoteSendRunsOutsideTransactionAndSuccessDoesNotSendAgain() {
        when(client.sendOperational(anyString())).thenAnswer(call -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); return true;
        });
        worker.deliver(now); worker.deliver(now.plusSeconds(1));
        verify(client,times(1)).sendOperational(anyString());
    }
    @Test void observationPausesImmediateMessagesButKeepsLegacyDigestDelivery() {
        when(settings.notificationsEnabled(any())).thenReturn(false);
        worker.deliver(now); verifyNoInteractions(client);
        jdbc.update("update ops_notification_outbox set transition_type='DIGEST'");
        when(client.sendOperational(anyString())).thenReturn(true);
        worker.deliver(now); verify(client).sendOperational(anyString());
    }
    @Test void fifthAttemptExpiredClaimBecomesVisibleFailureWithoutSixthSend() {
        jdbc.update("update ops_notification_outbox set status='SENDING',attempts=5,lease_expires_at=?",Timestamp.from(now.minusSeconds(1)));
        worker.deliver(now);
        assertThat(jdbc.queryForObject("select status from ops_notification_outbox",String.class)).isEqualTo("FAILED");
        verifyNoInteractions(client);
    }
    @Test void delayedResponseCannotPublishOverAnotherWorkersClaim() {
        when(client.sendOperational(anyString())).thenAnswer(call -> {
            jdbc.update("update ops_notification_outbox set lease_owner='replacement',lease_expires_at=?",Timestamp.from(now.plusSeconds(60)));
            return true;
        });
        worker.deliver(now);
        assertThat(jdbc.queryForObject("select status from ops_notification_outbox",String.class)).isEqualTo("SENDING");
        assertThat(jdbc.queryForObject("select lease_owner from ops_notification_outbox",String.class)).isEqualTo("replacement");
        assertThat(jdbc.queryForObject("select delivered_at from ops_notification_outbox",Timestamp.class)).isNull();
    }
    @Test void queuedMessageRespectsSilenceAddedBeforeDeliveryAndResumesAfterExpiry() {
        jdbc.update("insert into ops_alert_event(event_key,rule_id,rule_name,severity,status,sample_count,fired_at,silence_until,updated_at) values('test-event',1,'HTTP','WARNING','FIRING',100,?,?,?)",
            Timestamp.from(now),Timestamp.from(now.plusSeconds(300)),Timestamp.from(now));
        jdbc.update("update ops_notification_outbox set event_id=(select id from ops_alert_event where event_key='test-event')");
        worker.deliver(now);verifyNoInteractions(client);
        when(client.sendOperational(anyString())).thenReturn(true);
        worker.deliver(now.plusSeconds(301));verify(client).sendOperational(anyString());
    }
    Instant next() { return jdbc.queryForObject("select next_attempt_at from ops_notification_outbox",Timestamp.class).toInstant(); }
}
