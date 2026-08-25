package com.antshorttv.ai;

import com.antshorttv.security.CurrentPrincipal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class BuiltInAgentCatalogService {
    private final BuiltInAgentRegistry registry;
    private final CurrentPrincipal currentPrincipal;

    public BuiltInAgentCatalogService(BuiltInAgentRegistry registry, CurrentPrincipal currentPrincipal) {
        this.registry = registry;
        this.currentPrincipal = currentPrincipal;
    }

    public List<BuiltInAgentResponse> agents() {
        currentPrincipal.require();
        return registry.listAgents().stream().map(this::agentResponse).toList();
    }

    public BuiltInAgentResponse agent(String code) {
        currentPrincipal.require();
        return agentResponse(registry.findByCode(code));
    }

    public List<BuiltInSkillResponse> skills() {
        currentPrincipal.require();
        return registry.listSkills().stream().map(this::skillResponse).toList();
    }

    public BuiltInSkillResponse skill(String code) {
        currentPrincipal.require();
        return skillResponse(registry.findSkillByCode(code));
    }

    public BuiltInAgentPreviewResponse preview(String code, Map<String, Object> variables) {
        currentPrincipal.require();
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
