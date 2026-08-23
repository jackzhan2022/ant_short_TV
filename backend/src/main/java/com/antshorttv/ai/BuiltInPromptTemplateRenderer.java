package com.antshorttv.ai;

import java.util.Map;
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

    public BuiltInPromptTemplateRenderer() {
        this.registry = new BuiltInAgentRegistry();
    }

    @Override
    public String render(String templateId, Map<String, Object> variables) {
        String agentCode = LEGACY_TEMPLATE_TO_AGENT.getOrDefault(templateId, templateId);
        return registry.render(agentCode, variables);
    }
}
