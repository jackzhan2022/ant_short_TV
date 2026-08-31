package com.antshorttv.script;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

@Service
public class ScriptEpisodeService {
    private final ScriptEpisodeMapper episodeMapper;
    private final JdbcTemplate jdbc;
    private final ScriptEpisodeReconciler reconciler;
    private final ScriptEpisodeSummaryRepository summaryRepository;

    public ScriptEpisodeService(
        ScriptEpisodeMapper episodeMapper,
        JdbcTemplate jdbc,
        ScriptEpisodeSummaryRepository summaryRepository
    ) {
        this.episodeMapper = episodeMapper;
        this.jdbc = jdbc;
        this.summaryRepository = summaryRepository;
        this.reconciler = new ScriptEpisodeReconciler();
    }

    @Transactional
    public List<ScriptEpisodeResponse> reconcileAndPersist(
        Long tenantId,
        Long projectId,
        Long scriptId,
        Long scriptVersionId,
        List<ScriptEpisodeResponse> parsedEpisodes
    ) {
        return reconcileAndPersist(tenantId, projectId, scriptId, scriptVersionId, null, parsedEpisodes);
    }

    @Transactional
    public List<ScriptEpisodeResponse> reconcileAndPersist(
        Long tenantId,
        Long projectId,
        Long scriptId,
        Long scriptVersionId,
        Long generatedByRunId,
        List<ScriptEpisodeResponse> parsedEpisodes
    ) {
        List<ScriptEpisodeEntity> current = activeEntities(tenantId, projectId, scriptId);
        List<ScriptEpisodeReconciler.ExistingEpisode> existing = current.stream()
            .map(item -> new ScriptEpisodeReconciler.ExistingEpisode(
                item.getId(), item.getStableKey(), item.getEpisodeNo(), item.getTitle(), item.getContent()))
            .toList();
        ScriptEpisodeReconciler.EpisodeReconciliation reconciliation = reconciler.reconcile(existing, parsedEpisodes);
        Map<String, ScriptEpisodeEntity> byStableKey = current.stream()
            .collect(Collectors.toMap(ScriptEpisodeEntity::getStableKey, Function.identity()));
        LocalDateTime now = LocalDateTime.now();

        for (String stableKey : reconciliation.retiredStableKeys()) {
            ScriptEpisodeEntity retired = byStableKey.get(stableKey);
            if (retired != null) {
                retired.setStatus("RETIRED");
                retired.setReconciliationStatus("RETIRED");
                retired.setRetiredAt(now);
                retired.setUpdatedAt(now);
                episodeMapper.updateById(retired);
                jdbc.update("""
                    update asset_visual_variant_episode
                       set binding_status = 'RETIRED', retired_at = ?, updated_at = ?
                     where tenant_id = ? and project_id = ? and episode_id = ? and retired_at is null
                    """, now, now, tenantId, projectId, retired.getId());
            }
        }

        return reconciliation.active().stream().map(item -> {
            ScriptEpisodeEntity entity = item.id() == null
                ? new ScriptEpisodeEntity()
                : byStableKey.get(item.stableKey());
            if (entity == null) {
                entity = new ScriptEpisodeEntity();
            }
            entity.setTenantId(tenantId);
            entity.setProjectId(projectId);
            entity.setScriptId(scriptId);
            entity.setScriptVersionId(scriptVersionId);
            entity.setStableKey(item.stableKey());
            entity.setEpisodeNo(item.episodeNo());
            entity.setTitle(item.title());
            entity.setContent(item.content());
            entity.setSummary(item.summary());
            entity.setContentFingerprint(item.contentFingerprint());
            entity.setHeadingKey(item.headingKey());
            entity.setReconciliationStatus(item.status());
            entity.setStatus("AMBIGUOUS".equals(item.status()) ? "NEEDS_REVIEW" : "ACTIVE");
            entity.setRetiredAt(null);
            entity.setGeneratedByRunId(generatedByRunId);
            entity.setUpdatedAt(now);
            if (entity.getId() == null) {
                entity.setCreatedAt(now);
                episodeMapper.insert(entity);
            } else {
                episodeMapper.updateById(entity);
            }
            return new ScriptEpisodeResponse(
                entity.getId(), entity.getEpisodeNo(), entity.getTitle(), entity.getContent(), entity.getSummary());
        }).toList();
    }

    public List<ScriptEpisodeResponse> currentEpisodes(Long tenantId, Long projectId, Long scriptId) {
        Map<Long, ScriptEpisodeSummaryDocument> summaries = summaryRepository.findCurrentByScript(tenantId, scriptId);
        return activeEntities(tenantId, projectId, scriptId).stream()
            .map(item -> new ScriptEpisodeResponse(
                item.getId(), item.getEpisodeNo(), item.getTitle(), item.getContent(), item.getSummary(),
                item.getContentFingerprint(), item.getGeneratedByRunId(), summaries.get(item.getId())))
            .toList();
    }

    private List<ScriptEpisodeEntity> activeEntities(Long tenantId, Long projectId, Long scriptId) {
        return episodeMapper.selectList(new QueryWrapper<ScriptEpisodeEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .eq("script_id", scriptId)
            .isNull("retired_at")
            .orderByAsc("episode_no", "id"));
    }
}
