package com.antshorttv.ai;

import com.antshorttv.security.CurrentUserHolder;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class BuiltInAgentCatalogService {
    private final BuiltInAgentRegistry registry;

    public BuiltInAgentCatalogService(BuiltInAgentRegistry registry) {
        this.registry = registry;
    }

    public List<BuiltInAgentResponse> agents() {
        CurrentUserHolder.require();
        return registry.listAgents().stream().map(this::agentResponse).toList();
    }

    public BuiltInAgentResponse agent(String code) {
        CurrentUserHolder.require();
        return agentResponse(registry.findByCode(code));
    }

    public List<BuiltInSkillResponse> skills() {
        CurrentUserHolder.require();
        return registry.listSkills().stream().map(this::skillResponse).toList();
    }

    public BuiltInSkillResponse skill(String code) {
        CurrentUserHolder.require();
        return skillResponse(registry.findSkillByCode(code));
    }

    public BuiltInAgentPreviewResponse preview(String code, Map<String, Object> variables) {
        CurrentUserHolder.require();
        BuiltInAgentDefinition agent = registry.findByCode(code);
        return new BuiltInAgentPreviewResponse(code, registry.render(code, variables), agent.outputSchema());
    }

    private BuiltInAgentResponse agentResponse(BuiltInAgentDefinition agent) {
        List<BuiltInSkillSummaryResponse> skills = agent.skillCodes().stream()
            .map(registry::findSkillByCode)
            .map(BuiltInSkillSummaryResponse::from)
            .toList();
        return new BuiltInAgentResponse(
            agent.code(),
            agent.name(),
            agent.description(),
            agent.scene().code(),
            agent.scene().displayName(),
            agent.capability().name(),
            "PLATFORM_DEFAULT",
            agent.variables().stream().map(BuiltInAgentVariableResponse::from).toList(),
            agent.outputSchema(),
            skills
        );
    }

    private BuiltInSkillResponse skillResponse(BuiltInSkillDefinition skill) {
        List<BuiltInAgentSummaryResponse> agents = registry.listAgents().stream()
            .filter(agent -> agent.skillCodes().contains(skill.code()))
            .map(BuiltInAgentSummaryResponse::from)
            .toList();
        return new BuiltInSkillResponse(
            skill.code(),
            skill.name(),
            skill.description(),
            skill.category(),
            skill.content(),
            agents
        );
    }
}
