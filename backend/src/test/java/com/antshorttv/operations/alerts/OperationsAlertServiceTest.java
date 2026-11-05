package com.antshorttv.operations.alerts;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.antshorttv.common.BusinessException;
import com.antshorttv.operations.*;
import com.antshorttv.monitoring.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.server.ResponseStatusException;

class OperationsAlertServiceTest {
    JdbcTemplate jdbc;
    OperationsDatabase database;
    OperationsSettingsService settings;
    OperationsMetricSource source;
    OperationsAlertService service;
    Instant end = Instant.parse("2026-11-05T01:00:00Z");
    @BeforeEach void setup() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:alerts" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        OperationsAlertTestSchema.permissions(jdbc);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V174__platform_operations_monitoring.sql")).execute(ds);
        database = new OperationsDatabase(jdbc);
        settings = mock(OperationsSettingsService.class);
        when(settings.instanceId()).thenReturn("test-instance");
        when(settings.notificationsEnabled(any())).thenReturn(true);
        source = mock(OperationsMetricSource.class);
        when(source.sample(anyString(), anyString(), anyInt(), any())).thenReturn(new OperationsMetricSource.Sample(10d, 100, true));
        service = new OperationsAlertService(database, settings, source, new ObjectMapper(),Clock.fixed(end,ZoneOffset.UTC));
    }
    long rule(int sustained) {
        var body = new LinkedHashMap<String,Object>();
        body.put("name", "HTTP failure"); body.put("metric", "http.errorRate"); body.put("thresholdValue", 5d);
        body.put("recoveryThreshold", 2d); body.put("sustainedMinutes", sustained); body.put("minimumSamples", 100);
        body.put("windowMinutes", 1); body.put("cooldownMinutes", 30); body.put("notify", true);
        return ((Number)service.saveRule(null, body).get("id")).longValue();
    }
    @Test void sameClosedWindowCannotAdvanceEvidenceTwiceAndRestartResumesIncident() {
        rule(2);
        service.evaluate(end.plusSeconds(15)); service.evaluate(end.plusSeconds(45));
        assertThat(count("ops_alert_event")).isZero();
        new OperationsAlertService(database, settings, source, new ObjectMapper()).evaluate(end.plusSeconds(75));
        assertThat(count("ops_alert_event")).isEqualTo(1);
        service.evaluate(end.plusSeconds(135));
        assertThat(count("ops_alert_event")).isEqualTo(1);
        assertThat(count("ops_notification_outbox")).isEqualTo(1);
    }
    @Test void lowSamplesAndMissingMinutesInterruptPendingEvidence() {
        rule(2); service.evaluate(end);
        when(source.sample(anyString(), anyString(), anyInt(), any())).thenReturn(new OperationsMetricSource.Sample(100d, 1, true));
        service.evaluate(end.plusSeconds(60));
        assertThat(jdbc.queryForObject("select status from ops_alert_state", String.class)).isEqualTo("UNKNOWN");
        when(source.sample(anyString(), anyString(), anyInt(), any())).thenReturn(new OperationsMetricSource.Sample(10d, 100, true));
        service.evaluate(end.plusSeconds(120));
        assertThat(count("ops_alert_event")).isZero();
        service.evaluate(end.plusSeconds(240));
        assertThat(count("ops_alert_event")).isZero();
        service.evaluate(end.plusSeconds(300));
        assertThat(count("ops_alert_event")).isEqualTo(1);
    }
    @Test void noDataNeverRecoversAndTwoConsecutiveHealthyWindowsResolveWithoutReplacingTriggerEvidence() {
        rule(1); service.evaluate(end);
        long id = jdbc.queryForObject("select id from ops_alert_event", Long.class);
        when(source.sample(anyString(), anyString(), anyInt(), any())).thenReturn(new OperationsMetricSource.Sample(null, 0, false));
        service.evaluate(end.plusSeconds(60));
        assertThat(jdbc.queryForObject("select status from ops_alert_event", String.class)).isEqualTo("FIRING");
        when(source.sample(anyString(), anyString(), anyInt(), any())).thenReturn(new OperationsMetricSource.Sample(0d, 100, true));
        service.evaluate(end.plusSeconds(120));
        assertThat(jdbc.queryForObject("select status from ops_alert_event", String.class)).isEqualTo("FIRING");
        service.evaluate(end.plusSeconds(180));
        assertThat(jdbc.queryForObject("select status from ops_alert_event", String.class)).isEqualTo("RESOLVED");
        assertThat(jdbc.queryForObject("select metric_value from ops_alert_event where id=?", Double.class,id)).isEqualTo(10d);
        assertThat(count("ops_notification_outbox")).isEqualTo(2);
    }
    @Test void acknowledgementAndExpiringSilenceDoNotResolveAndReminderRespectsCooldown() {
        rule(1); service.evaluate(end);
        long id = jdbc.queryForObject("select id from ops_alert_event", Long.class);
        service.acknowledge(id, 12L, "Investigating");
        service.silence(id,12L,end.plusSeconds(3600),"maintenance");
        service.evaluate(end.plusSeconds(1800));
        assertThat(count("ops_notification_outbox")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from ops_alert_event", String.class)).isEqualTo("FIRING");
        service.evaluate(end.plusSeconds(3660));
        assertThat(count("ops_alert_event")).isEqualTo(1);
        assertThat(count("ops_notification_outbox")).isEqualTo(2);
        assertThat(count("ops_alert_action")).isEqualTo(2);
    }
    @Test void observationStillCreatesIncidentsButSuppressesImmediateNotifications() {
        when(settings.notificationsEnabled(any())).thenReturn(false);
        rule(1); service.evaluate(end);
        assertThat(count("ops_alert_event")).isEqualTo(1);
        assertThat(count("ops_notification_outbox")).isZero();
    }
    @Test void ruleValidationAndOptimisticVersionPreventBadRulesAndLostUpdates() {
        long id = rule(1);
        var saved = service.rules().get(0);
        var body = new LinkedHashMap<>(saved); body.put("thresholdValue", 20d);
        service.saveRule(id,body);
        assertThatThrownBy(() -> service.saveRule(id,body)).isInstanceOf(ResponseStatusException.class);
        body.put("metric", "arbitrary.sql");
        assertThatThrownBy(() -> service.saveRule(null,body)).isInstanceOf(BusinessException.class);
        body.put("metric", "http.errorRate"); body.put("windowMinutes", 0);
        assertThatThrownBy(() -> service.saveRule(null,body)).isInstanceOf(BusinessException.class);
    }
    @Test void healthyWindowsSeparatedByGapDoNotRecover() {
        rule(1); service.evaluate(end);
        when(source.sample(anyString(),anyString(),anyInt(),any())).thenReturn(new OperationsMetricSource.Sample(0d,100,true));
        service.evaluate(end.plusSeconds(60)); service.evaluate(end.plusSeconds(180));
        assertThat(jdbc.queryForObject("select status from ops_alert_event",String.class)).isEqualTo("FIRING");
        service.evaluate(end.plusSeconds(240));
        assertThat(jdbc.queryForObject("select status from ops_alert_event",String.class)).isEqualTo("RESOLVED");
    }
    @Test void remindersAreCappedAtTwoPerRollingHour() {
        long id=rule(1);
        var saved=service.rules().get(0); saved.put("cooldownMinutes",1); service.saveRule(id,saved);
        service.evaluate(end); service.evaluate(end.plusSeconds(60)); service.evaluate(end.plusSeconds(120));
        assertThat(count("ops_notification_outbox")).isEqualTo(2);
        service.evaluate(end.plusSeconds(3600));
        assertThat(count("ops_notification_outbox")).isEqualTo(3);
    }
    @Test void explicitRuleScopesRejectInapplicableDimensionsAndDefaultsAreIdempotent() {
        assertThatThrownBy(()->service.saveRule(null,Map.of("name","bad","metric","jvm.heapPercent","thresholdValue",85,"dimensionsJson","{\"modelId\":1}"))).isInstanceOf(BusinessException.class);
        service.initializeDefaults(); service.initializeDefaults();
        assertThat(service.rules()).hasSize(11);
        assertThat(service.rules()).anySatisfy(row -> {
            assertThat(row.get("metric")).isEqualTo("http.errorRate");
            assertThat(row.get("minimumSamples")).isEqualTo(100L);
        });
    }
    @Test void editingAnActiveRulePreservesIncidentAndInterruptsPendingRecovery() {
        long id=rule(1); service.evaluate(end);
        long event=jdbc.queryForObject("select id from ops_alert_event",Long.class);
        var edited=service.rules().get(0); edited.put("thresholdValue",15d); service.saveRule(id,edited);
        service.evaluate(end.plusSeconds(60));
        assertThat(jdbc.queryForObject("select active_event_id from ops_alert_state",Long.class)).isEqualTo(event);
        assertThat(count("ops_alert_event")).isEqualTo(1);
    }
    @Test void activeIncidentCannotBeMovedToAnotherMetricOrScopeByEditingRule() {
        long id=rule(1);service.evaluate(end);
        var edited=service.rules().get(0);edited.put("metric","http.p95Ms");
        assertThatThrownBy(()->service.saveRule(id,edited)).isInstanceOf(ResponseStatusException.class);
        edited.put("metric","http.errorRate");edited.put("dimensionsJson","{\"route\":\"/api/users\"}");
        assertThatThrownBy(()->service.saveRule(id,edited)).isInstanceOf(ResponseStatusException.class);
        assertThat(service.rules().get(0).get("dimensionsJson")).isEqualTo("{}");
    }
    @Test void twoInstancesEvaluatingConcurrentlyCreateOneIncidentAndOneNotification() throws Exception {
        rule(1);
        var secondSettings=mock(OperationsSettingsService.class);when(secondSettings.instanceId()).thenReturn("second");
        when(secondSettings.notificationsEnabled(any())).thenReturn(true);
        var second=new OperationsAlertService(database,secondSettings,source,new ObjectMapper());
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var gate=new java.util.concurrent.CountDownLatch(1);
            var one=executor.submit(()->{try{gate.await();}catch(InterruptedException e){throw new RuntimeException(e);}service.evaluate(end);});
            var two=executor.submit(()->{try{gate.await();}catch(InterruptedException e){throw new RuntimeException(e);}second.evaluate(end);});
            gate.countDown();one.get(5,java.util.concurrent.TimeUnit.SECONDS);two.get(5,java.util.concurrent.TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
        assertThat(count("ops_alert_event")).isEqualTo(1);assertThat(count("ops_notification_outbox")).isEqualTo(1);
    }
    @Test void lessThanRuleRequiresOppositeRecoveryDirectionAndFiniteThresholds() {
        var body=new LinkedHashMap<String,Object>();body.put("name","low sample");body.put("metric","jvm.heapPercent");
        body.put("operator","LT");body.put("thresholdValue",15d);body.put("recoveryThreshold",20d);body.put("sustainedMinutes",1);
        service.saveRule(null,body);service.evaluate(end);
        assertThat(count("ops_alert_event")).isEqualTo(1);
        when(source.sample(anyString(),anyString(),anyInt(),any())).thenReturn(new OperationsMetricSource.Sample(25d,100,true));
        service.evaluate(end.plusSeconds(60));service.evaluate(end.plusSeconds(120));
        assertThat(jdbc.queryForObject("select status from ops_alert_event",String.class)).isEqualTo("RESOLVED");
        body.put("thresholdValue",Double.NaN);assertThatThrownBy(()->service.saveRule(null,body)).isInstanceOf(BusinessException.class);
    }
    @Test void tenSecondGraceDoesNotConsumeTheMinuteBeforeItsFinalFlush() {
        rule(1);service.evaluate(end.plusSeconds(5));
        verify(source).sample(eq("http.errorRate"),eq("{}"),eq(1),eq(end.minusSeconds(60)));
        service.evaluate(end.plusSeconds(15));
        verify(source).sample(eq("http.errorRate"),eq("{}"),eq(1),eq(end));
    }
    @Test void phaseAgeRuleRequiresAnExplicitPhaseAndOtherMetricsCannotUsePhaseScope() {
        var body=new LinkedHashMap<String,Object>();body.put("name","phase timeout");body.put("metric","execution.phaseAgeSeconds");body.put("thresholdValue",600d);
        assertThatThrownBy(()->service.saveRule(null,body)).isInstanceOf(BusinessException.class);
        body.put("dimensionsJson","{\"phase\":\"PROVIDER_POLLING\",\"tenantId\":8}");
        assertThat(service.saveRule(null,body).get("dimensionsJson")).isEqualTo("{\"phase\":\"PROVIDER_POLLING\",\"tenantId\":8}");
        body.put("metric","execution.expiredLeases");assertThatThrownBy(()->service.saveRule(null,body)).isInstanceOf(BusinessException.class);
    }
    int count(String table) { return jdbc.queryForObject("select count(*) from " + table,Integer.class); }
}
