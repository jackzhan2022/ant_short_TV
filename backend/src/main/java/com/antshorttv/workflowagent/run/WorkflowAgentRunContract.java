package com.antshorttv.workflowagent.run;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.workflowagent.tool.WorkflowToolRunState;
import java.util.List;

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
            default -> NONE;
        };
    }

    public void requireNext(WorkflowToolRunState state, String toolCode) {
        List<String> sequence = activeSequence(state);
        if (sequence.isEmpty()) {
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
        if (state.successfulToolCodes().size() < sequence.size()) {
            throw new BusinessException(ErrorCode.REQUIRED_TOOL_NOT_CALLED,
                "必须调用工具：" + String.join(" -> ", sequence));
        }
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
}
