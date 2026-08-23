package com.antshorttv.ai;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class BuiltInAgentRegistry {
    private final List<BuiltInSkillDefinition> skills;
    private final List<BuiltInAgentDefinition> agents;
    private final Map<String, BuiltInSkillDefinition> skillsByCode;
    private final Map<String, BuiltInAgentDefinition> agentsByCode;
    private final Map<AiBusinessScene, BuiltInAgentDefinition> agentsByScene;

    public BuiltInAgentRegistry() {
        this.skills = List.of(
            new BuiltInSkillDefinition(
                "strict-json-output",
                "严格 JSON 输出",
                "要求模型返回可解析的 JSON，不包含解释或 Markdown。",
                "OUTPUT",
                "请只返回合法 JSON，不要解释，不要 Markdown，不要代码块。"
            ),
            new BuiltInSkillDefinition(
                "no-invention",
                "不得编造",
                "缺失信息使用空值，不补充无法从输入确认的事实。",
                "SAFETY",
                "字段缺失时使用空字符串或空数组，不要编造，不要新增无法从输入确认的信息。"
            ),
            new BuiltInSkillDefinition(
                "stable-entity-naming",
                "实体命名稳定",
                "要求角色、场景和道具名称保持稳定，便于后续合并。",
                "DOMAIN",
                "角色名称要稳定，优先使用输入中明确出现的称呼，便于后续按名称合并。"
            ),
            new BuiltInSkillDefinition(
                "short-drama-structure",
                "短剧结构化表达",
                "按短剧制作所需的角色、场景、道具、动作和对白维度组织内容。",
                "DOMAIN",
                "输出应服务于短剧制作流程，结构清晰，字段含义稳定，不要把多个实体合并到一个字段中。"
            ),
            new BuiltInSkillDefinition(
                "script-review-rules",
                "剧本审核规则",
                "从完整性、一致性、逻辑性和可执行性检查剧本。",
                "REVIEW",
                "请检查剧情完整性、人物前后一致性、情节逻辑、场景衔接和短剧拍摄可执行性，并为每个问题给出严重程度与修改建议。"
            ),
            new BuiltInSkillDefinition(
                "review-json-output",
                "审核结果 JSON",
                "要求审核结果按问题、评分和结论结构化输出。",
                "OUTPUT",
                "审核结果必须按约定 JSON 结构返回，问题列表为空时也必须返回空数组。"
            )
        );
        this.agents = List.of(
            agent(
                "script-rewrite",
                "AI 改写剧本",
                "根据改写要求重写短剧剧本。",
                AiBusinessScene.SCRIPT_REWRITE,
                """
                    你是专业的中文短剧剧本改写助手。
                    请基于原剧本和改写要求完成改写，保留核心人物关系与主要情节。

                    原剧本：
                    <<<
                    ${scriptContent}
                    >>>

                    改写要求：
                    <<<
                    ${rewriteRequirement}
                    >>>
                    """,
                List.of(
                    variable("scriptContent", "原剧本", "TEXT"),
                    variable("rewriteRequirement", "改写要求", "TEXT")
                ),
                "改写后的中文短剧剧本",
                List.of("no-invention", "short-drama-structure")
            ),
            agent(
                "script-character-extract",
                "提取角色",
                "从剧本中提取角色结构化信息。",
                AiBusinessScene.CHARACTER_EXTRACT,
                """
                    你是短剧剧本结构化信息提取助手。
                    请仅基于剧本内容提取角色信息。

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
                    """,
                List.of(
                    variable("scriptTitle", "剧本标题", "TEXT"),
                    variable("scriptContent", "剧本内容", "TEXT")
                ),
                "{\"characters\":[{\"name\":\"\",\"roleType\":\"LEAD\",\"gender\":\"\",\"ageRange\":\"\",\"identity\":\"\",\"personality\":[],\"appearance\":\"\",\"prompt\":\"\"}]}",
                List.of("strict-json-output", "no-invention", "stable-entity-naming")
            ),
            agent(
                "script-scene-extract",
                "提取场景",
                "从剧本中提取场景结构化信息。",
                AiBusinessScene.SCENE_EXTRACT,
                """
                    你是短剧剧本结构化信息提取助手。
                    请仅基于剧本内容提取场景信息。

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
                    """,
                List.of(
                    variable("scriptTitle", "剧本标题", "TEXT"),
                    variable("scriptContent", "剧本内容", "TEXT")
                ),
                "{\"scenes\":[{\"name\":\"\",\"sceneType\":\"INTERIOR\",\"atmosphere\":\"\",\"description\":\"\",\"visualStyle\":\"\",\"prompt\":\"\"}]}",
                List.of("strict-json-output", "no-invention", "stable-entity-naming")
            ),
            agent(
                "script-prop-extract",
                "提取道具",
                "从剧本中提取关键道具结构化信息。",
                AiBusinessScene.PROP_EXTRACT,
                """
                    你是短剧剧本结构化信息提取助手。
                    请仅基于剧本内容提取道具信息。

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
                    """,
                List.of(
                    variable("scriptTitle", "剧本标题", "TEXT"),
                    variable("scriptContent", "剧本内容", "TEXT")
                ),
                "{\"props\":[{\"name\":\"\",\"propType\":\"KEY_PROP\",\"appearance\":\"\",\"plotFunction\":\"\",\"prompt\":\"\"}]}",
                List.of("strict-json-output", "no-invention", "stable-entity-naming")
            ),
            agent(
                "video-understanding",
                "剧本拆剧：视频理解",
                "分析短剧视频并输出结构化拆剧信息。",
                AiBusinessScene.VIDEO_UNDERSTANDING,
                """
                    你是短剧视频拆剧助手。请分析短剧视频第 ${episodeNo} 集。
                    必须包含 characters、scenes、props、timeline、dialogue、actions、emotions 七个数组字段。
                    """,
                List.of(variable("episodeNo", "集数", "NUMBER")),
                "{\"characters\":[],\"scenes\":[],\"props\":[],\"timeline\":[],\"dialogue\":[],\"actions\":[],\"emotions\":[]}",
                List.of("strict-json-output", "no-invention", "short-drama-structure")
            ),
            agent(
                "video-script-draft",
                "剧本拆剧：草稿生成",
                "把视频拆解 JSON 改写成可审核的中文短剧剧本。",
                AiBusinessScene.VIDEO_SCRIPT_DRAFT,
                """
                    请把以下第 ${episodeNo} 集视频拆解 JSON 改写成中文短剧剧本。
                    要求：
                    1. 保留第 ${episodeNo} 集标识。
                    2. 按场次输出，包含场景、人物、动作、对白、情绪。

                    JSON：
                    ${normalizedJson}
                    """,
                List.of(
                    variable("episodeNo", "集数", "NUMBER"),
                    variable("normalizedJson", "结构化拆解 JSON", "JSON")
                ),
                "第 ${episodeNo} 集中文短剧剧本",
                List.of("no-invention", "short-drama-structure")
            ),
            agent(
                "script-review",
                "剧本审核",
                "审核短剧剧本内容并输出问题、评分和结论。",
                AiBusinessScene.SCRIPT_REVIEW,
                """
                    你是专业的中文短剧剧本审核助手。
                    请审核以下剧本，并区分致命问题、重要问题和建议项。

                    剧本标题：${scriptTitle}
                    剧本内容：
                    <<<
                    ${scriptContent}
                    >>>

                    返回结构：
                    {
                      "overallScore": 0,
                      "conclusion": "PASS",
                      "issues": [
                        {
                          "severity": "HIGH",
                          "category": "",
                          "description": "",
                          "suggestion": ""
                        }
                      ]
                    }
                    """,
                List.of(
                    variable("scriptTitle", "剧本标题", "TEXT"),
                    variable("scriptContent", "剧本内容", "TEXT")
                ),
                "{\"overallScore\":0,\"conclusion\":\"PASS\",\"issues\":[]}",
                List.of("strict-json-output", "no-invention", "script-review-rules", "review-json-output")
            )
        );
        this.skillsByCode = index(this.skills, BuiltInSkillDefinition::code);
        this.agentsByCode = index(this.agents, BuiltInAgentDefinition::code);
        this.agentsByScene = this.agents.stream().collect(Collectors.toUnmodifiableMap(
            BuiltInAgentDefinition::scene,
            Function.identity()
        ));
        validate();
    }

    public List<BuiltInSkillDefinition> listSkills() {
        return skills;
    }

    public List<BuiltInAgentDefinition> listAgents() {
        return agents;
    }

    public BuiltInAgentDefinition findByCode(String code) {
        BuiltInAgentDefinition definition = agentsByCode.get(code);
        if (definition == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "内置 Agent 不存在：" + code);
        }
        return definition;
    }

    public BuiltInAgentDefinition findByScene(AiBusinessScene scene) {
        BuiltInAgentDefinition definition = agentsByScene.get(scene);
        if (definition == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "业务场景未配置内置 Agent：" + scene);
        }
        return definition;
    }

    public BuiltInSkillDefinition findSkillByCode(String code) {
        BuiltInSkillDefinition definition = skillsByCode.get(code);
        if (definition == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "内置 Skill 不存在：" + code);
        }
        return definition;
    }

    public String render(String agentCode, Map<String, Object> variables) {
        BuiltInAgentDefinition agent = findByCode(agentCode);
        Map<String, Object> safeVariables = variables == null ? Map.of() : variables;
        for (BuiltInAgentVariable variable : agent.variables()) {
            Object value = safeVariables.get(variable.name());
            if (variable.required() && (value == null || value.toString().isBlank())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Agent 变量缺失：" + variable.name());
            }
        }
        StringBuilder prompt = new StringBuilder(agent.promptTemplate());
        for (String skillCode : agent.skillCodes()) {
            prompt.append("\n\n").append(findSkillByCode(skillCode).content());
        }
        String rendered = prompt.toString();
        for (Map.Entry<String, Object> entry : safeVariables.entrySet()) {
            rendered = rendered.replace(
                "${" + entry.getKey() + "}",
                String.valueOf(entry.getValue())
            );
        }
        return rendered;
    }

    private void validate() {
        validateUnique(skills, BuiltInSkillDefinition::code, "Skill");
        validateUnique(agents, BuiltInAgentDefinition::code, "Agent");
        if (agentsByScene.size() != agents.size()) {
            throw new IllegalStateException("每个内置 Agent 必须绑定唯一业务场景。");
        }
        for (BuiltInAgentDefinition agent : agents) {
            if (agent.code().isBlank() || agent.name().isBlank() || agent.promptTemplate().isBlank()
                || agent.outputSchema().isBlank() || agent.capability() == null || agent.scene() == null) {
                throw new IllegalStateException("内置 Agent 定义不完整：" + agent.code());
            }
            for (BuiltInAgentVariable variable : agent.variables()) {
                if (variable.name().isBlank() || variable.type().isBlank()) {
                    throw new IllegalStateException("内置 Agent 输入变量定义不完整：" + agent.code());
                }
            }
            agent.skillCodes().forEach(this::findSkillByCode);
        }
    }

    private <T> Map<String, T> index(List<T> values, Function<T, String> keyFunction) {
        return values.stream().collect(Collectors.toUnmodifiableMap(keyFunction, Function.identity()));
    }

    private <T> void validateUnique(List<T> values, Function<T, String> keyFunction, String type) {
        Map<String, Long> duplicates = values.stream()
            .collect(Collectors.groupingBy(keyFunction, LinkedHashMap::new, Collectors.counting()));
        if (duplicates.values().stream().anyMatch(count -> count > 1)) {
            throw new IllegalStateException(type + " code 重复：" + duplicates);
        }
    }

    private BuiltInAgentDefinition agent(
        String code,
        String name,
        String description,
        AiBusinessScene scene,
        String promptTemplate,
        List<BuiltInAgentVariable> variables,
        String outputSchema,
        List<String> skillCodes
    ) {
        return new BuiltInAgentDefinition(code, name, description, scene, scene.capability(), promptTemplate, variables, outputSchema, skillCodes);
    }

    private BuiltInAgentVariable variable(String name, String label, String type) {
        return new BuiltInAgentVariable(name, label, type, true, null);
    }
}
