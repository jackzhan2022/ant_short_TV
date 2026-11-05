package com.antshorttv.operations.telemetry;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.antshorttv.execution.AiExecutionPools;
import com.antshorttv.operations.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class OperationsWorkerTest {
    @Test void resourceSamplerPersistsMinuteAndHourAndUsesObservedCacheCounters() throws Exception {
        var jdbc=OperationsTestDatabase.create();var settings=mock(OperationsSettingsService.class);when(settings.instanceId()).thenReturn("test");
        var telemetry=new OperationsTelemetry(new OperationsDatabase(jdbc),settings,new ObjectMapper());
        var registry=new SimpleMeterRegistry();registry.counter("antv.query.cache","region","TASKS","outcome","hit").increment();
        ObjectProvider<MeterRegistry> meters=mock(ObjectProvider.class);when(meters.getIfAvailable()).thenReturn(registry);
        ObjectProvider<AiExecutionPools> pools=mock(ObjectProvider.class);
        var source=mock(DataSource.class);
        var worker=new OperationsWorker(telemetry,mock(OperationsProjections.class),mock(OperationsJobObserver.class),source,meters,pools,true);
        try {
            worker.sample();telemetry.flush();
            assertThat(jdbc.queryForObject("select count(*) from ops_metric_bucket where family='RESOURCE'",Long.class)).isEqualTo(2);
            var json=jdbc.queryForObject("select values_json from ops_metric_bucket where family='RESOURCE' and granularity='HOUR'",String.class);
            assertThat(new ObjectMapper().readTree(json).path("redisStatus").asText()).isEqualTo("UP");
            assertThat(new ObjectMapper().readTree(json).path("monitoringDropped").isNumber()).isTrue();
            registry.counter("antv.query.cache","region","TASKS","outcome","unavailable").increment();
            worker.sample();telemetry.flush();
            json=jdbc.queryForObject("select values_json from ops_metric_bucket where family='RESOURCE' and granularity='HOUR'",String.class);
            assertThat(new ObjectMapper().readTree(json).path("redisStatus").asText()).isEqualTo("ERROR");
            worker.sample();telemetry.flush();
            json=jdbc.queryForObject("select values_json from ops_metric_bucket where family='RESOURCE' and granularity='HOUR'",String.class);
            assertThat(new ObjectMapper().readTree(json).path("redisStatus").asText()).isEqualTo("UNKNOWN");
            verifyNoInteractions(source);
        }finally{worker.stop();registry.close();}
    }
}
