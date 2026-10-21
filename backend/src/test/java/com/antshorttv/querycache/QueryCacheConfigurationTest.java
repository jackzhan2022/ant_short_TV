package com.antshorttv.querycache;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class QueryCacheConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
        .withUserConfiguration(QueryCacheConfiguration.class)
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

    @Test void disabledByDefaultDoesNotCreateRedisResources() {
        context.run(app -> {
            assertThat(app).hasNotFailed().doesNotHaveBean(CacheStore.class);
            assertThat(app.getBean(QueryResultCache.class).read(QueryResultCache.Region.TASKS,
                "key",String.class,()->"database")).isEqualTo("database");
        });
    }
    @Test void enabledCacheRequiresEncryptionKey() {
        context.withPropertyValues("query-cache.enabled=true").run(app ->
            assertThat(app).hasFailed().getFailure().hasRootCauseMessage("Query cache encryption key must be configured"));
    }
}
