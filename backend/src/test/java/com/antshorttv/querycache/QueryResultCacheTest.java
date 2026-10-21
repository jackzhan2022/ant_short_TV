package com.antshorttv.querycache;

import com.antshorttv.ai.AiSecretCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class QueryResultCacheTest {
    static class Store implements CacheStore {
        final Map<String,String> values = new ConcurrentHashMap<>();
        boolean broken;
        int gets;
        public String get(String key) { gets++; if (broken) throw new IllegalStateException("offline"); return values.get(key); }
        public void put(String key, String value, Duration ttl) { if(broken) throw new IllegalStateException("offline"); values.put(key,value); }
    }
    final Store store = new Store();
    final QueryResultCache cache = new QueryResultCache(store, new ObjectMapper().findAndRegisterModules(),
        new AiSecretCodec("test-query-cache-domain-key"), new SimpleMeterRegistry(), true);
    final AtomicInteger loads = new AtomicInteger();
    String read(String id) { return cache.read(QueryResultCache.Region.TASKS,id,String.class,() -> "value-"+loads.incrementAndGet()); }
    @AfterEach void clearTransaction() { if(TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization(); TransactionSynchronizationManager.setActualTransactionActive(false); }

    @Test void hotReadsAvoidLoaderAndDoNotStorePlaintext() {
        assertThat(read("tenant:1:user:2")).isEqualTo("value-1");
        assertThat(read("tenant:1:user:2")).isEqualTo("value-1");
        assertThat(loads).hasValue(1);
        assertThat(store.values.values()).noneMatch(v -> v.contains("value-1"));
    }
    @Test void identitiesAndRegionsAreIsolated() {
        read("tenant:1:user:2");read("tenant:2:user:2");
        assertThat(loads).hasValue(2);
        assertThat(cache.read(QueryResultCache.Region.MESSAGES,"tenant:1:user:2",String.class,()->"other")).isEqualTo("other");
    }
    @Test void disabledCacheNeverContactsRedis() {
        QueryResultCache.disabled().read(QueryResultCache.Region.TASKS,"key",String.class,()->"db");
        assertThat(store.gets).isZero();
    }
    @Test void redisFailureFallsBackAndOpensShortCircuit() {
        store.broken=true;read("a");int calls=store.gets;read("b");
        assertThat(loads).hasValue(2);assertThat(store.gets).isEqualTo(calls);
    }
    @Test void databaseExceptionIsNotRetriedOrSwallowed() {
        assertThatThrownBy(()->cache.read(QueryResultCache.Region.TASKS,"x",String.class,()->{loads.incrementAndGet();throw new IllegalArgumentException("db failed");})).isInstanceOf(IllegalArgumentException.class).hasMessage("db failed");
        assertThat(loads).hasValue(1);
    }
    @Test void transactionReadsBypassCachedValuesAndDoNotPublish() {
        read("a");TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThat(read("a")).isEqualTo("value-2");
        TransactionSynchronizationManager.setActualTransactionActive(false);
        assertThat(read("a")).isEqualTo("value-1");
    }
    @Test void invalidationHappensOnlyAfterCommit() {
        read("a");TransactionSynchronizationManager.initSynchronization();
        cache.invalidateAfterCommit(QueryResultCache.Region.TASKS);
        assertThat(read("a")).isEqualTo("value-1");
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(read("a")).isEqualTo("value-2");
    }
    @Test void rolledBackInvalidationDoesNotDiscardCommittedCache() {
        read("a");TransactionSynchronizationManager.initSynchronization();cache.invalidateAfterCommit(QueryResultCache.Region.TASKS);
        TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertThat(read("a")).isEqualTo("value-1");
    }
    @Test void invalidationDuringLoadCannotResurrectOldVersion() {
        cache.read(QueryResultCache.Region.TASKS,"x",String.class,()->{cache.invalidateAfterCommit(QueryResultCache.Region.TASKS);return "old";});
        assertThat(cache.read(QueryResultCache.Region.TASKS,"x",String.class,()->"new")).isEqualTo("new");
    }
    @Test void tamperedCiphertextFallsBack() {
        read("a");store.values.replaceAll((k,v)->k.contains(":data:")?"corrupt":v);
        assertThat(read("a")).isEqualTo("value-2");
    }
    @Test void ciphertextCannotBeReplayedUnderAnotherIdentity() {
        read("a");read("b");var keys=store.values.keySet().stream().filter(k->k.contains(":data:")).toList();
        store.values.put(keys.get(1),store.values.get(keys.get(0)));
        read("a");read("b");assertThat(loads).hasValue(3);
    }
    @Test void oversizedValuesAreNotCached() {
        var huge="x".repeat(140_000);
        for(int i=0;i<2;i++)cache.read(QueryResultCache.Region.TASKS,"huge",String.class,()->{loads.incrementAndGet();return huge;});
        assertThat(loads).hasValue(2);assertThat(store.values).isEmpty();
    }
    @Test void concurrentReadersShareOneFill() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
        try {
            var a=executor.submit(()->cache.read(QueryResultCache.Region.TASKS,"same",String.class,()->{loads.incrementAndGet();entered.countDown();try{release.await(2,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}return "shared";}));
            assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            var b=executor.submit(()->cache.read(QueryResultCache.Region.TASKS,"same",String.class,()->{loads.incrementAndGet();return "duplicate";}));
            release.countDown();assertThat(a.get(2,TimeUnit.SECONDS)).isEqualTo("shared");assertThat(b.get(2,TimeUnit.SECONDS)).isEqualTo("shared");assertThat(loads).hasValue(1);
        } finally { executor.shutdownNow(); }
    }
    @Test void nullResultsAreCached() {
        for (int i=0;i<2;i++) assertThat(cache.read(QueryResultCache.Region.TASKS,"null",String.class,
            ()->{loads.incrementAndGet();return null;})).isNull();
        assertThat(loads).hasValue(1);
    }
    @Test void failedCacheWriteDoesNotRepeatTheDatabaseLoad() {
        assertThat(cache.read(QueryResultCache.Region.TASKS,"put-failure",String.class,
            ()->{loads.incrementAndGet();store.broken=true;return "database";})).isEqualTo("database");
        assertThat(loads).hasValue(1);
    }
    @Test void expiredEncryptedEnvelopeIsRejectedEvenIfRedisKeepsIt() throws Exception {
        read("a");
        String key=store.values.keySet().stream().filter(k->k.contains(":data:")).findFirst().orElseThrow();
        var envelope=new QueryResultCache.Envelope(key,System.currentTimeMillis()-1,"\"stale\"");
        store.values.put(key,new AiSecretCodec("test-query-cache-domain-key").encrypt(new ObjectMapper().writeValueAsString(envelope)));
        assertThat(read("a")).isEqualTo("value-2");
    }

    @Test void readsResumeCachingAfterFailureCooldown() throws Exception {
        store.broken=true;
        assertThat(read("recover")).isEqualTo("value-1");
        store.broken=false;
        Thread.sleep(2100);
        assertThat(read("recover")).isEqualTo("value-2");
        assertThat(read("recover")).isEqualTo("value-2");
        assertThat(loads).hasValue(2);
    }

}
