package com.antshorttv.workflowagent.run;

import com.antshorttv.workflowagent.agent.WorkflowAgentRecord;
import java.util.List;

public record WorkflowAgentExecutionPlan(
    WorkflowAgentRecord agent,
    List<WorkflowAgentSkillSnapshot> skillSnapshots,
    Long tenantId
) {
    public WorkflowAgentExecutionPlan {
        skillSnapshots = List.copyOf(skillSnapshots);
    }

    public boolean completeAssetPrompts() {
        var parser = new com.antshorttv.workflowagent.skill.SkillDocumentParser();
        return skillSnapshots.stream().filter(s -> s.content().startsWith("---\n"))
            .map(s -> parser.parse(s.code(), s.content()))
            .anyMatch(s -> "asset-extraction".equals(s.purpose()) && "complete".equals(s.promptContract()));
    }

    public WorkflowAgentExecutionPlan(WorkflowAgentRecord agent, List<WorkflowAgentSkillSnapshot> skillSnapshots) {
        this(agent, skillSnapshots, null);
    }
}
