package com.antshorttv.script;

import com.antshorttv.ai.AiAgentDefinitionEntity;
import com.antshorttv.ai.AiAgentDefinitionMapper;
import com.antshorttv.ai.AiModelParameterProfileEntity;
import com.antshorttv.ai.AiModelParameterProfileMapper;
import com.antshorttv.ai.BuiltInAgentRegistry;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ScriptAnalysisConfigSnapshotService {
    private static final List<String> ANALYSIS_AGENT_CODES = List.of(
        "script-global-understanding",
        "script-episode-split",
        "script-episode-summary",
        "script-character-scene-recognition"
    );

    private final ScriptAnalysisConfigSnapshotMapper snapshotMapper;
    private final AiAgentDefinitionMapper agentMapper;
    private final AiModelParameterProfileMapper parameterMapper;
    private final BuiltInAgentRegistry registry;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ScriptAnalysisConfigSnapshotService(
        ScriptAnalysisConfigSnapshotMapper snapshotMapper,
        AiAgentDefinitionMapper agentMapper,
        AiModelParameterProfileMapper parameterMapper,
        BuiltInAgentRegistry registry,
        JdbcTemplate jdbc,
        ObjectMapper objectMapper
    ) {
        this.snapshotMapper = snapshotMapper;
        this.agentMapper = agentMapper;
        this.parameterMapper = parameterMapper;
        this.registry = registry;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void snapshot(ScriptAnalysisTaskEntity task, Long modelId) {
        if (find(task.getId()) != null) return;
        AiModelParameterProfileEntity parameters = publishedParameters(modelId);
        Map<String, StageSnapshot> stages = new LinkedHashMap<>();
        List<SkillVersion> skillVersions = new ArrayList<>();
        for (String code : ANALYSIS_AGENT_CODES) {
            StageSnapshot stage = captureStage(code);
            stages.put(code, stage);
            skillVersions.addAll(stage.skills().stream()
                .map(skill -> new SkillVersion(code, skill.code(), skill.versionNo())).toList());
        }

        StageSnapshot global = stages.get("script-global-understanding");
        ScriptAnalysisConfigSnapshotEntity entity = new ScriptAnalysisConfigSnapshotEntity();
        entity.setTaskId(task.getId());
        entity.setAgentCode(global.code());
        entity.setAgentVersionNo(global.versionNo());
        entity.setModelParameterProfileId(parameters == null ? null : parameters.getId());
        entity.setModelParameterVersionNo(parameters == null ? null : parameters.getVersionNo());
        entity.setSkillVersionsJson(json(skillVersions));
        entity.setSnapshotJson(json(new SnapshotPayload(modelId, stages)));
        entity.setCreatedAt(LocalDateTime.now());
        snapshotMapper.insert(entity);
    }

    public String renderPrompt(Long taskId, String agentCode, Map<String, Object> variables) {
        SnapshotPayload payload = payload(find(taskId));
        if (payload == null || payload.stages() == null) return null;
        StageSnapshot stage = payload.stages().get(agentCode);
        if (stage == null) return null;
        StringBuilder source = new StringBuilder(stage.promptTemplate());
        if (stage.outputSchema() != null && !stage.outputSchema().isBlank()) {
            source.append("\n\n输出 Schema（必须严格遵守；name 不得为空，未知值使用空字符串或空数组）：\n")
                .append(stage.outputSchema());
        }
        if (stage.skills() != null && !stage.skills().isEmpty()) {
            source.append("\n\n技能约束（按顺序执行）：");
            stage.skills().forEach(skill -> source.append("\n\n### ")
                .append(skill.code()).append("\n").append(skill.content()));
        }
        String rendered = source.toString();
        if (variables != null) {
            for (var entry : variables.entrySet()) {
                rendered = rendered.replace("${" + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
        }
        return rendered;
    }

    public Long modelIdFor(Long taskId) {
        SnapshotPayload payload = payload(find(taskId));
        return payload == null ? null : payload.modelId();
    }

    public AiModelParameterProfileEntity parametersFor(Long taskId) {
        ScriptAnalysisConfigSnapshotEntity snapshot = find(taskId);
        if (snapshot == null || snapshot.getModelParameterProfileId() == null) return null;
        return parameterMapper.selectOne(new LambdaQueryWrapper<AiModelParameterProfileEntity>()
            .eq(AiModelParameterProfileEntity::getId, snapshot.getModelParameterProfileId())
            .eq(AiModelParameterProfileEntity::getVersionNo, snapshot.getModelParameterVersionNo()).last("limit 1"));
    }

    private StageSnapshot captureStage(String code) {
        AiAgentDefinitionEntity agent = agentMapper.selectOne(new LambdaQueryWrapper<AiAgentDefinitionEntity>()
            .eq(AiAgentDefinitionEntity::getCode, code).eq(AiAgentDefinitionEntity::getPublished, true)
            .eq(AiAgentDefinitionEntity::getStatus, "ENABLED")
            .orderByDesc(AiAgentDefinitionEntity::getVersionNo).last("limit 1"));
        if (agent == null) {
            var fallback = registry.findByCode(code);
            List<SkillSnapshot> skills = fallback.skillCodes().stream().map(skillCode -> {
                var skill = registry.findSkillByCode(skillCode);
                return new SkillSnapshot(skill.code(), null, skill.content());
            }).toList();
            return new StageSnapshot(code, null, fallback.promptTemplate(), fallback.outputSchema(), skills);
        }
        List<SkillSnapshot> skills = jdbc.query("""
            select skill.code, skill.version_no, skill.content
              from ai_agent_skill binding
              join ai_skill_definition skill on skill.id = binding.skill_definition_id
             where binding.agent_definition_id = ? and skill.published = true and skill.status = 'ENABLED'
             order by binding.sort_order, skill.id
            """, (rs, rowNum) -> new SkillSnapshot(
                rs.getString("code"), rs.getInt("version_no"), rs.getString("content")), agent.getId());
        return new StageSnapshot(code, agent.getVersionNo(), agent.getPromptTemplate(), agent.getOutputSchema(),
            skills == null ? List.of() : skills);
    }

    private AiModelParameterProfileEntity publishedParameters(Long modelId) {
        if (modelId == null) return null;
        return parameterMapper.selectOne(new LambdaQueryWrapper<AiModelParameterProfileEntity>()
            .eq(AiModelParameterProfileEntity::getModelId, modelId)
            .eq(AiModelParameterProfileEntity::getPublished, true)
            .eq(AiModelParameterProfileEntity::getStatus, "ENABLED")
            .orderByDesc(AiModelParameterProfileEntity::getVersionNo).last("limit 1"));
    }

    private ScriptAnalysisConfigSnapshotEntity find(Long taskId) {
        return snapshotMapper.selectOne(new LambdaQueryWrapper<ScriptAnalysisConfigSnapshotEntity>()
            .eq(ScriptAnalysisConfigSnapshotEntity::getTaskId, taskId).last("limit 1"));
    }

    private SnapshotPayload payload(ScriptAnalysisConfigSnapshotEntity entity) {
        if (entity == null || entity.getSnapshotJson() == null || entity.getSnapshotJson().isBlank()) return null;
        try {
            return objectMapper.readValue(entity.getSnapshotJson(), SnapshotPayload.class);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法保存剧本分析配置快照。", exception);
        }
    }

    private record SnapshotPayload(Long modelId, Map<String, StageSnapshot> stages) {}
    private record StageSnapshot(String code, Integer versionNo, String promptTemplate,
                                 String outputSchema, List<SkillSnapshot> skills) {}
    private record SkillSnapshot(String code, Integer versionNo, String content) {}
    private record SkillVersion(String agentCode, String skillCode, Integer versionNo) {}
}
