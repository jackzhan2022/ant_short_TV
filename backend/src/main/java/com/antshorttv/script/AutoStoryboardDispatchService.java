package com.antshorttv.script;

import com.antshorttv.ai.AiBusinessScene;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.execution.AiExecutionResponse;
import com.antshorttv.rbac.RbacPermissionService;
import com.antshorttv.security.TenantContext;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AutoStoryboardDispatchService {
    private final AutoStoryboardEventRepository events;
    private final ScriptAiOperationService operations;
    private final RbacPermissionService permissions;
    private final JdbcTemplate jdbc;
    private final boolean enabled;
    private final int staleDispatchSeconds;

    public AutoStoryboardDispatchService(
        AutoStoryboardEventRepository events,
        ScriptAiOperationService operations,
        RbacPermissionService permissions,
        JdbcTemplate jdbc,
        @Value("${ai.workflow-agent.auto-storyboard-enabled:false}") boolean enabled,
        @Value("${ai.workflow-agent.auto-storyboard-stale-dispatch-seconds:120}") int staleDispatchSeconds
    ) {
        this.events = events;
        this.operations = operations;
        this.permissions = permissions;
        this.jdbc = jdbc;
        this.enabled = enabled;
        this.staleDispatchSeconds = staleDispatchSeconds;
    }

    @Scheduled(fixedDelayString = "${ai.workflow-agent.auto-storyboard-dispatch-delay-ms:3000}")
    public void dispatchOne() {
        if (!enabled) return;
        events.recoverStaleDispatching(staleDispatchSeconds);
        events.claimNext().ifPresent(this::dispatch);
    }

    private void dispatch(AutoStoryboardEventRepository.Event event) {
        try {
            List<Map<String, Object>> episodes = jdbc.queryForList("""
                select content_fingerprint from script_episode
                 where id=? and tenant_id=? and project_id=? and script_id=?
                   and stable_key=? and status='ACTIVE' and retired_at is null
                """, event.episodeId(), event.tenantId(), event.projectId(), event.scriptId(),
                event.episodeKey());
            if (episodes.isEmpty() || !event.sourceFingerprint().equals(
                String.valueOf(episodes.get(0).get("content_fingerprint")))) {
                events.markOutcome(event.id(), "STALE", "EPISODE_CONTENT_CHANGED",
                    "剧集正文已变更，自动分镜事件已失效。");
                return;
            }
            Integer existing = jdbc.queryForObject("""
                select count(*) from storyboard
                 where tenant_id=? and project_id=? and episode_id=? and deleted_at is null
                """, Integer.class, event.tenantId(), event.projectId(), event.episodeId());
            if (existing != null && existing > 0) {
                events.markOutcome(event.id(), "PROTECTED", "STORYBOARD_EXISTS",
                    "已存在分镜，自动任务未覆盖现有成果。");
                return;
            }
            TenantContext context = memberContext(event);
            if (!permissions.hasPermission(context, "STORYBOARD:AI_BREAKDOWN", event.projectId())) {
                events.markOutcome(event.id(), "BLOCKED_AUTH", ErrorCode.FORBIDDEN.name(),
                    "发起人无自动生成分镜权限。");
                return;
            }
            Long scriptVersionId = jdbc.queryForObject(
                "select current_version_id from script where id=?", Long.class, event.scriptId());
            String idempotencyKey = "auto-storyboard:" + event.policyVersion() + ":"
                + event.tenantId() + ":" + event.scriptId() + ":" + event.episodeId()
                + ":" + event.sourceFingerprint();
            AiExecutionResponse execution = operations.submit(
                context, event.projectId(), AiBusinessScene.STORYBOARD_BREAKDOWN,
                "STORYBOARD_BREAKDOWN", event.scriptId(), scriptVersionId,
                new StoryboardBreakdownRequest(event.episodeId()), idempotencyKey,
                "auto-storyboard-event-" + event.id());
            events.markDispatched(event.id(), execution.id());
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.TEAM_POINTS_INSUFFICIENT) {
                events.markOutcome(event.id(), "BLOCKED_FUNDS", exception.getErrorCode().name(),
                    exception.getMessage());
            } else if (exception.getErrorCode() == ErrorCode.FORBIDDEN
                || exception.getErrorCode() == ErrorCode.PROJECT_ACCESS_DENIED) {
                events.markOutcome(event.id(), "BLOCKED_AUTH", exception.getErrorCode().name(),
                    exception.getMessage());
            } else {
                events.markFailed(event.id(), exception.getErrorCode().name(), exception.getMessage(),
                    event.attemptNo() < 3);
            }
        } catch (RuntimeException exception) {
            events.markFailed(event.id(), "AUTO_STORYBOARD_DISPATCH_FAILED",
                exception.getMessage(), event.attemptNo() < 3);
        }
    }

    private TenantContext memberContext(AutoStoryboardEventRepository.Event event) {
        List<Map<String, Object>> members = jdbc.queryForList("""
            select id, member_type from tenant_member
             where tenant_id=? and user_id=? and status='ACTIVE' order by id limit 1
            """, event.tenantId(), event.createdBy());
        if (members.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "发起人已不是当前团队成员。");
        }
        return new TenantContext(event.createdBy(), event.tenantId(),
            ((Number) members.get(0).get("id")).longValue(),
            String.valueOf(members.get(0).get("member_type")));
    }
}
