package com.antshorttv.operations.telemetry;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.annotation.Scheduled;

class OperationsJobObserverTest {
    @Test void nativeCronDigestPublicationRecordsRegisteredHeartbeat() {
        var jdbc=com.antshorttv.operations.OperationsTestDatabase.create();var settings=mock(com.antshorttv.operations.OperationsSettingsService.class);when(settings.instanceId()).thenReturn("test");when(settings.persistentDigestEnabled()).thenReturn(true);
        var telemetry=new OperationsTelemetry(new com.antshorttv.operations.OperationsDatabase(jdbc),settings,new com.fasterxml.jackson.databind.ObjectMapper());
        var observer=new OperationsJobObserver(telemetry,mock(ApplicationContext.class),new MockEnvironment());observer.registerCron("monitoring.hourly-digest","10 30 * * * *",ZoneId.of("Asia/Shanghai"));
        observer.setMonitoringProperties(new com.antshorttv.monitoring.MonitoringProperties(true,"https://example.invalid/webhook","","test","one",10,true));
        var factory=new org.springframework.aop.aspectj.annotation.AspectJProxyFactory(mock(com.antshorttv.operations.alerts.OperationsPersistentDigest.class));factory.addAspect(observer);
        ((com.antshorttv.operations.alerts.OperationsPersistentDigest)factory.getProxy()).publish(Instant.now());observer.flush();
        assertThat(jdbc.queryForObject("select succeeded_at from ops_job_heartbeat where job_code='monitoring.hourly-digest'",java.sql.Timestamp.class)).isNotNull();
    }
    @Test void hourlyDigestIsDisabledUntilLegacyScheduleAndChannelAreActuallyConfigured() {
        var jdbc=com.antshorttv.operations.OperationsTestDatabase.create();var settings=mock(com.antshorttv.operations.OperationsSettingsService.class);when(settings.instanceId()).thenReturn("test");when(settings.persistentDigestEnabled()).thenReturn(true);
        var telemetry=new OperationsTelemetry(new com.antshorttv.operations.OperationsDatabase(jdbc),settings,new com.fasterxml.jackson.databind.ObjectMapper());
        var observer=new OperationsJobObserver(telemetry,mock(ApplicationContext.class),new MockEnvironment());observer.registerCron("monitoring.hourly-digest","10 30 * * * *",ZoneId.of("Asia/Shanghai"));
        observer.setMonitoringProperties(new com.antshorttv.monitoring.MonitoringProperties(false,"","","test","one",10,true));observer.flush();
        assertThat(jdbc.queryForObject("select enabled from ops_job_heartbeat",Boolean.class)).isFalse();
        observer.setMonitoringProperties(new com.antshorttv.monitoring.MonitoringProperties(true,"https://example.invalid/webhook","","test","one",10,true));observer.flush();
        assertThat(jdbc.queryForObject("select enabled from ops_job_heartbeat",Boolean.class)).isTrue();
    }
    @Test void scheduledWrapperRecordsServiceFailureEvenWhenSchedulerCatchesIt() {
        var jdbc=com.antshorttv.operations.OperationsTestDatabase.create();var settings=mock(com.antshorttv.operations.OperationsSettingsService.class);when(settings.instanceId()).thenReturn("test");
        var telemetry=new OperationsTelemetry(new com.antshorttv.operations.OperationsDatabase(jdbc),settings,new com.fasterxml.jackson.databind.ObjectMapper());
        var observer=new OperationsJobObserver(telemetry,mock(ApplicationContext.class),new MockEnvironment());observer.registerFixedDelay("CommercialSubscriptionGrantScheduler.processDueSubscriptions",60000);
        var service=mock(com.antshorttv.commercial.CommercialSubscriptionGrantService.class);doThrow(new IllegalStateException("password=secret")).when(service).processDue(any());
        var serviceFactory=new org.springframework.aop.aspectj.annotation.AspectJProxyFactory(service);serviceFactory.addAspect(observer);
        var scheduler=new com.antshorttv.commercial.CommercialSubscriptionGrantScheduler(serviceFactory.getProxy());
        var schedulerFactory=new org.springframework.aop.aspectj.annotation.AspectJProxyFactory(scheduler);schedulerFactory.addAspect(observer);
        ((com.antshorttv.commercial.CommercialSubscriptionGrantScheduler)schedulerFactory.getProxy()).processDueSubscriptions();observer.flush();
        var row=jdbc.queryForMap("select consecutive_failures,error_message,succeeded_at from ops_job_heartbeat");
        assertThat(((Number)row.get("consecutive_failures")).longValue()).isEqualTo(1);assertThat(row.get("error_message").toString()).doesNotContain("secret");assertThat(row.get("succeeded_at")).isNull();
        telemetry.flush();assertThat(jdbc.queryForObject("select count(*) from ops_event where source='JOB'",Long.class)).isEqualTo(1);
    }
    @Test void slowHeartbeatPersistenceDoesNotBlockBusinessJobStart() throws Exception {
        var database=mock(com.antshorttv.operations.OperationsDatabase.class);var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);when(database.jdbc()).thenReturn(jdbc);
        var telemetry=new OperationsTelemetry(database,mock(com.antshorttv.operations.OperationsSettingsService.class),new com.fasterxml.jackson.databind.ObjectMapper());
        var observer=new OperationsJobObserver(telemetry,mock(ApplicationContext.class),new MockEnvironment());observer.registerFixedDelay("business-job",1000);
        var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        doAnswer(invocation->{entered.countDown();release.await(2,java.util.concurrent.TimeUnit.SECONDS);return 1;}).when(jdbc).update(anyString(),any(Object[].class));
        var flushing=java.util.concurrent.CompletableFuture.runAsync(observer::flush);
        try {assertThat(entered.await(2,java.util.concurrent.TimeUnit.SECONDS)).isTrue();org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofMillis(200),()->observer.started("business-job"));}
        finally{release.countDown();flushing.get(2,java.util.concurrent.TimeUnit.SECONDS);}
    }
    static class Jobs {
        @Scheduled(fixedDelayString="${test.delay:10000}") void delayed() {}
        @Scheduled(cron="0 30 10 * * *") void cron() {}
        @Scheduled(cron="${test.cron:-}") void disabled() {}
    }
    @Test void resolvesDelayAndDistinguishesRunningFromNextExpectedStart() throws Exception {
        var observer=new OperationsJobObserver(mock(OperationsTelemetry.class),mock(ApplicationContext.class),new MockEnvironment().withProperty("test.delay","2000"));
        var job=observer.registered("test",Jobs.class.getDeclaredMethod("delayed").getAnnotation(Scheduled.class));
        assertThat(job.type).isEqualTo("FIXED_DELAY");assertThat(job.interval).isEqualTo(2000);assertThat(job.expected).isAfter(Instant.now());
    }
    @Test void cronUsesShanghaiAndDisabledExpressionHasNoExpectedRun() throws Exception {
        var observer=new OperationsJobObserver(mock(OperationsTelemetry.class),mock(ApplicationContext.class),new MockEnvironment());
        var original=java.util.TimeZone.getDefault();
        try {java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));var cron=observer.registered("cron",Jobs.class.getDeclaredMethod("cron").getAnnotation(Scheduled.class));assertThat(cron.zone).isEqualTo(ZoneId.of("Asia/Shanghai"));}
        finally {java.util.TimeZone.setDefault(original);}
        var disabled=observer.registered("disabled",Jobs.class.getDeclaredMethod("disabled").getAnnotation(Scheduled.class));
        assertThat(disabled.enabled).isFalse();assertThat(disabled.expected).isNull();
    }
    @Test void explicitlyRegisteredMonitoringJobsKeepTheirSchedules() {
        var jdbc=com.antshorttv.operations.OperationsTestDatabase.create();var settings=mock(com.antshorttv.operations.OperationsSettingsService.class);when(settings.instanceId()).thenReturn("test");
        var telemetry=new OperationsTelemetry(new com.antshorttv.operations.OperationsDatabase(jdbc),settings,new com.fasterxml.jackson.databind.ObjectMapper());
        var observer=new OperationsJobObserver(telemetry,mock(ApplicationContext.class),new MockEnvironment());
        observer.registerFixedDelay("monitoring.alert-evaluation",30000);
        observer.registerCron("monitoring.hourly-digest","10 30 * * * *",ZoneId.of("Asia/Shanghai"));
        observer.started("monitoring.alert-evaluation");observer.flush();
        assertThat(jdbc.queryForObject("select expected_at from ops_job_heartbeat where job_code='monitoring.alert-evaluation'",java.sql.Timestamp.class)).isNull();
        observer.finished("monitoring.alert-evaluation",null);observer.flush();
        assertThat(jdbc.queryForObject("select interval_ms from ops_job_heartbeat where job_code='monitoring.alert-evaluation'",Long.class)).isEqualTo(30000);
        assertThat(jdbc.queryForObject("select schedule_type from ops_job_heartbeat where job_code='monitoring.hourly-digest'",String.class)).isEqualTo("CRON");
    }
}
