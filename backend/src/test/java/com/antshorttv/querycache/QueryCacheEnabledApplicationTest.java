package com.antshorttv.querycache;

import com.antshorttv.AntShortTvApplication;
import com.antshorttv.user.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AntShortTvApplication.class, properties = {
    "query-cache.enabled=true",
    "query-cache.secret-key=enabled-context-test-key",
    "spring.data.redis.host=127.0.0.1",
    "spring.data.redis.port=1"
})
class QueryCacheEnabledApplicationTest {
    @Autowired ApplicationContext context;
    @Autowired QueryResultCache cache;
    @Autowired UserMapper users;

    @Test void enabledCacheStartsWithOnlyTheRealStoreAndDatabaseMappersStillWork() {
        assertThat(context.getBeansOfType(CacheStore.class)).hasSize(1)
            .containsKey("queryCacheStore");
        assertThat(context.containsBean("cacheStore")).isFalse();
        assertThat(cache.isEnabled()).isTrue();
        assertThat(users.selectCount(null)).isNotNull();
        assertThat(cache.read(QueryResultCache.Region.MODELS,"unavailable-redis",String.class,
            () -> "database-fallback")).isEqualTo("database-fallback");
    }
}
