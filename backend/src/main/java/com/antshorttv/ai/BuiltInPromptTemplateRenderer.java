package com.antshorttv.ai;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Sole template source for text workflows that do not use a tool Agent. */
@Component
public class BuiltInPromptTemplateRenderer extends PromptTemplateRenderer {
    private static final Map<String, String> RESOURCES = Map.of(
        "script-rewrite", "script-rewrite",
        "video.understanding.analysis", "video-understanding",
        "video.script.draft", "video-script-draft"
    );
    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([a-zA-Z][a-zA-Z0-9]*)}");

    @Override
    public String render(String templateId, Map<String, Object> variables) {
        String resource = templateId == null ? null : RESOURCES.get(templateId);
        if (resource == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "提示词模板不存在：" + templateId);
        }
        String template;
        try {
            template = new ClassPathResource("prompts/" + resource + ".md").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("提示词模板不可用：" + templateId, exception);
        }
        Map<String, Object> values = variables == null ? Map.of() : variables;
        return VARIABLE.matcher(template).replaceAll(match -> {
            Object value = values.get(match.group(1));
            if (value == null || value.toString().isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模板变量缺失：" + match.group(1));
            }
            return java.util.regex.Matcher.quoteReplacement(value.toString());
        });
    }
}
