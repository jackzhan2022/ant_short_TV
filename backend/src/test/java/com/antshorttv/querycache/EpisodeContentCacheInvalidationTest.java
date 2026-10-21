package com.antshorttv.querycache;

import com.antshorttv.ai.AiSecretCodec;
import com.antshorttv.workflowagent.tool.EpisodeScriptCurrentSelector;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static com.antshorttv.querycache.QueryResultCache.Region.*;
import static com.antshorttv.querycache.QueryResultCache.Scope.*;

class EpisodeContentCacheInvalidationTest {
    @Test void persistedContentFingerprintRefreshesOnlyAfterCommitAndOnlyInItsProject() {
        var source=new DriverManagerDataSource("jdbc:h2:mem:episode_content_"+java.util.UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new JdbcTemplate(source);
        jdbc.execute("create table script_episode(id bigint,tenant_id bigint,project_id bigint,content clob,content_fingerprint varchar(64),updated_at timestamp)");
        jdbc.execute("create table script_episode_version(id bigint,episode_id bigint,is_current boolean)");
        jdbc.update("insert into script_episode values (100,1,10,'old','old-fingerprint',now())");
        jdbc.update("insert into script_episode_version values (200,100,true)");
        var store=new QueryResultCacheTest.Store();
        var cache=new QueryResultCache(store,new ObjectMapper(),new AiSecretCodec("episode-test"),new SimpleMeterRegistry(),true);
        var selector=new EpisodeScriptCurrentSelector(jdbc);ReflectionTestUtils.setField(selector,"queryCache",cache);
        var loads=new AtomicInteger();
        java.util.function.Supplier<String> read=()->cache.read(SCRIPT,project(1,10),"navigation-fingerprint",String.class,()->{
            loads.incrementAndGet();return jdbc.queryForObject("select content_fingerprint from script_episode where id=100",String.class);
        });
        assertThat(read.get()).isEqualTo("old-fingerprint");
        cache.read(SCRIPT,project(1,11),"navigation",String.class,()->"unrelated");
        var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
        tx.executeWithoutResult(status->{selector.selectCurrent(1,10,100,200,"rolled back");status.setRollbackOnly();});
        assertThat(read.get()).isEqualTo("old-fingerprint");assertThat(loads).hasValue(1);
        tx.executeWithoutResult(status->{selector.selectCurrent(1,10,100,200,"new content");assertThat(store.values.keySet()).noneMatch(k->k.endsWith(":generation"));});
        assertThat(read.get()).isNotEqualTo("old-fingerprint");assertThat(loads).hasValue(2);
        assertThat(cache.read(SCRIPT,project(1,11),"navigation",String.class,()->"unexpected reload")).isEqualTo("unrelated");
    }
}
