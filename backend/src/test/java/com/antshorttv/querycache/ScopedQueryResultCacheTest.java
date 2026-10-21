package com.antshorttv.querycache;

import com.antshorttv.ai.AiSecretCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static com.antshorttv.querycache.QueryResultCache.Region.*;
import static com.antshorttv.querycache.QueryResultCache.Scope.*;
import static org.assertj.core.api.Assertions.*;

class ScopedQueryResultCacheTest {
    static class Store extends QueryResultCacheTest.Store {
        final Map<String,Duration> ttls=new HashMap<>();
        int writes;
        @Override public void put(String key,String value,Duration ttl) { super.put(key,value,ttl);ttls.put(key,ttl);writes++; }
    }
    final Store store=new Store();
    final QueryResultCache cache=new QueryResultCache(store,new ObjectMapper(),new AiSecretCodec("scope-test"),new SimpleMeterRegistry(),true);
    final AtomicInteger loads=new AtomicInteger();
    String read(QueryResultCache.Region region,QueryResultCache.Scope scope) {
        return cache.read(region,scope,"same",String.class,()->"v"+loads.incrementAndGet());
    }
    @AfterEach void cleanup() { if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.clearSynchronization(); }
    @Test void userInvalidationPreservesOtherUsersAndBroadcastInvalidatesAll() {
        assertThat(read(MESSAGES,user(1))).isEqualTo("v1");
        assertThat(read(MESSAGES,user(2))).isEqualTo("v2");
        cache.invalidateAfterCommit(user(1),MESSAGES);
        assertThat(read(MESSAGES,user(1))).isEqualTo("v3");
        assertThat(read(MESSAGES,user(2))).isEqualTo("v2");
        cache.invalidateAfterCommit(MESSAGES);
        assertThat(read(MESSAGES,user(1))).isEqualTo("v4");
        assertThat(read(MESSAGES,user(2))).isEqualTo("v5");
    }
    @Test void sameProjectNumberInAnotherTenantAndOtherProjectStayHot() {
        read(SCRIPT,project(1,10));read(SCRIPT,project(1,11));read(SCRIPT,project(2,10));
        cache.invalidateAfterCommit(project(1,10),SCRIPT,ANALYSIS);
        assertThat(read(SCRIPT,project(1,10))).isEqualTo("v4");
        assertThat(read(SCRIPT,project(1,11))).isEqualTo("v2");
        assertThat(read(SCRIPT,project(2,10))).isEqualTo("v3");
    }
    @Test void taskInvalidationStaysWithinTenant() {
        read(TASKS,tenant(1));read(TASKS,tenant(2));cache.invalidateAfterCommit(tenant(1),TASKS);
        assertThat(read(TASKS,tenant(1))).isEqualTo("v3");assertThat(read(TASKS,tenant(2))).isEqualTo("v2");
    }
    @Test void stableCachesHaveLongerTtlButProgressRetainsShortTtl() {
        read(SCRIPT,project(1,10));read(MODELS,global());read(ANALYSIS,project(1,10));
        assertThat(store.ttls).anySatisfy((key,ttl)->{assertThat(key).contains(":SCRIPT:",":data:");assertThat(ttl).isEqualTo(Duration.ofSeconds(60));});
        assertThat(store.ttls).anySatisfy((key,ttl)->{assertThat(key).contains(":MODELS:",":data:");assertThat(ttl).isEqualTo(Duration.ofSeconds(120));});
        assertThat(store.ttls).anySatisfy((key,ttl)->{assertThat(key).contains(":ANALYSIS:",":data:");assertThat(ttl).isEqualTo(Duration.ofSeconds(2));});
    }
    @Test void commitCoalescesDuplicateScopesAndRollbackPublishesNothing() {
        TransactionSynchronizationManager.initSynchronization();
        cache.invalidateAfterCommit(project(1,10),SCRIPT);cache.invalidateAfterCommit(project(1,10),SCRIPT);
        cache.invalidateAfterCommit(project(1,11),SCRIPT);
        assertThat(store.writes).isZero();
        TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCommit());
        assertThat(store.writes).isEqualTo(2);
    }
    @Test void invalidationDuringFillCannotPublishIntoTheNewScopeGeneration() {
        cache.read(SCRIPT,project(1,10),"same",String.class,()->{cache.invalidateAfterCommit(project(1,10),SCRIPT);return "old";});
        assertThat(read(SCRIPT,project(1,10))).isEqualTo("v1");
    }
    @Test void batchAbortsOnFirstRedisFailureAndHonorsCooldown() {
        AtomicInteger attempts=new AtomicInteger();
        CacheStore failing=new CacheStore() {
            public String get(String key) { return null; }
            public void put(String key,String value,Duration ttl) { attempts.incrementAndGet();throw new IllegalStateException("Redis command timed out"); }
        };
        var cache=new QueryResultCache(failing,new ObjectMapper(),new AiSecretCodec("batch-test"),new SimpleMeterRegistry(),true);
        TransactionSynchronizationManager.initSynchronization();
        for(int user=1;user<=500;user++)cache.invalidateAfterCommit(user(user),MESSAGES);
        TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCommit());
        assertThat(attempts).hasValue(1);
        TransactionSynchronizationManager.clearSynchronization();
        cache.invalidateAfterCommit(project(1,10),SCRIPT,ANALYSIS);
        assertThat(attempts).hasValue(1);
    }

}
