package com.antshorttv.workflowagent.run;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.workflowagent.tool.WorkflowToolRunState;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record WorkflowAgentRunContract(
    List<String> requiredToolSequence,
    String terminalToolCode
) {
    private static final List<String> SPLIT_FALLBACK_SEQUENCE = List.of(
        "read_script_structure", "analyze_script_chunks", "save_episode_splitting");
    private static final WorkflowAgentRunContract NONE = new WorkflowAgentRunContract(List.of(), null);

    public WorkflowAgentRunContract {
        requiredToolSequence = requiredToolSequence == null ? List.of() : List.copyOf(requiredToolSequence);
    }

    public static WorkflowAgentRunContract forAgent(String agentCode) {
        return switch (agentCode == null ? "" : agentCode) {
            case "short-drama-global-understanding" -> new WorkflowAgentRunContract(
                List.of("read_current_script", "save_global_understanding"),
                "save_global_understanding"
            );
            case "short-drama-episode-splitting" -> new WorkflowAgentRunContract(
                List.of("read_current_script", "save_episode_splitting"),
                "save_episode_splitting"
            );
            case "short-drama-episode-summary" -> new WorkflowAgentRunContract(
                List.of("read_current_episode", "save_episode_summary"),
                "save_episode_summary"
            );
            case "short-drama-asset-recognition" -> new WorkflowAgentRunContract(
                List.of("read_current_episode", "save_episode_assets"),
                "save_episode_assets"
            );
            case "short-drama-storyboard" -> new WorkflowAgentRunContract(
                List.of("read_current_episode", "read_adjacent_episodes", "read_script_analysis",
                    "read_project_context", "read_script_assets", "save_episode_storyboards"),
                "save_episode_storyboards"
            );
            default -> NONE;
        };
    }

    public static WorkflowAgentRunContract forAgent(String agentCode, String reviewPhase) {
        if ("script-review".equals(agentCode)) return forReviewPhase(reviewPhase);
        return forAgent(agentCode);
    }

    public static WorkflowAgentRunContract forReviewPhase(String phase) {
        return switch (phase == null ? "" : phase) {
            case "MARKDOWN_QUICK", "MARKDOWN_DEEP_CHILD" -> new WorkflowAgentRunContract(List.of(
                "read_review_context", "read_review_content"), null);
            case "MARKDOWN_DEEP_AGGREGATION" -> NONE;
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "未知剧本审核阶段。");
        };
    }

    public void requireNext(WorkflowToolRunState state, String toolCode) {
        if ("save_episode_assets".equals(terminalToolCode)) {
            var completed=state.successfulToolCodes();
            boolean first=completed.isEmpty() && "read_current_episode".equals(toolCode);
            boolean lookup=Set.of("search_script_assets","read_asset_details","save_episode_assets").contains(toolCode)
                && completed.contains("read_current_episode") && !completed.contains("save_episode_assets");
            if(first || lookup) return;
            throw new BusinessException(ErrorCode.REQUIRED_TOOL_NOT_CALLED,"必须按顺序调用工具：先读取当前剧集，再检索资产，最后保存。");
        }
        List<String> sequence = activeSequence(state);
        if (sequence.isEmpty()) {
            return;
        }
        if (isReviewContract()) {
            requireNextReviewTool(state, toolCode, sequence);
            return;
        }
        int completed = state.successfulToolCodes().size();
        if (completed >= sequence.size() || !sequence.get(completed).equals(toolCode)) {
            throw new BusinessException(ErrorCode.REQUIRED_TOOL_NOT_CALLED,
                "必须按顺序调用工具：" + String.join(" -> ", sequence));
        }
    }

    public void requireComplete(WorkflowToolRunState state) {
        List<String> sequence = activeSequence(state);
        boolean incomplete = isReviewContract() || "save_episode_assets".equals(terminalToolCode)
            ? !new HashSet<>(state.successfulToolCodes()).containsAll(sequence)
            : state.successfulToolCodes().size() < sequence.size();
        if (incomplete) {
            throw new BusinessException(ErrorCode.REQUIRED_TOOL_NOT_CALLED,
                "必须调用工具：" + String.join(" -> ", sequence));
        }
    }

    private void requireNextReviewTool(
        WorkflowToolRunState state,
        String toolCode,
        List<String> sequence
    ) {
        Set<String> completed = new HashSet<>(state.successfulToolCodes());
        String contextTool = sequence.get(0);
        if (!sequence.contains(toolCode)
            || (!contextTool.equals(toolCode) && !completed.contains(contextTool))) {
            throw reviewOrderError(sequence);
        }
    }

    private BusinessException reviewOrderError(List<String> sequence) {
        return new BusinessException(ErrorCode.REQUIRED_TOOL_NOT_CALLED,
            "必须先读取审核上下文，再读取可信正文：" + String.join(" -> ", sequence));
    }

    private boolean isReviewContract() {
        return requiredToolSequence.contains("read_review_context");
    }

    private List<String> activeSequence(WorkflowToolRunState state) {
        if ("save_episode_splitting".equals(terminalToolCode)
            && "CHUNK_FALLBACK".equals(state.splitMode())) {
            return SPLIT_FALLBACK_SEQUENCE;
        }
        return requiredToolSequence;
    }

    public boolean isTerminal(String toolCode) {
        return terminalToolCode != null && terminalToolCode.equals(toolCode);
    }

    public List<String> preparationToolCodes() {
        if (!"save_episode_storyboards".equals(terminalToolCode)) return List.of();
        return requiredToolSequence.stream().filter(tool -> !isTerminal(tool)).toList();
    }
}
