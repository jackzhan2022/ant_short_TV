package com.antshorttv.script;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class EpisodePromptContextService {
    private final JdbcTemplate jdbc;
    private final EpisodePromptContextFactory factory;
    private final EpisodePromptContextRepository repository;

    public EpisodePromptContextService(
        JdbcTemplate jdbc,
        EpisodePromptContextFactory factory,
        EpisodePromptContextRepository repository
    ) {
        this.jdbc = jdbc;
        this.factory = factory;
        this.repository = repository;
    }

    public Prepared prepare(ScriptAnalysisTaskEntity task, Long episodeId, Long modelId) {
        EpisodeSource episode = episode(
            task.getTenantId(), task.getProjectId(), task.getScriptId(), episodeId);
        String global = frozenGlobalUnderstanding(task.getId());
        return create(task.getTenantId(), task.getProjectId(), task.getScriptId(), episodeId,
            modelId, episode, global);
    }

    public Prepared prepareReextraction(ScriptAnalysisTaskEntity scope, Long episodeId, Long modelId) {
        if (modelId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "资产重提取缺少冻结文本模型。");
        }
        EpisodeSource episode = episode(scope.getTenantId(), scope.getProjectId(), scope.getScriptId(), episodeId);
        return create(scope.getTenantId(), scope.getProjectId(), scope.getScriptId(), episodeId,
            modelId, episode, null);
    }

    public Prepared prepareStoryboard(
        ScriptAiOperationEntity operation,
        Long episodeId,
        Long modelId
    ) {
        EpisodeSource episode = episode(
            operation.tenantId, operation.projectId, operation.scriptId, episodeId);
        var existing = repository.findLatest(
            operation.tenantId, operation.projectId, operation.scriptId, episodeId,
            modelId, episode.fingerprint());
        if (existing.isPresent()) {
            var snapshot = existing.get();
            return new Prepared(snapshot.id(), snapshot.commonPrefix(),
                "episode-context:" + operation.tenantId + ":" + modelId + ":" + snapshot.contextHash(),
                snapshot.contextHash());
        }
        return create(operation.tenantId, operation.projectId, operation.scriptId, episodeId,
            modelId, episode, null);
    }

    private EpisodeSource episode(Long tenantId, Long projectId, Long scriptId, Long episodeId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            select content, content_fingerprint
              from script_episode
             where tenant_id = ? and project_id = ? and script_id = ? and id = ?
               and status = 'ACTIVE' and retired_at is null
            """, tenantId, projectId, scriptId, episodeId);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "当前正式剧集不存在或已退役。");
        }
        String source = String.valueOf(rows.get(0).getOrDefault("content", ""));
        String fingerprint = String.valueOf(rows.get(0).getOrDefault(
            "content_fingerprint", EpisodePromptContextFactory.hash(source)));
        return new EpisodeSource(source, fingerprint);
    }

    private Prepared create(
        Long tenantId,
        Long projectId,
        Long scriptId,
        Long episodeId,
        Long modelId,
        EpisodeSource episode,
        String global
    ) {
        EpisodePromptContextFactory.Context context = factory.build(
            tenantId, projectId, scriptId, episodeId, modelId,
            episode.fingerprint(), global, episode.content());
        EpisodePromptContextRepository.Snapshot snapshot = repository.createOrLoad(
            new EpisodePromptContextRepository.Draft(
                tenantId, projectId, scriptId, episodeId, modelId,
                episode.fingerprint(), EpisodePromptContextFactory.hash(global == null ? "ABSENT" : global),
                context.contextHash(), EpisodePromptContextFactory.RULES_REVISION,
                EpisodePromptContextFactory.TOOL_PROTOCOL_REVISION, context.commonPrefix()));
        return new Prepared(snapshot.id(), snapshot.commonPrefix(), context.cacheKey(), context.contextHash());
    }

    private String frozenGlobalUnderstanding(Long taskId) {
        if (taskId == null) return null;
        List<String> values = jdbc.queryForList("""
            select result.normalized_json
              from script_analysis_stage stage
              join script_analysis_result result on result.stage_id = stage.id
             where stage.task_id = ? and stage.stage_code = 'GLOBAL_UNDERSTANDING'
               and stage.status = 'SUCCEEDED' and result.status = 'SUCCEEDED'
             order by result.id desc limit 1
            """, String.class, taskId);
        return values.isEmpty() ? null : values.get(0);
    }

    public record Prepared(Long snapshotId, String commonPrefix, String cacheKey, String contextHash) {}

    private record EpisodeSource(String content, String fingerprint) {}
}
