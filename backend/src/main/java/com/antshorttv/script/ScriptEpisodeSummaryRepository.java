package com.antshorttv.script;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Repository
public class ScriptEpisodeSummaryRepository {
    private final ScriptEpisodeSummaryMapper mapper;
    private final ObjectMapper json;

    public ScriptEpisodeSummaryRepository(ScriptEpisodeSummaryMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    public Optional<ScriptEpisodeSummaryDocument> findCurrent(Long tenantId, Long scriptId, Long episodeId) {
        return Optional.ofNullable(mapper.selectOne(new QueryWrapper<ScriptEpisodeSummaryEntity>()
            .eq("tenant_id", tenantId)
            .eq("script_id", scriptId)
            .eq("episode_id", episodeId)))
            .map(this::toDocument);
    }

    public Map<Long, ScriptEpisodeSummaryDocument> findCurrentByScript(Long tenantId, Long scriptId) {
        return mapper.selectList(new QueryWrapper<ScriptEpisodeSummaryEntity>()
                .eq("tenant_id", tenantId)
                .eq("script_id", scriptId))
            .stream()
            .map(this::toDocument)
            .collect(Collectors.toMap(ScriptEpisodeSummaryDocument::episodeId, Function.identity()));
    }

    @Transactional
    public long upsert(ScriptEpisodeSummaryDocument document) {
        ScriptEpisodeSummaryEntity entity = mapper.selectOne(new QueryWrapper<ScriptEpisodeSummaryEntity>()
            .eq("tenant_id", document.tenantId())
            .eq("episode_id", document.episodeId()));
        LocalDateTime now = LocalDateTime.now();
        if (entity == null) {
            entity = new ScriptEpisodeSummaryEntity();
            entity.setTenantId(document.tenantId());
            entity.setProjectId(document.projectId());
            entity.setScriptId(document.scriptId());
            entity.setEpisodeId(document.episodeId());
            entity.setCreatedBy(document.createdBy());
            entity.setCreatedAt(now);
        }
        entity.setSchemaVersion(document.schemaVersion());
        entity.setContentJson(document.content().toString());
        entity.setSource(document.source());
        entity.setGeneratedByRunId(document.generatedByRunId());
        entity.setUpdatedBy(document.updatedBy());
        entity.setUpdatedAt(now);
        if (entity.getId() == null) {
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
        return entity.getId();
    }

    private ScriptEpisodeSummaryDocument toDocument(ScriptEpisodeSummaryEntity entity) {
        return new ScriptEpisodeSummaryDocument(
            entity.getId(), entity.getTenantId(), entity.getProjectId(), entity.getScriptId(), entity.getEpisodeId(),
            entity.getSchemaVersion(), read(entity.getContentJson()), entity.getSource(), entity.getGeneratedByRunId(),
            entity.getCreatedBy(), entity.getUpdatedBy(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private JsonNode read(String value) {
        try {
            return json.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("剧集概要数据损坏。", exception);
        }
    }
}
