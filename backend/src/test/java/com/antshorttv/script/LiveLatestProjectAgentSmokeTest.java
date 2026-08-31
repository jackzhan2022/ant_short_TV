package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;

import com.antshorttv.authsession.AuthenticatedUser;
import com.antshorttv.workflowagent.WorkflowAgentProperties;
import com.antshorttv.workflowagent.run.WorkflowAgentRunResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest(properties = {
    "ai.testing.mock-provider-enabled=false",
    "ai.execution.dispatcher.enabled=false",
    "ai.workflow-agent.global-understanding-enabled=true",
    "ai.workflow-agent.episode-splitting-enabled=true",
    "ai.workflow-agent.episode-summary-enabled=true",
    "ai.workflow-agent.asset-recognition-enabled=true",
    "ai.workflow-agent.run-timeout-seconds=600"
})
@EnabledIfEnvironmentVariable(named = "RUN_LIVE_SHORT_DRAMA_SMOKE", matches = "true")
class LiveLatestProjectAgentSmokeTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ScriptWorkflowService scriptWorkflowService;
    @Autowired
    private WorkflowAgentProperties workflowAgentProperties;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sequentiallyRunsRemainingAgentsAgainstLatestProject() {
        assertThat(workflowAgentProperties.getRunTimeoutSeconds()).isEqualTo(600);
        Map<String, Object> target = jdbcTemplate.queryForMap("""
            select p.id project_id, p.tenant_id, p.owner_id, u.mobile, s.id script_id
              from project p
              join app_user u on u.id = p.owner_id
              join script s on s.project_id = p.id and s.deleted_at is null
             where p.deleted_at is null
               and s.content is not null
               and length(trim(s.content)) > 0
             order by coalesce(p.updated_at, p.created_at) desc, p.id desc
             limit 1
            """);

        long projectId = number(target, "project_id");
        long tenantId = number(target, "tenant_id");
        long ownerId = number(target, "owner_id");
        long scriptId = number(target, "script_id");
        String mobile = String.valueOf(target.get("mobile"));
        authenticate(ownerId, mobile);

        String source = jdbcTemplate.queryForObject(
            "select content from script where tenant_id = ? and project_id = ? and id = ?",
            String.class, tenantId, projectId, scriptId);
        int originalSafeContext = workflowAgentProperties.getSplitSafeContextTokens();
        WorkflowAgentRunResult fullSplit;
        WorkflowAgentRunResult chunkSplit;
        try {
            workflowAgentProperties.setSplitSafeContextTokens(Integer.MAX_VALUE);
            fullSplit = scriptWorkflowService.regenerateEpisodeSplitting(tenantId, projectId);
            assertRunSucceeded(fullSplit, "short-drama-episode-splitting");
            assertSingleFormalSave(fullSplit.runId());
            if (snapshotCount(fullSplit.runId()) > 0) {
                assertSuccessfulFallbackSnapshot(fullSplit.runId(), List.of(
                    "CONTEXT_ERROR", "OUTPUT_TRUNCATED", "EMPTY_RESPONSE", "SAVE_NOT_CALLED"));
            }
            assertExactCoverage(tenantId, projectId, scriptId, source);
            List<Long> fullEpisodeIds = activeEpisodeIds(tenantId, projectId, scriptId);

            workflowAgentProperties.setSplitSafeContextTokens(1);
            chunkSplit = scriptWorkflowService.regenerateEpisodeSplitting(tenantId, projectId);
            assertRunSucceeded(chunkSplit, "short-drama-episode-splitting");
            assertSingleFormalSave(chunkSplit.runId());
            assertSuccessfulFallbackSnapshot(chunkSplit.runId(), List.of("CONTEXT_PREFLIGHT"));
            assertExactCoverage(tenantId, projectId, scriptId, source);
            assertThat(activeEpisodeIds(tenantId, projectId, scriptId)).containsExactlyElementsOf(fullEpisodeIds);
            assertProviderUsageCaptured(fullSplit.runId());
            assertProviderUsageCaptured(chunkSplit.runId());
        } finally {
            workflowAgentProperties.setSplitSafeContextTokens(originalSafeContext);
        }

        Map<String, Object> episode = jdbcTemplate.queryForMap("""
            select id, episode_no
              from script_episode
             where tenant_id = ? and project_id = ? and script_id = ?
               and status = 'ACTIVE' and retired_at is null
             order by episode_no, id
             limit 1
            """, tenantId, projectId, scriptId);
        long episodeId = number(episode, "id");

        WorkflowAgentRunResult summary = scriptWorkflowService.regenerateEpisodeSummary(
            tenantId, projectId, episodeId, true);
        assertRunSucceeded(summary, "short-drama-episode-summary");
        assertThat(jdbcTemplate.queryForObject("""
            select count(*) from script_episode_summary
             where tenant_id = ? and project_id = ? and script_id = ? and episode_id = ?
               and generated_by_run_id = ?
            """, Long.class, tenantId, projectId, scriptId, episodeId, summary.runId())).isEqualTo(1L);

        WorkflowAgentRunResult assets = scriptWorkflowService.regenerateEpisodeAssets(
            tenantId, projectId, episodeId);
        assertRunSucceeded(assets, "short-drama-asset-recognition");
        assertThat(jdbcTemplate.queryForObject("""
            select count(*) from script_episode_asset_analysis
             where tenant_id = ? and project_id = ? and script_id = ? and episode_id = ?
               and generated_by_run_id = ?
            """, Long.class, tenantId, projectId, scriptId, episodeId, assets.runId())).isEqualTo(1L);

        System.out.printf(
            "LIVE_SMOKE_OK projectId=%d scriptId=%d episodeId=%d fullSplitRunId=%d chunkSplitRunId=%d summaryRunId=%d assetRunId=%d%n",
            projectId, scriptId, episodeId, fullSplit.runId(), chunkSplit.runId(),
            summary.runId(), assets.runId());
    }

    private void authenticate(long userId, String mobile) {
        AuthenticatedUser user = new AuthenticatedUser(
            userId, mobile, "live-smoke", LocalDateTime.now().plusHours(1));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, java.util.List.of()));
    }

    private void assertRunSucceeded(WorkflowAgentRunResult result, String agentCode) {
        assertThat(result).isNotNull();
        assertThat(result.runId()).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
            "select status from ai_workflow_agent_run where id = ? and agent_code = ?",
            String.class, result.runId(), agentCode)).isEqualTo("SUCCESS");
    }

    private void assertSingleFormalSave(long runId) {
        assertThat(jdbcTemplate.queryForObject("""
            select count(*) from ai_workflow_agent_run_step
             where run_id = ? and step_type = 'TOOL' and tool_code = 'save_episode_splitting'
               and status = 'SUCCESS'
            """, Integer.class, runId)).isEqualTo(1);
    }

    private int snapshotCount(long runId) {
        return jdbcTemplate.queryForObject(
            "select count(*) from script_split_snapshot where parent_run_id = ?",
            Integer.class, runId);
    }

    private void assertSuccessfulFallbackSnapshot(long runId, List<String> reasons) {
        Map<String, Object> snapshot = jdbcTemplate.queryForMap("""
            select mode, fallback_reason, status, total_chunks, completed_chunks, failed_chunks
              from script_split_snapshot where parent_run_id = ?
             order by id desc limit 1
            """, runId);
        assertThat(snapshot.get("mode")).isEqualTo("CHUNK_FALLBACK");
        assertThat(String.valueOf(snapshot.get("fallback_reason"))).isIn(reasons);
        assertThat(snapshot.get("status")).isEqualTo("SUCCEEDED");
        assertThat(number(snapshot, "total_chunks")).isPositive();
        assertThat(number(snapshot, "completed_chunks")).isEqualTo(number(snapshot, "total_chunks"));
        assertThat(number(snapshot, "failed_chunks")).isZero();
    }

    private void assertExactCoverage(long tenantId, long projectId, long scriptId, String source) {
        List<String> parts = jdbcTemplate.queryForList("""
            select content from script_episode
             where tenant_id = ? and project_id = ? and script_id = ?
               and status = 'ACTIVE' and retired_at is null
             order by episode_no, id
            """, String.class, tenantId, projectId, scriptId);
        assertThat(String.join("", parts)).isEqualTo(source);
    }

    private List<Long> activeEpisodeIds(long tenantId, long projectId, long scriptId) {
        return jdbcTemplate.queryForList("""
            select id from script_episode
             where tenant_id = ? and project_id = ? and script_id = ?
               and status = 'ACTIVE' and retired_at is null
             order by episode_no, id
            """, Long.class, tenantId, projectId, scriptId);
    }

    private void assertProviderUsageCaptured(long runId) {
        assertThat(jdbcTemplate.queryForObject("""
            select coalesce(sum(coalesce(call_log.total_tokens, 0)), 0)
              from ai_workflow_agent_run_step step
              join ai_call_log call_log on call_log.id = step.ai_call_log_id
             where step.run_id = ?
            """, Long.class, runId)).isPositive();
    }

    private long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }
}
