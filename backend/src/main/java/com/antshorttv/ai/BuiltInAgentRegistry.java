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
                "script-global-understanding",
                "剧情全局理解",
                "理解剧本主线、人物关系、核心冲突和整体节奏。",
                AiBusinessScene.SCRIPT_GLOBAL_UNDERSTANDING,
                """
                    你是中文短剧结构分析助手。请基于剧本输出严格 JSON：
                    {"logline":"","themes":[],"characters":[],"relationships":[],"coreConflict":"","turningPoints":[],"endingHook":""}
                    剧本内容：
                    ${scriptContent}
                    """,
                List.of(variable("scriptContent", "剧本内容", "TEXT")),
                "{\"logline\":\"\",\"themes\":[],\"characters\":[],\"relationships\":[],\"coreConflict\":\"\",\"turningPoints\":[],\"endingHook\":\"\"}",
                List.of("strict-json-output", "no-invention", "short-drama-structure")
            ),
            agent(
                "script-episode-split",
                "剧集智能拆分",
                "根据剧情节点、冲突和悬念把无明确集标题的正文拆成剧集。",
                AiBusinessScene.SCRIPT_EPISODE_SPLIT,
                """
                    你是短剧分集助手。请根据剧情理解判断分集边界，只返回严格 JSON：
                    {"episodes":[{"title":"","startMarker":"","endMarker":""}]}
                    重要规则：
                    1. 只返回每段标题、原文中的 startMarker 和 endMarker，不要返回 content。
                    2. startMarker 和 endMarker 必须是原剧本中逐字出现的连续短句，后端会据此截取正文。
                    3. 按剧情顺序返回分段；后端会自动编号，标题统一使用“第N集”。
                    3. “3-2 夜 外 深海”等是第3集内部场次，不是第4集。
                    4. 必须覆盖原剧本全部正文；无法判断边界时返回空 episodes，后端会将全文作为第1集。
                    剧情理解：
                    ${globalUnderstanding}
                    原剧本：
                    ${scriptContent}
                    """,
                List.of(
                    variable("globalUnderstanding", "剧情全局理解", "JSON"),
                    variable("scriptContent", "剧本内容", "TEXT")
                ),
                "{\"episodes\":[{\"title\":\"\",\"startMarker\":\"\",\"endMarker\":\"\"}]}",
                List.of("strict-json-output", "no-invention", "short-drama-structure")
            ),
            agent(
                "script-episode-summary",
                "剧集概要提炼",
                "为每一集提炼概要、看点和结尾悬念。",
                AiBusinessScene.SCRIPT_EPISODE_SUMMARY,
                """
                    你是短剧概要提炼助手。请为输入的每一集返回严格 JSON：
                    {"episodes":[{"episodeNo":1,"summary":"","highlights":[],"endingHook":""}]}
                    分集内容：
                    ${episodes}
                    """,
                List.of(variable("episodes", "分集内容", "JSON")),
                "{\"episodes\":[{\"episodeNo\":1,\"summary\":\"\",\"highlights\":[],\"endingHook\":\"\"}]}",
                List.of("strict-json-output", "no-invention")
            ),
            agent(
                "script-character-scene-recognition",
                "角色场景识别",
                "从剧本中识别角色、场景和关键道具。",
                AiBusinessScene.SCRIPT_CHARACTER_SCENE_RECOGNITION,
                """
                    你是短剧资产识别助手。请仅基于剧本返回严格 JSON：
                    {"characters":[],"scenes":[],"props":[]}
                    只能返回以上三个顶层字段，禁止返回 locations、costumes、creatures、vehicles、visual_effects、organizations 或任何其他字段；场景信息必须放在 scenes 数组中。
                    剧本内容：
                    ${scriptContent}
                    """,
                List.of(variable("scriptContent", "剧本内容", "TEXT")),
                "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"characters\",\"scenes\",\"props\"],\"properties\":{\"characters\":{\"type\":\"array\",\"maxItems\":500,\"items\":{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"name\"],\"properties\":{\"name\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":100},\"aliases\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"maxLength\":100}},\"roleType\":{\"type\":\"string\",\"enum\":[\"LEAD\",\"SUPPORTING\",\"MINOR\",\"OTHER\"],\"default\":\"SUPPORTING\"},\"gender\":{\"type\":\"string\",\"maxLength\":32},\"ageRange\":{\"type\":\"string\",\"maxLength\":32},\"identity\":{\"type\":\"string\",\"maxLength\":200},\"personality\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"maxLength\":100}},\"appearance\":{\"type\":\"string\",\"maxLength\":500},\"prompt\":{\"type\":\"string\",\"maxLength\":4000}}}},\"scenes\":{\"type\":\"array\",\"maxItems\":500,\"items\":{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"name\"],\"properties\":{\"name\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":100},\"aliases\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"maxLength\":100}},\"sceneType\":{\"type\":\"string\",\"enum\":[\"INTERIOR\",\"EXTERIOR\",\"MIXED\",\"OTHER\"],\"default\":\"INTERIOR\"},\"atmosphere\":{\"type\":\"string\",\"maxLength\":100},\"description\":{\"type\":\"string\",\"maxLength\":4000},\"visualStyle\":{\"type\":\"string\",\"maxLength\":300},\"prompt\":{\"type\":\"string\",\"maxLength\":4000}}}},\"props\":{\"type\":\"array\",\"maxItems\":500,\"items\":{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"name\"],\"properties\":{\"name\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":100},\"aliases\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"maxLength\":100}},\"propType\":{\"type\":\"string\",\"enum\":[\"KEY_PROP\",\"DAILY\",\"WEAPON\",\"DOCUMENT\",\"OTHER\"],\"default\":\"KEY_PROP\"},\"appearance\":{\"type\":\"string\",\"maxLength\":500},\"plotFunction\":{\"type\":\"string\",\"maxLength\":500},\"relatedCharacter\":{\"type\":\"string\",\"maxLength\":200},\"prompt\":{\"type\":\"string\",\"maxLength\":4000}}}}}}",
                List.of("strict-json-output", "no-invention", "stable-entity-naming")
            ),
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
                    角色与任务：你是一位资深的影视编剧与多模态视频分析专家。请逐帧解析输入的第 ${episodeNo} 集视频，综合视觉画面、人物表情、声音对白、音效和字幕，精准反向还原为专业短剧剧本。

                    script 字段必须是以下固定 Markdown 拍摄剧本格式：
                    # 第${episodeNo}集：标题

                    ## ${episodeNo}-1 夜 内 地点

                    出场人物：人物A、人物B

                    动作、环境、光影、氛围、走位、微表情和关键音效使用独立段落，按视频发生顺序客观描述。

                    人物A
                    （情绪或动作，可省略）
                    台词正文，不加引号，不另写字幕行。

                    画外音和内心独白分别使用全角标记（OS）和（VO）。每个场景都必须有场景标头、出场人物和正文；场次从 ${episodeNo}-1 开始连续编号，不得跳号或重复。最后一行必须是“——本集完”。悬念通过最后的剧情动作自然呈现，不得另写结尾钩子标签，也不得补造视频中无法确认的悬念。

                    解析要求：不要遗漏雷声、心跳声、脚步声等环境音和道具特写；准确标注对白语气；不改变事件顺序，不补充视频无法确认的事实。

                    只输出一个完整、合法的 JSON 对象，格式必须是 {"script":"完整剧本文本"}。JSON 外层不要使用 Markdown 代码块，不要解释或省略号；script 必须是非空 Markdown 字符串，JSON 绝不能中途截断。
                    """,
                List.of(variable("episodeNo", "集数", "NUMBER")),
                "{\"script\":\"\"}",
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
                    你是专业的中文短剧剧本审核 Agent。
                    本轮审核模式：${reviewMode}
                    本轮审核维度：${selectedDimensions}
                    本轮审核范围：${reviewScope}
                    上一轮问题摘要：${previousIssues}
                    全局审核索引：${globalIndex}

                    请基于剧本内容执行本轮审核。优先检查用户选中的维度；如果发现选中范围外的 P0/P1 剧情硬伤，允许作为“兜底问题”顺带提醒。
                    快速审核以当前范围内的明显局部问题为主，不声称覆盖全部跨集问题；深度审核必须结合全局索引检查跨集人物、时间线、道具、伏笔和因果关系。
                    不要为了找问题而强行判错。没有证据时使用 uncertain，不要编造剧本中不存在的事实。
                    每个问题必须给出具体位置、原文片段、问题原因、证据和可执行修改建议。

                    剧本标题：${scriptTitle}
                    剧本内容：
                    <<<
                    ${scriptContent}
                    >>>

                    只返回严格 JSON：
                    {
                      "overallScore": 0,
                      "overallConclusion": "PASS",
                      "summary": "",
                      "issues": [
                        {
                          "issueNo": "R1-01",
                          "dimension": "台词合理性",
                          "severity": "P1",
                          "title": "",
                          "position": {"episode": 0, "scene": "", "shot": 0, "line": 0, "anchor": ""},
                          "excerpt": "",
                          "problem": "",
                          "evidence": [],
                          "suggestion": "",
                          "status": "new",
                          "relatedIssueNo": null,
                          "hits": [
                            {"episode": 0, "scene": "", "shot": 0, "line": 0, "anchor": "", "excerpt": "", "entity": ""}
                          ]
                        }
                      ]
                    }
                """,
                List.of(
                    variable("scriptTitle", "剧本标题", "TEXT"),
                    variable("scriptContent", "剧本内容", "TEXT"),
                    variable("reviewMode", "审核模式", "TEXT"),
                    variable("selectedDimensions", "审核维度", "JSON"),
                    variable("reviewScope", "审核范围", "JSON"),
                    variable("previousIssues", "上一轮问题", "JSON"),
                    variable("globalIndex", "全局审核索引", "JSON")
                ),
                "{\"overallScore\":0,\"overallConclusion\":\"PASS\",\"summary\":\"\",\"issues\":[]}",
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
        StringBuilder prompt = new StringBuilder(agent.promptTemplate())
            .append("\n\n输出 Schema（必须严格遵守；name 不得为空，未知值使用空字符串或空数组）：\n")
            .append(agent.outputSchema())
            .append("\n\n技能约束（按顺序执行）：");
        for (String skillCode : agent.skillCodes()) {
            prompt.append("\n\n### ").append(skillCode).append("\n")
                .append(findSkillByCode(skillCode).content());
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
