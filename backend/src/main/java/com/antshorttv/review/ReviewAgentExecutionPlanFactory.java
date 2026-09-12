package com.antshorttv.review;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.workflowagent.agent.ScriptReviewAgentBootstrap;
import com.antshorttv.workflowagent.agent.WorkflowAgentRecord;
import com.antshorttv.workflowagent.agent.WorkflowAgentService;
import com.antshorttv.workflowagent.run.WorkflowAgentExecutionPlan;
import com.antshorttv.workflowagent.run.WorkflowAgentSkillSnapshot;
import com.antshorttv.workflowagent.skill.WorkflowSkillService;
import com.antshorttv.workflowagent.skill.WorkflowSkillView;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ReviewAgentExecutionPlanFactory {
    private final WorkflowAgentService agents;
    private final WorkflowSkillService skills;

    public ReviewAgentExecutionPlanFactory(WorkflowAgentService agents, WorkflowSkillService skills) {
        this.agents = agents;
        this.skills = skills;
    }

    public WorkflowAgentExecutionPlan freeze(List<String> selectedDimensions, String phase) {
        WorkflowAgentRecord maximum = agents.loadForRun(ScriptReviewAgentBootstrap.AGENT_CODE);
        boolean aggregation = "MARKDOWN_DEEP_AGGREGATION".equals(phase);
        if (!List.of("MARKDOWN_QUICK", "MARKDOWN_DEEP_CHILD", "MARKDOWN_DEEP_AGGREGATION").contains(phase)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "未知剧本审核阶段。");
        }
        List<String> skillCodes;
        try {
            skillCodes = ReviewDimension.skillCodes(ReviewDimension.parseAll(selectedDimensions), aggregation);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, exception.getMessage());
        }
        if (!maximum.skillCodes().containsAll(skillCodes)) {
            throw new BusinessException(ErrorCode.WORKFLOW_AGENT_TOOL_UNAUTHORIZED,
                "剧本审核 Agent 未授权所需 Skill。");
        }
        List<String> toolCodes = switch (phase) {
            case "MARKDOWN_QUICK", "MARKDOWN_DEEP_CHILD" -> List.of(
                "read_review_context", "read_review_content");
            case "MARKDOWN_DEEP_AGGREGATION" -> List.of();
            default -> throw new IllegalStateException();
        };
        if (!maximum.toolCodes().containsAll(toolCodes)) {
            throw new BusinessException(ErrorCode.WORKFLOW_AGENT_TOOL_UNAUTHORIZED,
                "剧本审核 Agent 未授权当前阶段工具。");
        }
        WorkflowAgentRecord narrowed = new WorkflowAgentRecord(maximum.id(), maximum.code(), maximum.name(),
            maximum.description(), maximum.systemPrompt(), maximum.modelId(), maximum.temperature(),
            maximum.maxTokens(), maximum.maxSteps(), maximum.status(), maximum.revision(), maximum.createdBy(),
            maximum.updatedBy(), maximum.createdAt(), maximum.updatedAt(), skillCodes, toolCodes);
        List<WorkflowAgentSkillSnapshot> snapshots = skillCodes.stream().map(this::snapshot).toList();
        return new WorkflowAgentExecutionPlan(narrowed, snapshots);
    }

    private WorkflowAgentSkillSnapshot snapshot(String code) {
        WorkflowSkillView skill = skills.detail(code);
        return new WorkflowAgentSkillSnapshot(skill.code(), skill.name(), skill.revision(), skill.content());
    }
}
