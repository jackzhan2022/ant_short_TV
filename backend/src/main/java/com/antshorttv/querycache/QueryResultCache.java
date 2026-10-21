package com.antshorttv.querycache;

import com.antshorttv.ai.AiSecretCodec;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Disposable display data only. Authorization and authoritative writes never depend on this cache. */
public final class QueryResultCache {
    public enum Region {
        TASKS(2), ANALYSIS(2), SCRIPT(60), MESSAGES(5), MODELS(120);
        final Duration ttl;
        Region(int seconds) { ttl=Duration.ofSeconds(seconds); }
    }
    public record Scope(String value) {
        public Scope {
            if (value == null || value.isBlank()) throw new IllegalArgumentException("Cache scope is required");
        }
        public static Scope global() { return new Scope("global"); }
        public static Scope user(long userId) { return new Scope("user:" + userId); }
        public static Scope tenant(long tenantId) { return new Scope("tenant:" + tenantId); }
        public static Scope project(long tenantId, long projectId) { return new Scope("project:" + tenantId + ":" + projectId); }
    }
    private static final int MAX_BYTES=128*1024;
    private static final int MAX_CIPHER_CHARS=192*1024;
    private static final String PREFIX="antv:query:v2:";
    private final CacheStore store;
    private final ObjectMapper mapper;
    private final AiSecretCodec cipher;
    private final MeterRegistry metrics;
    private final boolean enabled;
    private final ReentrantLock[] locks=new ReentrantLock[64];
    private volatile long retryAt;
    public QueryResultCache(CacheStore store,ObjectMapper mapper,AiSecretCodec cipher,MeterRegistry metrics,boolean enabled) {
        this.store=store;this.mapper=mapper;this.cipher=cipher;this.metrics=metrics;this.enabled=enabled;
        for(int i=0;i<locks.length;i++)locks[i]=new ReentrantLock();
    }
    public static QueryResultCache disabled() { return new QueryResultCache(null,null,null,null,false); }
    public boolean isEnabled() { return enabled; }
    public <T> T read(Region region,Object identity,Class<T> type,Supplier<T> loader) {
        return read(region,Scope.global(),identity,type,loader);
    }
    public <T> T read(Region region,Object identity,TypeReference<T> type,Supplier<T> loader) {
        return read(region,Scope.global(),identity,type,loader);
    }
    public <T> T read(Region region,Scope scope,Object identity,Class<T> type,Supplier<T> loader) {
        if(!enabled)return loader.get();
        return read(region,scope,identity,mapper.constructType(type),loader);
    }
    public <T> T read(Region region,Scope scope,Object identity,TypeReference<T> type,Supplier<T> loader) {
        if(!enabled)return loader.get();
        return read(region,scope,identity,mapper.constructType(type),loader);
    }
    private <T> T read(Region region,Scope scope,Object identity,JavaType type,Supplier<T> loader) {
        if(TransactionSynchronizationManager.isActualTransactionActive() || System.nanoTime()<retryAt) {
            count(region,"bypass");return loader.get();
        }
        String key;
        try {
            String version=version(region,scope);
            // A broadcast affects all users; ordinary message changes only affect the recipient.
            if(region==Region.MESSAGES && !Scope.global().equals(scope)) version += "." + version(region,Scope.global());
            String identityHash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                (type.toCanonical()+":"+mapper.writeValueAsString(identity)).getBytes(StandardCharsets.UTF_8)));
            key=PREFIX+region.name()+":"+scopeHash(scope)+":data:"+version+":"+identityHash;
        } catch(Exception failure) { unavailable(region);return loader.get(); }
        ReentrantLock lock=locks[Math.floorMod(key.hashCode(),locks.length)];
        boolean acquired;
        try { acquired=lock.tryLock(50,TimeUnit.MILLISECONDS); }
        catch(InterruptedException interrupted) { Thread.currentThread().interrupt();count(region,"bypass");return loader.get(); }
        if(!acquired) { count(region,"contention");return loader.get(); }
        try {
            String cached;
            try { cached=store.get(key); }
            catch(RuntimeException failure) { unavailable(region);return loader.get(); }
            if(cached!=null) {
                try {
                    if(cached.length()>MAX_CIPHER_CHARS)throw new IllegalStateException("Oversized cached payload");
                    Envelope envelope=mapper.readValue(cipher.decrypt(cached),Envelope.class);
                    if(!key.equals(envelope.key()) || envelope.expiresAt()<=System.currentTimeMillis())
                        throw new IllegalStateException("Expired or misplaced cache entry");
                    T value=mapper.readValue(envelope.payload(),type);
                    count(region,"hit");return value;
                } catch(Exception invalid) { count(region,"invalid"); }
            }
            count(region,"miss");
            // Keep application exceptions outside Redis handling; never run the loader twice.
            T value=loader.get();
            try {
                String payload=mapper.writeValueAsString(value);
                if(payload.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES) { count(region,"oversize");return value; }
                String encrypted=cipher.encrypt(mapper.writeValueAsString(new Envelope(key,
                    System.currentTimeMillis()+region.ttl.toMillis(),payload)));
                if(encrypted.length()>MAX_CIPHER_CHARS) { count(region,"oversize");return value; }
                store.put(key,encrypted,region.ttl);count(region,"fill");
            } catch(Exception failure) { unavailable(region); }
            return value;
        } finally { lock.unlock(); }
    }
    public void invalidateAfterCommit(Region... regions) { invalidateAfterCommit(Scope.global(),regions); }
    public void invalidateProjectAfterCommit(Long tenantId,Long projectId,Region... regions) {
        if(enabled && tenantId!=null && projectId!=null) invalidateAfterCommit(Scope.project(tenantId,projectId),regions);
    }
    public void invalidateAfterCommit(Scope scope,Region... regions) {
        if(!enabled)return;
        if(TransactionSynchronizationManager.isSynchronizationActive()) {
            PendingInvalidations pending=TransactionSynchronizationManager.getSynchronizations().stream()
                .filter(s -> s instanceof PendingInvalidations p && p.owner==this)
                .map(s -> (PendingInvalidations)s).findFirst().orElse(null);
            if(pending==null) {
                pending=new PendingInvalidations(this);
                TransactionSynchronizationManager.registerSynchronization(pending);
            }
            for(Region region:regions) pending.entries.add(new Invalidation(region,scope));
        } else {
            for(Region region:regions) if(!publish(new Invalidation(region,scope))) break;
        }
    }
    private boolean publish(Invalidation entry) {
        if(System.nanoTime()<retryAt) {
            count(entry.region(),"invalidation_bypass");
            return false;
        }
        try {
            store.put(versionKey(entry.region(),entry.scope()),UUID.randomUUID().toString(),Duration.ofDays(1));
            count(entry.region(),"invalidation");
            return true;
        } catch(RuntimeException failure) {
            unavailable(entry.region());
            return false;
        }
    }
    private record Invalidation(Region region,Scope scope) { }
    private static final class PendingInvalidations implements TransactionSynchronization {
        private final QueryResultCache owner;
        private final Set<Invalidation> entries=new LinkedHashSet<>();
        private PendingInvalidations(QueryResultCache owner) { this.owner=owner; }
        @Override public void afterCommit() {
            // A recipient batch must not turn one Redis outage into hundreds of timeouts.
            for(Invalidation entry:entries) if(!owner.publish(entry)) break;
        }
    }
    private String version(Region region,Scope scope) {
        String value=store.get(versionKey(region,scope));
        if(value!=null && value.length()>64) throw new IllegalStateException("Invalid cache generation");
        return value==null ? "0" : value;
    }
    private static String scopeHash(Scope scope) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(scope.value().getBytes(StandardCharsets.UTF_8)));
        } catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String versionKey(Region region,Scope scope) { return PREFIX+region.name()+":"+scopeHash(scope)+":generation"; }
    private void unavailable(Region region) { retryAt=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);count(region,"error"); }
    private void count(Region region,String outcome) {
        if(metrics!=null)metrics.counter("antv.query.cache", "region",region.name(),"outcome",outcome).increment();
    }
    public record Envelope(String key,long expiresAt,String payload) { }
}
