package com.antshorttv.script;

import com.antshorttv.ai.AiBusinessScene;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.execution.AiExecutionResponse;
import com.antshorttv.execution.AiExecutionTaskEntity;
import com.antshorttv.execution.AiExecutionTaskMapper;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.security.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class StoryboardBatchService {
    private final ProjectPermissionGuard permissionGuard;
    private final ScriptMapper scriptMapper;
    private final ScriptAiOperationService operationService;
    private final StoryboardBatchMapper batchMapper;
    private final StoryboardBatchItemMapper itemMapper;
    private final AiExecutionTaskMapper executionMapper;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    StoryboardBatchService(
        ProjectPermissionGuard permissionGuard,
        ScriptMapper scriptMapper,
        ScriptAiOperationService operationService,
        StoryboardBatchMapper batchMapper,
        StoryboardBatchItemMapper itemMapper,
        AiExecutionTaskMapper executionMapper,
        JdbcTemplate jdbc,
        ObjectMapper json
    ) {
        this.permissionGuard = permissionGuard;
        this.scriptMapper = scriptMapper;
        this.operationService = operationService;
        this.batchMapper = batchMapper;
        this.itemMapper = itemMapper;
        this.executionMapper = executionMapper;
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    StoryboardBatchResponse create(
        Long tenantId,
        Long projectId,
        CreateStoryboardBatchRequest request,
        String requestedIdempotencyKey,
        String requestedTraceId
    ) {
        TenantContext context = permissionGuard.require(
            tenantId, projectId, "STORYBOARD:AI_BREAKDOWN");
        String idempotencyKey = valueOrUuid(requestedIdempotencyKey);
        StoryboardBatchEntity existing = batchMapper.selectByIdempotency(
            tenantId, projectId, idempotencyKey);
        if (existing != null) {
            return response(existing);
        }
        ScriptEntity script = scriptMapper.selectCurrentByProject(tenantId, projectId);
        if (script == null) {
            throw invalid("当前项目暂无可生成分镜的剧本。");
        }
        List<Long> requested = request.episodeIds();
        if (new HashSet<>(requested).size() != requested.size()) {
            throw invalid("批次剧集不能重复。");
        }
        List<EpisodeRef> episodes = jdbc.query("""
            select id, episode_no from script_episode
             where tenant_id = ? and project_id = ? and script_id = ?
               and status = 'ACTIVE' and retired_at is null
               and id in (%s)
             order by episode_no
            """.formatted(placeholders(requested.size())),
            (row, index) -> new EpisodeRef(row.getLong("id"), row.getInt("episode_no")),
            queryArguments(tenantId, projectId, script.getId(), requested));
        Set<Long> found = new HashSet<>(episodes.stream().map(EpisodeRef::id).toList());
        if (episodes.size() != requested.size() || !found.containsAll(requested)) {
            throw invalid("批次只能包含当前项目的有效剧集。");
        }

        LocalDateTime now = LocalDateTime.now();
        StoryboardBatchEntity batch = new StoryboardBatchEntity();
        batch.tenantId = tenantId;
        batch.projectId = projectId;
        batch.scriptId = script.getId();
        batch.name = "分镜批次-" + now;
        batch.idempotencyKey = idempotencyKey;
        batch.createdBy = context.userId();
        batch.createdAt = now;
        batchMapper.insert(batch);

        String traceId = valueOrUuid(requestedTraceId);
        for (EpisodeRef episode : episodes) {
            AiExecutionResponse execution = operationService.submit(
                context,
                projectId,
                AiBusinessScene.STORYBOARD_BREAKDOWN,
                "STORYBOARD_BREAKDOWN",
                script.getId(),
                script.getCurrentVersionId(),
                new StoryboardBreakdownRequest(episode.id()),
                idempotencyKey + ":episode:" + episode.id(),
                traceId
            );
            StoryboardBatchItemEntity item = new StoryboardBatchItemEntity();
            item.batchId = batch.id;
            item.tenantId = tenantId;
            item.projectId = projectId;
            item.episodeId = episode.id();
            item.episodeNo = episode.episodeNo();
            item.executionId = execution.id();
            item.createdAt = now;
            itemMapper.insert(item);
        }
        return response(batch);
    }

    StoryboardBatchResponse latest(Long tenantId, Long projectId) {
        permissionGuard.require(tenantId, projectId, "STORYBOARD:VIEW");
        StoryboardBatchEntity batch = batchMapper.selectLatest(tenantId, projectId);
        return batch == null ? null : response(batch);
    }

    StoryboardBatchResponse get(Long tenantId, Long projectId, Long batchId) {
        permissionGuard.require(tenantId, projectId, "STORYBOARD:VIEW");
        StoryboardBatchEntity batch = batchMapper.selectScoped(tenantId, projectId, batchId);
        if (batch == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分镜批次不存在。");
        }
        return response(batch);
    }

    private StoryboardBatchResponse response(StoryboardBatchEntity batch) {
        List<StoryboardBatchItemResponse> items = itemMapper
            .selectByBatch(batch.tenantId, batch.projectId, batch.id)
            .stream()
            .map(this::itemResponse)
            .toList();
        int pending = count(items, "PENDING");
        int running = count(items, "RUNNING");
        int succeeded = count(items, "SUCCESS");
        int warning = count(items, "SUCCESS_WITH_WARNING");
        int failed = count(items, "FAILED");
        String status = batchStatus(items.size(), pending, running, succeeded, warning, failed);
        return new StoryboardBatchResponse(
            batch.id,
            batch.projectId,
            batch.name,
            status,
            items.size(),
            pending,
            running,
            succeeded,
            warning,
            failed,
            items.stream().mapToInt(StoryboardBatchItemResponse::businessCallCount).sum(),
            items.stream().mapToInt(StoryboardBatchItemResponse::technicalRetryCount).sum(),
            items.stream().map(StoryboardBatchItemResponse::settledPoints)
                .reduce(BigDecimal.ZERO, BigDecimal::add),
            items,
            batch.createdAt
        );
    }

    private StoryboardBatchItemResponse itemResponse(StoryboardBatchItemEntity item) {
        AiExecutionTaskEntity execution = executionMapper.selectById(item.executionId);
        if (execution == null) {
            throw new IllegalStateException("Storyboard batch execution is missing: " + item.executionId);
        }
        int warningCount = "SUCCEEDED".equals(execution.status)
            ? warningCount(item, execution.id)
            : 0;
        String status = itemStatus(execution.status, warningCount);
        return new StoryboardBatchItemResponse(
            item.id,
            item.episodeId,
            item.episodeNo,
            item.executionId,
            status,
            warningCount,
            valueOrZero(execution.businessCallCount),
            valueOrZero(execution.technicalRetryCount),
            execution.settledPoints == null ? BigDecimal.ZERO : execution.settledPoints,
            execution.errorCode,
            execution.errorMessage
        );
    }

    private int warningCount(StoryboardBatchItemEntity item, Long executionId) {
        List<String> plans = jdbc.queryForList("""
            select storyboard.shot_plan_json
              from storyboard
             where storyboard.tenant_id = ? and storyboard.project_id = ?
               and storyboard.episode_id = ? and storyboard.deleted_at is null
               and storyboard.generated_by_run_id in (
                   select distinct step.run_id
                     from ai_workflow_agent_run_step step
                     join ai_call_log call_log on call_log.id = step.ai_call_log_id
                    where call_log.execution_id = ?
               )
            """, String.class, item.tenantId, item.projectId, item.episodeId, executionId);
        int warnings = 0;
        for (String plan : plans) {
            try {
                JsonNode parsed = json.readTree(plan);
                warnings += parsed.path("warnings").isArray() ? parsed.path("warnings").size() : 0;
                warnings += parsed.path("diagnostics").path("classificationWarnings").isArray()
                    ? parsed.path("diagnostics").path("classificationWarnings").size()
                    : 0;
            } catch (Exception ignored) {
                // A malformed historical plan is not a generated quality warning.
            }
        }
        return warnings;
    }

    static String itemStatus(String executionStatus, int warningCount) {
        return switch (executionStatus) {
            case "SUCCEEDED" -> warningCount > 0 ? "SUCCESS_WITH_WARNING" : "SUCCESS";
            case "FAILED", "CANCELED", "TIMED_OUT" -> "FAILED";
            case "RUNNING" -> "RUNNING";
            default -> "PENDING";
        };
    }

    static String batchStatus(
        int total, int pending, int running, int succeeded, int warning, int failed
    ) {
        if (running > 0) return "RUNNING";
        if (pending > 0) return "PENDING";
        if (total == 0) return "PENDING";
        if (failed == total) return "FAILED";
        if (failed > 0) return "COMPLETED_WITH_FAILURES";
        return warning > 0 ? "SUCCEEDED_WITH_WARNING" : "SUCCEEDED";
    }

    private static int count(List<StoryboardBatchItemResponse> items, String status) {
        return (int) items.stream().filter(item -> status.equals(item.status())).count();
    }

    private static String valueOrUuid(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value.trim();
    }

    private static int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    private static Object[] queryArguments(
        Long tenantId, Long projectId, Long scriptId, List<Long> episodeIds
    ) {
        List<Object> values = new ArrayList<>();
        values.add(tenantId);
        values.add(projectId);
        values.add(scriptId);
        values.addAll(episodeIds);
        return values.toArray();
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }

    private record EpisodeRef(Long id, Integer episodeNo) {
    }
}
