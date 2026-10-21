package com.antshorttv.script;

import com.antshorttv.ai.AiSecretCodec;
import com.antshorttv.querycache.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.antshorttv.querycache.QueryResultCache.Region.*;
import static com.antshorttv.querycache.QueryResultCache.Scope.*;

class ScriptScopedInvalidationTest {
    final ObjectMapper json=new ObjectMapper();
    final java.util.Map<String,String> values=new HashMap<>();
    final CacheStore store=new CacheStore() {
        public String get(String key) { return values.get(key); }
        public void put(String key,String value,Duration ttl) { values.put(key,value); }
    };
    final QueryResultCache cache=new QueryResultCache(store,json,new AiSecretCodec("script-scoped"),new SimpleMeterRegistry(),true);
    final AtomicInteger loads=new AtomicInteger();
    String read(long tenant,long project) { return cache.read(SCRIPT,project(tenant,project),"navigation",String.class,()->"v"+loads.incrementAndGet()); }
    @Test void backgroundSummaryPersistenceRefreshesOnlyItsProjectAfterCommit() {
        var mapper=mock(ScriptEpisodeSummaryMapper.class);
        doAnswer(i->{i.getArgument(0,ScriptEpisodeSummaryEntity.class).setId(99L);return 1;}).when(mapper).insert(any(ScriptEpisodeSummaryEntity.class));
        var target=new ScriptEpisodeSummaryRepository(mapper,json);
        var factory=new AspectJProxyFactory(target);factory.addAspect(new QueryCacheInvalidationAspect(cache));
        ScriptEpisodeSummaryRepository repository=factory.getProxy();
        var document=new ScriptEpisodeSummaryDocument(null,1L,10L,100L,1000L,1,json.createObjectNode().put("summary","new summary"),"AI",null,1L,1L,null,null);
        var tx=new TransactionTemplate(new DataSourceTransactionManager(new DriverManagerDataSource("jdbc:h2:mem:script_scope_tx","sa","")));
        read(1,10);read(1,11);read(2,10);
        tx.executeWithoutResult(status->{repository.upsert(document);assertThat(values.keySet()).noneMatch(k->k.endsWith(":generation"));status.setRollbackOnly();});
        assertThat(read(1,10)).isEqualTo("v1");
        tx.executeWithoutResult(status->{repository.upsert(document);assertThat(values.keySet()).noneMatch(k->k.endsWith(":generation"));});
        assertThat(read(1,10)).isEqualTo("v4");
        assertThat(read(1,11)).isEqualTo("v2");assertThat(read(2,10)).isEqualTo("v3");
    }
    @Test void episodeReconciliationDoesNotInvalidateOtherProjects() {
        var episodes=mock(ScriptEpisodeMapper.class);
        var service=new ScriptEpisodeService(episodes,mock(org.springframework.jdbc.core.JdbcTemplate.class),mock(ScriptEpisodeSummaryRepository.class));
        var factory=new AspectJProxyFactory(service);factory.addAspect(new QueryCacheInvalidationAspect(cache));
        ScriptEpisodeService proxy=factory.getProxy();
        read(1,10);read(1,11);
        proxy.reconcileAndPersist(1L,10L,100L,200L,java.util.List.of());
        assertThat(read(1,10)).isEqualTo("v3");assertThat(read(1,11)).isEqualTo("v2");
    }
}
