package com.antshorttv.script;

import com.antshorttv.ai.AiChatMessage;
import com.antshorttv.ai.AiInvocationRequest;
import com.antshorttv.ai.AiInvocationResult;
import com.antshorttv.ai.AiInvocationService;
import com.antshorttv.ai.AiTextRequest;
import com.antshorttv.ai.AiTextResponse;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.workflowagent.WorkflowAgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ScriptSplitChunkAnalyzer {
    private static final String SYSTEM_PROMPT = """
        你只分析一个短剧剧本处理分块，静默判断潜在分集边界。
        仅输出 JSON：{"candidates":[{"marker":"连续原文","localOffset":0,
        "type":"TURN","rationale":"简短理由","confidence":0.0}]}。
        localOffset 是 marker 在本分块文本中的 UTF-16 起始位置；不得改写 marker。
        """;

    private final ScriptSplitSnapshotStore store;
    private final SplitSourceReader sources;
    private final AiInvocationService invocation;
    private final ObjectMapper json;
    private final WorkflowAgentProperties properties;
    private final ScriptSplitChunkPlanner planner;

    @Autowired
    public ScriptSplitChunkAnalyzer(
        ScriptSplitSnapshotStore store,
        JdbcTemplate jdbc,
        AiInvocationService invocation,
        ObjectMapper json,
        WorkflowAgentProperties properties,
        ScriptSplitChunkPlanner planner
    ) {
        this(store, snapshotId -> jdbc.queryForObject("""
            select script.content from script_split_snapshot snapshot
              join script on script.id = snapshot.script_id
             where snapshot.id = ? and script.deleted_at is null
            """, String.class, snapshotId), invocation, json, properties, planner);
    }

    ScriptSplitChunkAnalyzer(
        ScriptSplitSnapshotStore store,
        SplitSourceReader sources,
        AiInvocationService invocation,
        ObjectMapper json,
        WorkflowAgentProperties properties
    ) {
        this(store, sources, invocation, json, properties, new ScriptSplitChunkPlanner());
    }

    private ScriptSplitChunkAnalyzer(
        ScriptSplitSnapshotStore store,
        SplitSourceReader sources,
        AiInvocationService invocation,
        ObjectMapper json,
        WorkflowAgentProperties properties,
        ScriptSplitChunkPlanner planner
    ) {
        this.store = store;
        this.sources = sources;
        this.invocation = invocation;
        this.json = json;
        this.properties = properties;
        this.planner = planner;
    }

    public ChunkAnalysisResult analyze(AnalysisContext context, long snapshotId) {
        ScriptSplitSnapshotStore.SplitSnapshot snapshot = store.require(snapshotId);
        String source = sources.load(snapshotId);
        List<ScriptSplitSnapshotStore.SplitChunk> completed = store.successfulChunks(snapshotId);
        List<ScriptSplitSnapshotStore.SplitChunk> pending = store.retryableChunks(snapshotId);
        ExecutorService pool = Executors.newFixedThreadPool(properties.getSplitChunkConcurrency());
        List<ChunkOutcome> outcomes;
        try {
            outcomes = pending.stream().map(chunk -> CompletableFuture.supplyAsync(
                () -> analyzeOne(context, snapshotId, source, chunk), pool))
                .toList().stream().map(CompletableFuture::join).toList();
        } finally {
            pool.shutdownNow();
        }
        if (outcomes.stream().anyMatch(outcome -> outcome.errorCode() != null)) {
            throw new BusinessException(ErrorCode.WORKFLOW_AGENT_TOOL_INVALID,
                "部分剧本分块分析失败，可重试失败分块。");
        }

        List<BoundaryCandidate> candidates = new ArrayList<>();
        List<Long> callIds = new ArrayList<>();
        for (ScriptSplitSnapshotStore.SplitChunk chunk : completed) {
            candidates.addAll(parsePersisted(chunk.candidates()));
            if (chunk.aiCallLogId() != null) callIds.add(chunk.aiCallLogId());
        }
        for (ChunkOutcome outcome : outcomes) {
            candidates.addAll(outcome.candidates());
            if (outcome.aiCallLogId() != null) callIds.add(outcome.aiCallLogId());
        }
        List<BoundaryCandidate> merged = merge(candidates);
        var settings = new ScriptSplitChunkPlanner.ChunkSettings(
            properties.getSplitChunkTargetMin(), properties.getSplitChunkTargetMax(),
            properties.getSplitChunkHardMax(), properties.getSplitChunkOverlap());
        List<ScriptSplitChunkPlanner.TrustedAnchor> anchors = planner.plan(source, settings).stream()
            .flatMap(chunk -> chunk.anchors().stream())
            .collect(java.util.stream.Collectors.collectingAndThen(
                java.util.stream.Collectors.toMap(
                    ScriptSplitChunkPlanner.TrustedAnchor::offset, anchor -> anchor,
                    (left, right) -> left, TreeMap::new),
                values -> List.copyOf(values.values())));
        return new ChunkAnalysisResult(snapshot.total(), completed.size() + outcomes.size(),
            merged, anchors, List.copyOf(callIds));
    }

    private ChunkOutcome analyzeOne(
        AnalysisContext context,
        long snapshotId,
        String source,
        ScriptSplitSnapshotStore.SplitChunk chunk
    ) {
        try {
            String body = source.substring(chunk.contextStart(), chunk.contextEnd());
            String prompt = "chunkNo=" + chunk.chunkNo() + "\ncontextStart=" + chunk.contextStart()
                + "\n分块正文：\n" + body;
            AiInvocationResult<AiTextResponse> result = invocation.invokeText(AiInvocationRequest.text()
                .tenantId(context.tenantId()).userId(context.userId()).projectId(context.projectId())
                .taskId(context.taskId()).modelId(context.modelId())
                .businessSceneCode("workflow_agent")
                .traceId("agent-run-" + context.agentRunId() + "-split-chunks")
                .executionId(context.executionId()).attemptId(context.attemptId())
                .executionVersion(context.executionVersion())
                .phase("EPISODE_SPLIT_CHUNK_" + chunk.chunkNo())
                .idempotencyKey("agent-run-" + context.agentRunId() + "-split-chunk-"
                    + snapshotId + "-" + chunk.chunkNo())
                .requestSummary("Episode split chunk " + chunk.chunkNo())
                .textRequest(new AiTextRequest(
                    null, null, 0.2, 4096, null, true, null, 120, 0,
                    List.of(AiChatMessage.system(SYSTEM_PROMPT), AiChatMessage.user(prompt)),
                    List.of(), "disabled"))
                .build());
            List<BoundaryCandidate> candidates = parseAndVerify(
                result.response() == null ? null : result.response().content(), source, chunk);
            JsonNode persisted = json.valueToTree(candidates);
            store.markChunkSucceeded(
                snapshotId, chunk.chunkNo(), result.aiCallLogId(), persisted);
            return new ChunkOutcome(candidates, result.aiCallLogId(), null);
        } catch (RuntimeException exception) {
            store.markChunkFailed(snapshotId, chunk.chunkNo(),
                "CHUNK_ANALYSIS_FAILED", bounded(exception.getMessage(), 1000));
            return new ChunkOutcome(List.of(), null, "CHUNK_ANALYSIS_FAILED");
        }
    }

    private List<BoundaryCandidate> parseAndVerify(
        String content, String source, ScriptSplitSnapshotStore.SplitChunk chunk
    ) {
        try {
            JsonNode root = json.readTree(content == null ? "" : content);
            if (!root.path("candidates").isArray()) throw new IllegalArgumentException("候选数组缺失。");
            List<BoundaryCandidate> result = new ArrayList<>();
            String body = source.substring(chunk.contextStart(), chunk.contextEnd());
            for (JsonNode item : root.path("candidates")) {
                try {
                    String marker = item.path("marker").asText();
                    int localOffset = item.path("localOffset").asInt(-1);
                    if (marker.isBlank() || marker.length() > 2000 || localOffset < 0) {
                        throw new IllegalArgumentException("候选原文标记无效。");
                    }
                    MarkerLocation location = body.startsWith(marker, localOffset)
                        ? new MarkerLocation(localOffset, localOffset + marker.length())
                        : locateUniqueMarker(body, marker);
                    int absoluteOffset = chunk.contextStart() + location.start();
                    String trustedMarker = body.substring(location.start(), location.endExclusive());
                    result.add(new BoundaryCandidate(trustedMarker, absoluteOffset,
                        item.path("type").asText("UNKNOWN"), item.path("rationale").asText(""),
                        Math.max(0, Math.min(1, item.path("confidence").asDouble(0))),
                        List.of(chunk.chunkNo())));
                } catch (IllegalArgumentException ignored) {
                    // An unverified suggestion is excluded; verified candidates in the same
                    // chunk remain usable. A wholly invalid non-empty response still fails below.
                }
            }
            if (root.path("candidates").size() > 0 && result.isEmpty()) {
                throw new IllegalArgumentException("候选原文标记均无效。");
            }
            return result;
        } catch (Exception exception) {
            throw new IllegalArgumentException("分块模型响应无效。", exception);
        }
    }

    private MarkerLocation locateUniqueMarker(String source, String marker) {
        int exact = source.indexOf(marker);
        if (exact >= 0) {
            if (source.indexOf(marker, exact + 1) >= 0) {
                throw new IllegalArgumentException("候选原文标记重复。");
            }
            return new MarkerLocation(exact, exact + marker.length());
        }
        NormalizedLineText normalizedSource = normalizeLineEndings(source, true);
        NormalizedLineText normalizedMarker = normalizeLineEndings(marker, false);
        int first = normalizedSource.text().indexOf(normalizedMarker.text());
        if (first < 0) {
            throw new IllegalArgumentException("候选原文标记不存在。");
        }
        if (normalizedSource.text().indexOf(normalizedMarker.text(), first + 1) >= 0) {
            throw new IllegalArgumentException("候选原文标记重复。");
        }
        int last = first + normalizedMarker.text().length() - 1;
        return new MarkerLocation(
            normalizedSource.starts().get(first), normalizedSource.ends().get(last));
    }

    private NormalizedLineText normalizeLineEndings(String value, boolean retainOffsets) {
        StringBuilder normalized = new StringBuilder();
        List<Integer> starts = new ArrayList<>();
        List<Integer> ends = new ArrayList<>();
        for (int index = 0; index < value.length(); index++) {
            int start = index;
            char character = value.charAt(index);
            if (character == '\r') {
                if (index + 1 < value.length() && value.charAt(index + 1) == '\n') {
                    index++;
                }
                character = '\n';
            }
            normalized.append(character);
            if (retainOffsets) {
                starts.add(start);
                ends.add(index + 1);
            }
        }
        return new NormalizedLineText(
            normalized.toString(), List.copyOf(starts), List.copyOf(ends));
    }

    private List<BoundaryCandidate> parsePersisted(JsonNode value) {
        if (value == null || !value.isArray()) return List.of();
        List<BoundaryCandidate> result = new ArrayList<>();
        for (JsonNode item : value) {
            result.add(new BoundaryCandidate(
                item.path("marker").asText(), item.path("absoluteOffset").asInt(),
                item.path("type").asText(), item.path("rationale").asText(),
                item.path("confidence").asDouble(),
                json.convertValue(item.path("sourceChunks"),
                    json.getTypeFactory().constructCollectionType(List.class, Integer.class))));
        }
        return result;
    }

    private List<BoundaryCandidate> merge(List<BoundaryCandidate> candidates) {
        Map<Integer, BoundaryCandidate> merged = new TreeMap<>();
        for (BoundaryCandidate candidate : candidates) {
            merged.merge(candidate.absoluteOffset(), candidate, (left, right) -> {
                LinkedHashSet<Integer> chunks = new LinkedHashSet<>(left.sourceChunks());
                chunks.addAll(right.sourceChunks());
                BoundaryCandidate strongest = right.confidence() > left.confidence() ? right : left;
                return new BoundaryCandidate(strongest.marker(), strongest.absoluteOffset(),
                    strongest.type(), strongest.rationale(),
                    Math.max(left.confidence(), right.confidence()), List.copyOf(chunks));
            });
        }
        return List.copyOf(merged.values());
    }

    private String bounded(String value, int max) {
        if (value == null) return "未知错误";
        return value.length() <= max ? value : value.substring(0, max);
    }

    @FunctionalInterface
    interface SplitSourceReader { String load(long snapshotId); }

    public record AnalysisContext(
        long tenantId, long userId, long projectId, Long taskId, long modelId, long agentRunId,
        Long executionId, Long attemptId, Integer executionVersion
    ) {
        public AnalysisContext(
            long tenantId, long userId, long projectId, Long taskId, long modelId, long agentRunId
        ) {
            this(tenantId, userId, projectId, taskId, modelId, agentRunId, null, null, null);
        }
    }
    public record BoundaryCandidate(
        String marker, int absoluteOffset, String type, String rationale,
        double confidence, List<Integer> sourceChunks
    ) {}
    public record ChunkAnalysisResult(
        int total, int completed, List<BoundaryCandidate> candidates,
        List<ScriptSplitChunkPlanner.TrustedAnchor> anchors, List<Long> aiCallLogIds
    ) {}
    private record ChunkOutcome(
        List<BoundaryCandidate> candidates, Long aiCallLogId, String errorCode
    ) {}
    private record MarkerLocation(int start, int endExclusive) {}
    private record NormalizedLineText(String text, List<Integer> starts, List<Integer> ends) {}
}
