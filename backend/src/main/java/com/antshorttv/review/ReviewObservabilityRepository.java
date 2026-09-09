package com.antshorttv.review;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper json;

    public ReviewObservabilityRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public ReviewObservabilityResponse load(
        long snapshotId,
        Collection<Long> runIds
    ) {
        ReviewQualityProgressResponse quality = quality(snapshotId);
        ReviewDecisionCountsResponse decisions = decisionCounts(snapshotId);
        List<Long> effectiveRunIds = new ArrayList<>(runIds == null ? List.of() : runIds);
        if (quality != null && quality.runId() != null) effectiveRunIds.add(quality.runId());
        return new ReviewObservabilityResponse(
            quality, decisions, humanReviewFindings(snapshotId), cacheUsage(effectiveRunIds)
        );
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

    private ReviewQualityProgressResponse quality(long snapshotId) {
        List<ReviewQualityProgressResponse> rows = jdbc.query("""
            select status, run_id, attempt_no, candidate_count, decision_count, coverage_json
              from review_pipeline_stage
             where snapshot_id=? and stage_key='semantic-quality'
            """, (row, index) -> {
            Map<String, Object> coverage = object(row.getString("coverage_json"));
            Object anomalyReview = coverage.get("anomalyReview");
            Boolean anomalyPassed = anomalyReview instanceof Map<?, ?> review
                && review.get("passed") instanceof Boolean value ? value : null;
            return new ReviewQualityProgressResponse(
                row.getString("status"), nullableLong(row, "run_id"), row.getInt("attempt_no"),
                nullableInt(row, "candidate_count"), nullableInt(row, "decision_count"),
                Boolean.TRUE.equals(coverage.get("anomalyRequired")), anomalyPassed
            );
        }, snapshotId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private ReviewDecisionCountsResponse decisionCounts(long snapshotId) {
        Map<String, Integer> counts = jdbc.query("""
            select decision, count(*) total from review_semantic_decision
             where snapshot_id=? group by decision
            """, result -> {
            java.util.LinkedHashMap<String, Integer> values = new java.util.LinkedHashMap<>();
            while (result.next()) values.put(result.getString("decision"), result.getInt("total"));
            return values;
        }, snapshotId);
        return new ReviewDecisionCountsResponse(
            counts.getOrDefault("CONFIRMED", 0), counts.getOrDefault("NEEDS_HUMAN_REVIEW", 0),
            counts.getOrDefault("REJECTED", 0), counts.getOrDefault("INSUFFICIENT_EVIDENCE", 0)
        );
    }

    private List<ReviewHumanReviewFindingResponse> humanReviewFindings(long snapshotId) {
        return jdbc.query("""
            select c.id candidate_id, c.unit_id, c.dimension, c.raw_payload_json,
                   d.confidence, d.rationale, d.severity_decision, d.evidence_refs_json
              from review_semantic_decision d
              join review_candidate_audit c on c.id=d.candidate_id
             where d.snapshot_id=? and d.decision='NEEDS_HUMAN_REVIEW'
             order by c.unit_id, c.candidate_no, c.id
            """, (row, index) -> new ReviewHumanReviewFindingResponse(
            row.getLong("candidate_id"), row.getLong("unit_id"), row.getString("dimension"),
            row.getBigDecimal("confidence"), row.getString("rationale"), row.getString("severity_decision"),
            strings(row.getString("evidence_refs_json")), object(row.getString("raw_payload_json"))
        ), snapshotId);
    }

    private ReviewCacheUsageResponse emptyCacheUsage() {
        return new ReviewCacheUsageResponse(0L, null, null, null, 0L, 0L, null, false);
    }

    private Map<String, Object> object(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try { return json.readValue(value, new TypeReference<>() {}); }
        catch (Exception ignored) { return Map.of(); }
    }

    private List<String> strings(String value) {
        if (value == null || value.isBlank()) return List.of();
        try { return json.readValue(value, new TypeReference<>() {}); }
        catch (Exception ignored) { return List.of(); }
    }

    private Long nullableLong(java.sql.ResultSet row, String column) throws java.sql.SQLException {
        long value = row.getLong(column);
        return row.wasNull() ? null : value;
    }

    private Integer nullableInt(java.sql.ResultSet row, String column) throws java.sql.SQLException {
        int value = row.getInt(column);
        return row.wasNull() ? null : value;
    }
}
