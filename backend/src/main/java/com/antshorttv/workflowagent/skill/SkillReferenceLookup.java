package com.antshorttv.workflowagent.skill;

import java.util.List;

public abstract class SkillReferenceLookup {
    public abstract List<String> findAgentCodes(String skillCode);
}
