package com.antshorttv.operations.alerts;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.antshorttv.operations.*;
import com.antshorttv.monitoring.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;

class OperationsPersistentDigestTest {
    JdbcTemplate jdbc; OperationsDatabase database; OperationsSettingsService settings; MonitoringProperties properties;
    OperationsPersistentDigest digest; MonitoringDigestFormatter formatter;
    Instant end=Instant.parse("2026-11-05T01:30:00Z");
    @BeforeEach void setup() {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:digest"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(ds);OperationsAlertTestSchema.permissions(jdbc);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V174__platform_operations_monitoring.sql")).execute(ds);
        database=new OperationsDatabase(jdbc);settings=mock(OperationsSettingsService.class);
        when(settings.instanceId()).thenReturn("one");when(settings.telemetryEnabled()).thenReturn(true);
        when(settings.persistentDigestEnabled()).thenAnswer(call -> settings.telemetryEnabled());
        properties=new MonitoringProperties(true,"","","test","one",100,true);
        formatter=new MonitoringDigestFormatter(properties);
        digest=new OperationsPersistentDigest(database,settings,properties,formatter);
    }
    @Test void persistedWindowIsSentOnceAcrossRestartAndEventsAreNotDrained() {
        jdbc.update("insert into ops_event(event_key,occurred_at,source,code,location,message) values('one',?,'AI_EXECUTION','FAILED','task','failure')",Timestamp.from(end.minusSeconds(60)));
        digest.publish(end.plusSeconds(10));
        new OperationsPersistentDigest(database,settings,properties,formatter).publish(end.plusSeconds(20));
        assertThat(jdbc.queryForObject("select count(*) from ops_notification_outbox",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from ops_event",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select transition_type from ops_notification_outbox",String.class)).isEqualTo("DIGEST");
        assertThat(jdbc.queryForObject("select message from ops_notification_outbox",String.class)).contains("AI_EXECUTION");
        assertThat(digest.published(end)).isTrue();assertThat(digest.published(end.plusSeconds(3600))).isFalse();
        assertThat(OperationsPersistentDigest.windowEnd(end.minusMillis(1))).isEqualTo(end.minusSeconds(3600));
    }
    @Test void turningOffTelemetryRestoresLegacyModeAndDisabledLegacyFlagDoesNotEnqueue() {
        when(settings.telemetryEnabled()).thenReturn(false);assertThat(digest.enabled()).isFalse();
        when(settings.telemetryEnabled()).thenReturn(true);
        properties=new MonitoringProperties(false,"","","test","one",100,true);
        digest=new OperationsPersistentDigest(database,settings,properties,formatter);
        assertThat(digest.enabled()).isTrue();digest.publish(end.plusSeconds(10));
        assertThat(jdbc.queryForObject("select count(*) from ops_runtime_setting",Integer.class)).isZero();
    }
    @Test void disabledAiTasksAreExcludedBeforeEventLimitAndDoNotCountAsDropped() {
        jdbc.update("insert into ops_event(event_key,occurred_at,source,code,location,message) values('ai',?,'AI_EXECUTION','AI_FAILED','task','ai-failure')",Timestamp.from(end.minusSeconds(60)));
        jdbc.update("insert into ops_event(event_key,occurred_at,source,code,location,message) values('http',?,'HTTP','SERVER_ERROR','route','http-failure')",Timestamp.from(end.minusSeconds(30)));
        properties=new MonitoringProperties(true,"","","test","one",1,false);
        formatter=new MonitoringDigestFormatter(properties);
        digest=new OperationsPersistentDigest(database,settings,properties,formatter);
        digest.publish(end.plusSeconds(10));
        String message=jdbc.queryForObject("select message from ops_notification_outbox",String.class);
        assertThat(message).contains("http-failure","报错：1").doesNotContain("AI_EXECUTION","ai-failure","容量限制");
        assertThat(jdbc.queryForObject("select count(*) from ops_event",Integer.class)).isEqualTo(2);
    }
}
