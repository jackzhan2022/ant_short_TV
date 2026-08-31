package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.ai.AiCapability;
import com.antshorttv.ai.AiInvocationResult;
import com.antshorttv.ai.AiInvocationService;
import com.antshorttv.ai.AiTextResponse;
import com.antshorttv.workflowagent.WorkflowAgentProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.Assertions;

class ScriptSplitChunkAnalyzerTest {
    @Test
    void verifiesAbsoluteMarkersDeduplicatesOverlapAndBoundsConcurrency() {
        String source = "A".repeat(20) + "边界一" + "B".repeat(20) + "边界二" + "C".repeat(20);
        ScriptSplitSnapshotStore store = mock(ScriptSplitSnapshotStore.class);
        when(store.require(11L)).thenReturn(new ScriptSplitSnapshotStore.SplitSnapshot(
            11, 99, "hash", "CHUNK_FALLBACK", "OUTPUT_TRUNCATED", "RUNNING", 3, 0, 0));
        when(store.successfulChunks(11L)).thenReturn(List.of());
        when(store.retryableChunks(11L)).thenReturn(List.of(
            chunk(1, 0, 35), chunk(2, 15, 55), chunk(3, 40, source.length())));
        AtomicInteger active = new AtomicInteger();
        AtomicInteger max = new AtomicInteger();
        AiInvocationService invocation = mock(AiInvocationService.class);
        when(invocation.invokeText(any())).thenAnswer(call -> {
            int running = active.incrementAndGet();
            max.accumulateAndGet(running, Math::max);
            Thread.sleep(20);
            String prompt = call.<com.antshorttv.ai.AiInvocationRequest>getArgument(0)
                .textRequest().messages().get(1).content();
            String marker = prompt.contains("chunkNo=3") ? "边界二" : "边界一";
            int local = prompt.contains("chunkNo=1") ? 20 : prompt.contains("chunkNo=2") ? 5 : 3;
            active.decrementAndGet();
            String body = "{\"candidates\":[{\"marker\":\"" + marker
                + "\",\"localOffset\":" + local
                + ",\"type\":\"TURN\",\"rationale\":\"r\",\"confidence\":0.9}]}";
            return result(body, (long) marker.hashCode());
        });
        WorkflowAgentProperties properties = new WorkflowAgentProperties();
        properties.setSplitChunkConcurrency(2);
        ScriptSplitChunkAnalyzer analyzer = new ScriptSplitChunkAnalyzer(
            store, snapshotId -> source, invocation, new ObjectMapper(), properties);

        ScriptSplitChunkAnalyzer.ChunkAnalysisResult result = analyzer.analyze(
            new ScriptSplitChunkAnalyzer.AnalysisContext(1, 2, 3, null, 7, 99), 11);

        assertThat(result.total()).isEqualTo(3);
        assertThat(result.candidates()).extracting(
            ScriptSplitChunkAnalyzer.BoundaryCandidate::absoluteOffset)
            .containsExactly(20, 43);
        assertThat(result.candidates().get(0).sourceChunks()).containsExactlyInAnyOrder(1, 2);
        assertThat(max.get()).isLessThanOrEqualTo(2);
    }

    @Test
    void rejectsInvalidMarkersAndPersistsTheChunkAsRetryableFailure() {
        ScriptSplitSnapshotStore store = mock(ScriptSplitSnapshotStore.class);
        when(store.require(12L)).thenReturn(new ScriptSplitSnapshotStore.SplitSnapshot(
            12, 100, "hash", "CHUNK_FALLBACK", "EMPTY_RESPONSE", "RUNNING", 1, 0, 0));
        when(store.successfulChunks(12L)).thenReturn(List.of());
        when(store.retryableChunks(12L)).thenReturn(List.of(
            new ScriptSplitSnapshotStore.SplitChunk(
                1, 12, 1, 0, 6, 0, 6, "hash", "FAILED", null, null)));
        AiInvocationService invocation = mock(AiInvocationService.class);
        when(invocation.invokeText(any())).thenReturn(result(
            "{\"candidates\":[{\"marker\":\"不存在\",\"localOffset\":0}]}", 9));
        ScriptSplitChunkAnalyzer analyzer = new ScriptSplitChunkAnalyzer(
            store, snapshotId -> "有效原文", invocation, new ObjectMapper(),
            new WorkflowAgentProperties());

        Assertions.assertThatThrownBy(() -> analyzer.analyze(
            new ScriptSplitChunkAnalyzer.AnalysisContext(1, 2, 3, null, 7, 100), 12))
            .isInstanceOf(com.antshorttv.common.BusinessException.class);
        verify(store).markChunkFailed(
            org.mockito.ArgumentMatchers.eq(12L), org.mockito.ArgumentMatchers.eq(1),
            org.mockito.ArgumentMatchers.eq("CHUNK_ANALYSIS_FAILED"), any());
    }

    @Test
    void recomputesAnIncorrectModelOffsetFromAUniqueMarkerAndNormalizesLineEndings() {
        String source = "序章\r\n---\r\n第二集：重逢\r\n正文";
        ScriptSplitSnapshotStore store = mock(ScriptSplitSnapshotStore.class);
        when(store.require(13L)).thenReturn(new ScriptSplitSnapshotStore.SplitSnapshot(
            13, 101, "hash", "CHUNK_FALLBACK", "OUTPUT_TRUNCATED", "RUNNING", 1, 0, 0));
        when(store.successfulChunks(13L)).thenReturn(List.of());
        when(store.retryableChunks(13L)).thenReturn(List.of(
            new ScriptSplitSnapshotStore.SplitChunk(
                1, 13, 1, 0, source.length(), 0, source.length(), "hash", "PENDING", null, null)));
        AiInvocationService invocation = mock(AiInvocationService.class);
        when(invocation.invokeText(any())).thenReturn(result(
            "{\"candidates\":[{\"marker\":\"---\\n第二集：重逢\",\"localOffset\":0}]}", 10));
        ScriptSplitChunkAnalyzer analyzer = new ScriptSplitChunkAnalyzer(
            store, snapshotId -> source, invocation, new ObjectMapper(),
            new WorkflowAgentProperties());

        ScriptSplitChunkAnalyzer.ChunkAnalysisResult result = analyzer.analyze(
            new ScriptSplitChunkAnalyzer.AnalysisContext(1, 2, 3, null, 7, 101), 13);

        assertThat(result.candidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.absoluteOffset()).isEqualTo(4);
            assertThat(candidate.marker()).isEqualTo("---\r\n第二集：重逢");
        });
    }

    @Test
    void keepsVerifiedCandidatesWhenTheSameChunkAlsoContainsAnInvalidSuggestion() {
        String source = "第一集\n正文\n第二集\n正文";
        ScriptSplitSnapshotStore store = mock(ScriptSplitSnapshotStore.class);
        when(store.require(14L)).thenReturn(new ScriptSplitSnapshotStore.SplitSnapshot(
            14, 102, "hash", "CHUNK_FALLBACK", "OUTPUT_TRUNCATED", "RUNNING", 1, 0, 0));
        when(store.successfulChunks(14L)).thenReturn(List.of());
        when(store.retryableChunks(14L)).thenReturn(List.of(
            new ScriptSplitSnapshotStore.SplitChunk(
                1, 14, 1, 0, source.length(), 0, source.length(), "hash", "PENDING", null, null)));
        AiInvocationService invocation = mock(AiInvocationService.class);
        when(invocation.invokeText(any())).thenReturn(result("""
            {"candidates":[
              {"marker":"第二集","localOffset":0},
              {"marker":"不存在的场次","localOffset":0}
            ]}
            """, 11));
        ScriptSplitChunkAnalyzer analyzer = new ScriptSplitChunkAnalyzer(
            store, snapshotId -> source, invocation, new ObjectMapper(),
            new WorkflowAgentProperties());

        ScriptSplitChunkAnalyzer.ChunkAnalysisResult result = analyzer.analyze(
            new ScriptSplitChunkAnalyzer.AnalysisContext(1, 2, 3, null, 7, 102), 14);

        assertThat(result.candidates()).extracting(
            ScriptSplitChunkAnalyzer.BoundaryCandidate::marker).containsExactly("第二集");
        verify(store).markChunkSucceeded(org.mockito.ArgumentMatchers.eq(14L),
            org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.eq(11L), any());
    }

    private ScriptSplitSnapshotStore.SplitChunk chunk(int no, int start, int end) {
        return new ScriptSplitSnapshotStore.SplitChunk(
            no, 11, no, start, end, start, end, "hash-" + no, "PENDING", null, null);
    }

    private AiInvocationResult<AiTextResponse> result(String content, long callId) {
        AiTextResponse response = new AiTextResponse(
            content, "req", 1, 1, 2, 1, Map.of(), "stop", false);
        return AiInvocationResult.success(AiCapability.TEXT, "workflow_agent", response,
            content, callId, "req", 7L, 8L, "DeepSeek", 1, 1, 2, 1L);
    }
}
