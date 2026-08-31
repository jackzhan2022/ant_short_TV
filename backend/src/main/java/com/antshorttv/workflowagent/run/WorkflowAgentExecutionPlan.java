package com.antshorttv.workflowagent.run;

import com.antshorttv.workflowagent.agent.WorkflowAgentRecord;
import java.util.List;

public record WorkflowAgentExecutionPlan(
    WorkflowAgentRecord agent,
    List<WorkflowAgentSkillSnapshot> skillSnapshots
) {
    public WorkflowAgentExecutionPlan {
        skillSnapshots = List.copyOf(skillSnapshots);
    }
}
