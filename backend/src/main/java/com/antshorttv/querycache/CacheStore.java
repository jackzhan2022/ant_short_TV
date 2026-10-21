package com.antshorttv.querycache;
import java.time.Duration;
public interface CacheStore extends AutoCloseable {
    String get(String key);
    void put(String key, String value, Duration ttl);
    @Override default void close() { }
}
