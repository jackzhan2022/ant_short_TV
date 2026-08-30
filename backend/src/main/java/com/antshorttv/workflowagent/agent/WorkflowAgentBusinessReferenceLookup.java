package com.antshorttv.workflowagent.agent;

import java.util.List;

public abstract class WorkflowAgentBusinessReferenceLookup {
    public abstract List<String> findReferences(String agentCode);
}
