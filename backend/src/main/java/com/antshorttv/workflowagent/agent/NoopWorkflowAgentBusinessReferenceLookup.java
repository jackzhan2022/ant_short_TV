package com.antshorttv.workflowagent.agent;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NoopWorkflowAgentBusinessReferenceLookup extends WorkflowAgentBusinessReferenceLookup {
    @Override
    public List<String> findReferences(String agentCode) {
        return List.of();
    }
}
