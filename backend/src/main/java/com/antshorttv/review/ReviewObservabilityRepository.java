package com.antshorttv.review;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReviewObservabilityRepository {
    private final JdbcTemplate jdbc;

    public ReviewObservabilityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ReviewObservabilityResponse load(Collection<Long> runIds) {
        return new ReviewObservabilityResponse(cacheUsage(runIds));
    }

    public ReviewCacheUsageResponse cacheUsage(Collection<Long> requestedRunIds) {
        List<Long> runIds = new ArrayList<>(new LinkedHashSet<>(
            requestedRunIds == null ? List.of() : requestedRunIds.stream().filter(java.util.Objects::nonNull).toList()
        ));
        if (runIds.isEmpty()) return emptyCacheUsage();
        String placeholders = String.join(",", java.util.Collections.nCopies(runIds.size(), "?"));
        return jdbc.query("""
            select count(*) call_count,
                   sum(case when cached_input_tokens is not null then 1 else 0 end) cached_observed_count,
                   sum(case when cache_write_tokens is not null then 1 else 0 end) cache_write_observed_count,
                   coalesce(sum(prompt_tokens), 0) prompt_tokens,
                   coalesce(sum(completion_tokens), 0) output_tokens,
                   coalesce(sum(duration_ms), 0) latency_ms,
                   coalesce(sum(cached_input_tokens), 0) cached_input_tokens,
                   coalesce(sum(cache_write_tokens), 0) cache_write_tokens
              from (
                    select distinct l.id, l.prompt_tokens, l.completion_tokens, l.duration_ms,
                           l.cached_input_tokens, l.cache_write_tokens
                      from ai_workflow_agent_run_step s
                      join ai_call_log l on l.id=s.ai_call_log_id
                     where s.run_id in (%s)
                   ) review_calls
            """.formatted(placeholders), result -> {
            if (!result.next()) return emptyCacheUsage();
            long callCount = result.getLong("call_count");
            long cachedObservedCount = result.getLong("cached_observed_count");
            long cacheWriteObservedCount = result.getLong("cache_write_observed_count");
            long promptTokens = result.getLong("prompt_tokens");
            long outputTokens = result.getLong("output_tokens");
            long latencyMs = result.getLong("latency_ms");
            boolean cachedObservable = callCount > 0 && cachedObservedCount == callCount;
            boolean cacheWriteObservable = callCount > 0 && cacheWriteObservedCount == callCount;
            Long cached = cachedObservable ? result.getLong("cached_input_tokens") : null;
            Long cacheWrite = cacheWriteObservable ? result.getLong("cache_write_tokens") : null;
            Long ordinary = cachedObservable && cacheWriteObservable
                ? Math.max(0, promptTokens - cached - cacheWrite) : null;
            BigDecimal ratio = cachedObservable && promptTokens > 0
                ? BigDecimal.valueOf(cached).divide(BigDecimal.valueOf(promptTokens), 4, RoundingMode.HALF_UP)
                : null;
            return new ReviewCacheUsageResponse(promptTokens, ordinary, cached, cacheWrite, outputTokens,
                latencyMs, ratio, cachedObservable);
        }, runIds.toArray());
    }

    public Map<Long, ReviewCacheUsageResponse> cacheUsageByRunIds(Collection<Long> requestedRunIds) {
        List<Long> runIds = new ArrayList<>(new LinkedHashSet<>(
            requestedRunIds == null ? List.of() : requestedRunIds.stream().filter(java.util.Objects::nonNull).toList()
        ));
        if (runIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(runIds.size(), "?"));
        return jdbc.query("""
            select s.run_id, count(*) call_count,
                   sum(case when l.cached_input_tokens is not null then 1 else 0 end) cached_observed_count,
                   sum(case when l.cache_write_tokens is not null then 1 else 0 end) cache_write_observed_count,
                   coalesce(sum(l.prompt_tokens), 0) prompt_tokens, coalesce(sum(l.completion_tokens), 0) output_tokens,
                   coalesce(sum(l.duration_ms), 0) latency_ms, coalesce(sum(l.cached_input_tokens), 0) cached_input_tokens,
                   coalesce(sum(l.cache_write_tokens), 0) cache_write_tokens
              from ai_workflow_agent_run_step s join ai_call_log l on l.id=s.ai_call_log_id
             where s.run_id in (%s) group by s.run_id
            """.formatted(placeholders), result -> {
            java.util.LinkedHashMap<Long, ReviewCacheUsageResponse> values = new java.util.LinkedHashMap<>();
            while (result.next()) {
                long prompt = result.getLong("prompt_tokens");
                long cached = result.getLong("cached_input_tokens");
                long calls = result.getLong("call_count");
                boolean cachedObservable = calls > 0 && result.getLong("cached_observed_count") == calls;
                boolean cacheWriteObservable = calls > 0 && result.getLong("cache_write_observed_count") == calls;
                values.put(result.getLong("run_id"), new ReviewCacheUsageResponse(prompt,
                    cachedObservable && cacheWriteObservable
                        ? Math.max(0, prompt - cached - result.getLong("cache_write_tokens")) : null,
                    cachedObservable ? cached : null,
                    cacheWriteObservable ? result.getLong("cache_write_tokens") : null,
                    result.getLong("output_tokens"), result.getLong("latency_ms"),
                    cachedObservable && prompt > 0
                        ? BigDecimal.valueOf(cached).divide(BigDecimal.valueOf(prompt), 4, RoundingMode.HALF_UP) : null,
                    cachedObservable));
            }
            return values;
        }, runIds.toArray());
    }

    private ReviewCacheUsageResponse emptyCacheUsage() {
        return new ReviewCacheUsageResponse(0L, null, null, null, 0L, 0L, null, false);
    }

}
