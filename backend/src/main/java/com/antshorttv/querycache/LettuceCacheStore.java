package com.antshorttv.querycache;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.resource.DefaultClientResources;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/** One multiplexed connection; no unbounded queue while Redis is disconnected. */
public final class LettuceCacheStore implements CacheStore {
    private final DefaultClientResources resources;
    private final RedisClient client;
    private volatile StatefulRedisConnection<String,String> connection;
    private final ReentrantLock initialization = new ReentrantLock();
    private volatile long retryAt;
    private volatile boolean closed;
    public LettuceCacheStore(String host, int port, String user, String password, int database) {
        var uri = RedisURI.Builder.redis(host,port).withDatabase(database).withTimeout(Duration.ofMillis(200));
        if (password != null && !password.isBlank()) {
            if(user != null && !user.isBlank()) uri.withAuthentication(user,password);
            else uri.withPassword(password.toCharArray());
        }
        resources=DefaultClientResources.builder().ioThreadPoolSize(2).computationThreadPoolSize(2).build();
        client=RedisClient.create(resources,uri.build());
        client.setOptions(ClientOptions.builder().autoReconnect(true).requestQueueSize(64)
            .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
            .socketOptions(SocketOptions.builder().connectTimeout(Duration.ofMillis(300)).build()).build());
    }
    private StatefulRedisConnection<String,String> connection() {
        if (closed) throw new IllegalStateException("Cache store is closed");
        var current = connection;
        if (current != null) return current;
        if (System.nanoTime() < retryAt) throw new IllegalStateException("Cache connection cooling down");
        boolean acquired;
        try {
            acquired = initialization.tryLock(50, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while opening cache connection", interrupted);
        }
        if (!acquired) throw new IllegalStateException("Cache connection initialization busy");
        try {
            if (closed) throw new IllegalStateException("Cache store is closed");
            if (connection != null) return connection;
            if (System.nanoTime() < retryAt) throw new IllegalStateException("Cache connection cooling down");
            try {
                connection = client.connect();
                return connection;
            } catch (RuntimeException failure) {
                retryAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                throw failure;
            }
        } finally {
            initialization.unlock();
        }
    }
    @Override public String get(String key) {
        int limit = key.endsWith(":generation") ? 64 : 192 * 1024;
        // GETRANGE is atomic and transfers at most limit+1 bytes, even for a hostile value.
        String value = connection().sync().getrange(key, 0, limit);
        if (value.length() > limit) throw new IllegalStateException("Oversized cached value");
        return value.isEmpty() ? null : value;
    }
    @Override public void put(String key,String value,Duration ttl) {
        connection().sync().set(key,value,SetArgs.Builder.px(ttl.toMillis()));
    }
    @Override public void close() {
        initialization.lock();
        try {
            if (closed) return;
            closed = true;
            if (connection != null) connection.close();
            client.shutdown(Duration.ZERO,Duration.ofSeconds(1));
            resources.shutdown(0,1,TimeUnit.SECONDS);
        } finally {
            initialization.unlock();
        }
    }
}
