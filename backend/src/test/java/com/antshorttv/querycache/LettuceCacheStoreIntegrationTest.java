package com.antshorttv.querycache;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="QUERY_CACHE_TEST_PORT",matches=".+")
class LettuceCacheStoreIntegrationTest {
    LettuceCacheStore store() { return new LettuceCacheStore("127.0.0.1",Integer.parseInt(System.getenv("QUERY_CACHE_TEST_PORT")),"antv_app",System.getenv("QUERY_CACHE_TEST_PASSWORD"),0); }
    @Test void authenticatesRoundTripsExpiresAndRejectsOversizedResponses() throws Exception {
        try(var store=store()) {
            String prefix="antv:query-test:"+java.util.UUID.randomUUID()+":";
            store.put(prefix+"roundtrip","hello",Duration.ofSeconds(1));
            assertThat(store.get(prefix+"roundtrip")).isEqualTo("hello");
            Thread.sleep(1200);assertThat(store.get(prefix+"roundtrip")).isNull();
            store.put(prefix+"oversize","x".repeat(300_000),Duration.ofSeconds(2));
            assertThatThrownBy(()->store.get(prefix+"oversize")).isInstanceOf(IllegalStateException.class);
            store.put(prefix+"generation","x".repeat(100),Duration.ofSeconds(2));
            assertThatThrownBy(()->store.get(prefix+"generation")).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(()->store.put("foreign:verification","denied",Duration.ofSeconds(1))).isInstanceOf(RuntimeException.class);
        }
    }
    @Test void concurrentColdConnectionFailuresRemainBounded() throws Exception {
        // Local listening socket accepts TCP but never answers the Redis handshake.
        try(var stalled=new java.net.ServerSocket(0);var store=new LettuceCacheStore("127.0.0.1",stalled.getLocalPort(),"","",0)) {
            var executor=Executors.newFixedThreadPool(12);var ready=new CountDownLatch(12);var start=new CountDownLatch(1);
            var jobs=new ArrayList<java.util.concurrent.Future<?>>();
            try {
                for(int i=0;i<12;i++)jobs.add(executor.submit(()->{ready.countDown();try{start.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}assertThatThrownBy(()->store.get("antv:missing")).isInstanceOf(RuntimeException.class);}));
                assertThat(ready.await(2,TimeUnit.SECONDS)).isTrue();long began=System.nanoTime();start.countDown();
                for(var job:jobs)job.get(2,TimeUnit.SECONDS);
                assertThat(Duration.ofNanos(System.nanoTime()-began)).isLessThan(Duration.ofMillis(1500));
            } finally { executor.shutdownNow(); }
        }
    }
}
