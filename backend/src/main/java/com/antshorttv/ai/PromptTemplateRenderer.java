package com.antshorttv.ai;

import java.util.Map;

public abstract class PromptTemplateRenderer {
    public abstract String render(String templateId, Map<String, Object> variables);
}
