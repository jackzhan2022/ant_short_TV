package com.antshorttv.workflowagent.skill;

import com.antshorttv.workflowagent.agent.StoryboardAgentBootstrap;
import java.util.Set;

public final class TenantStoryboardSkillPolicy {
    public static final Set<String> EDITABLE_CODES = Set.of(StoryboardAgentBootstrap.SKILL_CODE);

    private TenantStoryboardSkillPolicy() { }

    public static boolean canOverride(String agentCode, String skillCode) {
        return StoryboardAgentBootstrap.AGENT_CODE.equals(agentCode) && EDITABLE_CODES.contains(skillCode);
    }
}
