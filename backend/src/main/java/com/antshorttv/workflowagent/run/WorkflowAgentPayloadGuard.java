package com.antshorttv.workflowagent.run;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Set;

final class WorkflowAgentPayloadGuard {
    private static final Set<String> BOUNDED_SAVE_TOOLS = Set.of(
        "save_global_understanding",
        "save_episode_splitting",
        "save_episode_summary",
        "save_episode_assets",
        "save_episode_storyboards",
        "save_review_unit_result",
        "save_review_result"
    );

    private WorkflowAgentPayloadGuard() {
    }

    static void requireBounded(String toolCode, String argumentsJson, long maxBytes) {
        if (!BOUNDED_SAVE_TOOLS.contains(toolCode) || argumentsJson == null) {
            return;
        }
        if (argumentsJson.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new BusinessException(ErrorCode.WORKFLOW_AGENT_TOOL_INVALID,
                "Agent 保存负载超过允许大小。");
        }
    }
}
