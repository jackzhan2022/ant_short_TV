package com.antshorttv.workflowagent.skill;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcSkillReferenceLookup extends SkillReferenceLookup {
    private final JdbcTemplate jdbc;

    public JdbcSkillReferenceLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<String> findAgentCodes(String skillCode) {
        List<String> agents = jdbc.queryForList("""
            select agent.code
              from ai_workflow_agent_skill association
              join ai_workflow_agent agent on agent.id = association.agent_id
             where association.skill_code = ?
             order by agent.code
            """, String.class, skillCode);
        if (com.antshorttv.project.StoryboardSkillVersion.V2.skillCode().equals(skillCode)) {
            return java.util.stream.Stream.concat(agents.stream(),
                java.util.stream.Stream.of(com.antshorttv.workflowagent.agent.StoryboardAgentBootstrap.AGENT_CODE))
                .distinct().sorted().toList();
        }
        return agents;
    }
}
