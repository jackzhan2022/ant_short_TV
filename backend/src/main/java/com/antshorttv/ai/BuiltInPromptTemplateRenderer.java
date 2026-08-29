package com.antshorttv.ai;

import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class BuiltInPromptTemplateRenderer extends PromptTemplateRenderer {
    private static final Map<String, String> LEGACY_TEMPLATE_TO_AGENT = Map.of(
        "script.element.character.extract", "script-character-extract",
        "script.element.scene.extract", "script-scene-extract",
        "script.element.prop.extract", "script-prop-extract",
        "video.understanding.analysis", "video-understanding",
        "video.script.draft", "video-script-draft"
    );

    private final BuiltInAgentRegistry registry;
    @Autowired(required = false)
    private AiAgentDefinitionMapper definitionMapper;
    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    public BuiltInPromptTemplateRenderer() {
        this.registry = new BuiltInAgentRegistry();
    }

    @Override
    public String render(String templateId, Map<String, Object> variables) {
        String agentCode = LEGACY_TEMPLATE_TO_AGENT.getOrDefault(templateId, templateId);
        if (definitionMapper != null) {
            AiAgentDefinitionEntity definition = definitionMapper.selectOne(new LambdaQueryWrapper<AiAgentDefinitionEntity>()
                .eq(AiAgentDefinitionEntity::getCode, agentCode)
                .eq(AiAgentDefinitionEntity::getPublished, true)
                .eq(AiAgentDefinitionEntity::getStatus, "ENABLED")
                .orderByDesc(AiAgentDefinitionEntity::getVersionNo)
                .last("limit 1"));
            if (definition != null) {
                StringBuilder source = new StringBuilder(definition.getPromptTemplate());
                if (definition.getOutputSchema() != null && !definition.getOutputSchema().isBlank()) {
                    source.append("\n\n输出 Schema（必须严格遵守；name 不得为空，未知值使用空字符串或空数组）：\n")
                        .append(definition.getOutputSchema());
                }
                if (jdbcTemplate != null) {
                    var skills = jdbcTemplate.query("""
                        select skill.code, skill.content
                          from ai_agent_skill binding
                          join ai_skill_definition skill on skill.id = binding.skill_definition_id
                         where binding.agent_definition_id = ?
                           and skill.published = true
                           and skill.status = 'ENABLED'
                         order by binding.sort_order, skill.id
                        """, (rs, rowNum) -> Map.entry(rs.getString("code"), rs.getString("content")), definition.getId());
                    if (!skills.isEmpty()) {
                        source.append("\n\n技能约束（按顺序执行）：");
                        skills.forEach(skill -> source.append("\n\n### ")
                            .append(skill.getKey())
                            .append("\n")
                            .append(skill.getValue()));
                    }
                }
                String prompt = source.toString();
                if (variables != null) {
                    for (var entry : variables.entrySet()) {
                        prompt = prompt.replace("${" + entry.getKey() + "}", String.valueOf(entry.getValue()));
                    }
                }
                return prompt;
            }
        }
        return registry.render(agentCode, variables);
    }
}
