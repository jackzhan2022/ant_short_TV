package com.antshorttv.security;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class CurrentTenantStore {

    private final Map<Long, TenantContext> contexts = new ConcurrentHashMap<>();

    public void put(TenantContext context) {
        contexts.put(context.userId(), context);
    }

    public Optional<TenantContext> get(Long userId) {
        return Optional.ofNullable(contexts.get(userId));
    }
}
