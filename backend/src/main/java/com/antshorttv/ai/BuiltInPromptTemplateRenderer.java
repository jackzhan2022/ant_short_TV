package com.antshorttv.ai;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import org.springframework.stereotype.Component;

@Component
public class BuiltInPromptTemplateRenderer extends PromptTemplateRenderer {
    private static final Map<String, Template> TEMPLATES = Map.of(
        "script.element.character.extract",
        new Template(
            List.of("scriptTitle", "scriptContent"),
            """
                你是短剧剧本结构化信息提取助手。
                请仅基于剧本内容提取角色信息，只返回合法 JSON，不要解释，不要 Markdown，不要代码块。
                字段缺失时使用空字符串或空数组，不要编造。
                角色名称要稳定，尽量使用剧本中明确出现的称呼，便于后续按名称合并。

                返回结构：
                {
                  "characters": [
                    {
                      "name": "",
                      "roleType": "LEAD",
                      "gender": "",
                      "ageRange": "",
                      "identity": "",
                      "personality": [],
                      "appearance": "",
                      "prompt": ""
                    }
                  ]
                }

                剧本标题：${scriptTitle}
                剧本内容：
                <<<
                ${scriptContent}
                >>>
                """
        ),
        "script.element.scene.extract",
        new Template(
            List.of("scriptTitle", "scriptContent"),
            """
                你是短剧剧本结构化信息提取助手。
                请仅基于剧本内容提取场景信息，只返回合法 JSON，不要解释，不要 Markdown，不要代码块。
                字段缺失时使用空字符串，不要编造。
                场景名称要稳定，尽量使用剧本中明确出现的场景名，便于后续按名称合并。

                返回结构：
                {
                  "scenes": [
                    {
                      "name": "",
                      "sceneType": "INTERIOR",
                      "atmosphere": "",
                      "description": "",
                      "visualStyle": "",
                      "prompt": ""
                    }
                  ]
                }

                剧本标题：${scriptTitle}
                剧本内容：
                <<<
                ${scriptContent}
                >>>
                """
        ),
        "script.element.prop.extract",
        new Template(
            List.of("scriptTitle", "scriptContent"),
            """
                你是短剧剧本结构化信息提取助手。
                请仅基于剧本内容提取道具信息，只返回合法 JSON，不要解释，不要 Markdown，不要代码块。
                字段缺失时使用空字符串，不要编造。
                道具名称要稳定，尽量使用剧本中明确出现的称呼，便于后续按名称合并。

                返回结构：
                {
                  "props": [
                    {
                      "name": "",
                      "propType": "KEY_PROP",
                      "appearance": "",
                      "plotFunction": "",
                      "prompt": ""
                    }
                  ]
                }

                剧本标题：${scriptTitle}
                剧本内容：
                <<<
                ${scriptContent}
                >>>
                """
        ),
        "video.understanding.analysis",
        new Template(
            List.of("episodeNo"),
            """
                你是短剧视频拆剧助手。请分析该短剧视频第 ${episodeNo} 集，只返回合法 JSON，不要解释，不要 Markdown。
                必须包含 characters、scenes、props、timeline、dialogue、actions、emotions 七个数组字段。
                字段缺失时使用空数组，不要编造无法从视频确认的信息。
                """
        ),
        "video.script.draft",
        new Template(
            List.of("episodeNo", "normalizedJson"),
            """
                请把以下第 ${episodeNo} 集视频拆解 JSON 改写成中文短剧剧本。
                要求：
                1. 保留第 ${episodeNo} 集标识。
                2. 按场次输出，包含场景、人物、动作、对白、情绪。
                3. 只根据 JSON 信息创作，不新增无法推断的关键情节。

                JSON:
                ${normalizedJson}
                """
        )
    );

    @Override
    public String render(String templateId, Map<String, Object> variables) {
        Template template = TEMPLATES.get(templateId);
        if (template == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Prompt 模板不存在：" + templateId);
        }
        Map<String, Object> safeVariables = variables == null ? Map.of() : variables;
        for (String requiredVariable : template.requiredVariables()) {
            Object value = safeVariables.get(requiredVariable);
            if (value == null || value.toString().isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Prompt 模板变量缺失：" + requiredVariable);
            }
        }
        String rendered = template.content();
        for (Map.Entry<String, Object> entry : safeVariables.entrySet()) {
            rendered = rendered.replaceAll("\\$\\{" + entry.getKey() + "}", Matcher.quoteReplacement(String.valueOf(entry.getValue())));
        }
        return rendered;
    }

    private record Template(List<String> requiredVariables, String content) {
    }
}
