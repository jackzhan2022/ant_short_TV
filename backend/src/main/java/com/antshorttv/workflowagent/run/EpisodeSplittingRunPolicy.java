package com.antshorttv.workflowagent.run;

import com.antshorttv.ai.AiTextResponse;
import com.antshorttv.workflowagent.WorkflowAgentProperties;
import com.antshorttv.workflowagent.tool.WorkflowToolRunState;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class EpisodeSplittingRunPolicy {
    public static final String AGENT_CODE = "short-drama-episode-splitting";
    private final WorkflowAgentProperties properties;
    private final ScriptSourceReader sourceReader;

    @Autowired
    public EpisodeSplittingRunPolicy(WorkflowAgentProperties properties, JdbcTemplate jdbc) {
        this(properties, input -> jdbc.queryForObject("""
            select content from script
             where tenant_id = ? and project_id = ? and id = ? and deleted_at is null
            """, String.class, input.tenantId(), input.projectId(), input.scriptId()));
    }

    EpisodeSplittingRunPolicy(WorkflowAgentProperties properties, ScriptSourceReader sourceReader) {
        this.properties = properties;
        this.sourceReader = sourceReader;
    }

    public Optional<FallbackReason> preflight(WorkflowAgentRunInput input) {
        if (!AGENT_CODE.equals(input.agentCode()) || input.scriptId() == null) return Optional.empty();
        String source = sourceReader.read(input);
        long estimated = (long) Math.ceil(source.getBytes(StandardCharsets.UTF_8).length / 3.0)
            + properties.getSplitPromptReserveTokens() + properties.getSplitToolReserveTokens();
        return estimated > properties.getSplitSafeContextTokens()
            ? Optional.of(FallbackReason.CONTEXT_PREFLIGHT) : Optional.empty();
    }

    public Optional<FallbackReason> classify(AiTextResponse response, WorkflowToolRunState state) {
        if ("CHUNK_FALLBACK".equals(state.splitMode())) return Optional.empty();
        if (response == null) return Optional.of(FallbackReason.EMPTY_RESPONSE);
        if (response.truncated() || "length".equalsIgnoreCase(response.finishReason())) {
            return Optional.of(FallbackReason.OUTPUT_TRUNCATED);
        }
        if (response.toolCalls() != null && !response.toolCalls().isEmpty()) return Optional.empty();
        if (response.content() == null || response.content().isBlank()) {
            return Optional.of(FallbackReason.EMPTY_RESPONSE);
        }
        return Optional.of(FallbackReason.SAVE_NOT_CALLED);
    }

    public Optional<FallbackReason> classifyGateway(Throwable error, WorkflowToolRunState state) {
        if ("CHUNK_FALLBACK".equals(state.splitMode())) return Optional.empty();
        String message = error == null || error.getMessage() == null
            ? "" : error.getMessage().toLowerCase(java.util.Locale.ROOT);
        return message.contains("context") || message.contains("token") || message.contains("length")
            ? Optional.of(FallbackReason.CONTEXT_ERROR) : Optional.empty();
    }

    @FunctionalInterface
    interface ScriptSourceReader { String read(WorkflowAgentRunInput input); }

    public enum SplitMode { FULL, CHUNK_FALLBACK }
    public enum FallbackReason {
        CONTEXT_PREFLIGHT, CONTEXT_ERROR, OUTPUT_TRUNCATED, EMPTY_RESPONSE, SAVE_NOT_CALLED
    }
}
