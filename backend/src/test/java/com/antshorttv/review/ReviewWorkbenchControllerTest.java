package com.antshorttv.review;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.antshorttv.execution.AiExecutionWorker;
import com.antshorttv.points.TeamPointService;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.mockito.ArgumentCaptor;

@SpringBootTest
@AutoConfigureMockMvc
class ReviewWorkbenchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReviewWorkbenchService reviewWorkbenchService;

    @Autowired
    private AiExecutionWorker aiExecutionWorker;

    @Autowired
    private com.antshorttv.workflowagent.agent.ScriptReviewAgentBootstrap reviewAgentBootstrap;

    @MockBean
    private com.antshorttv.workflowagent.run.WorkflowAgentRunner workflowAgentRunner;

    @MockBean
    private TeamPointService teamPointService;

    @Test
    void returnsRenderCriticalSummariesBeforeBatchReviewMetrics() throws Exception {
        String token = registerUser("13800017111", "Review Summary");
        Long tenantId = createTenant(token, "剧本审核摘要团队");

        MvcResult firstProject = mockMvc.perform(multipart("/api/script-review/projects")
                .file(new MockMultipartFile("content", "", MediaType.TEXT_PLAIN_VALUE, "第一本剧本".getBytes()))
                .param("name", "first-summary")
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andReturn();
        MvcResult secondProject = mockMvc.perform(multipart("/api/script-review/projects")
                .file(new MockMultipartFile("content", "", MediaType.TEXT_PLAIN_VALUE, "第二本剧本".getBytes()))
                .param("name", "second-summary")
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andReturn();
        Long firstProjectId = readLong(firstProject, "$.data.project.id");
        Long secondProjectId = readLong(secondProject, "$.data.project.id");

        mockMvc.perform(get("/api/script-review/projects/summaries")
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(2)))
            .andExpect(jsonPath("$.data[0].id", is(secondProjectId.intValue())))
            .andExpect(jsonPath("$.data[1].id", is(firstProjectId.intValue())))
            .andExpect(jsonPath("$.data[0].sourceType").exists())
            .andExpect(jsonPath("$.data[0].originalContent").doesNotExist())
            .andExpect(jsonPath("$.data[0].versionCount").doesNotExist())
            .andExpect(jsonPath("$.data[0].reviewState").doesNotExist());

        mockMvc.perform(get("/api/script-review/projects/metrics")
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(2)))
            .andExpect(jsonPath("$.data[0].projectId").exists())
            .andExpect(jsonPath("$.data[0].versionCount", is(1)))
            .andExpect(jsonPath("$.data[0].name").doesNotExist())
            .andExpect(jsonPath("$.data[0].reviewState", is("NOT_REVIEWED")));

        mockMvc.perform(get("/api/script-review/projects")
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(2)))
            .andExpect(jsonPath("$.data[0].versionCount", is(1)));
    }

    @Test
    void batchesMetricsForUnreviewedRunningAndCompletedProjects() throws Exception {
        String token = registerUser("13800017112", "Review Metrics");
        Long tenantId = createTenant(token, "剧本审核指标团队");
        Long actionRequiredProjectId = importReviewProject(token, tenantId, "待处理");
        Long readyForReviewProjectId = importReviewProject(token, tenantId, "待复审");
        Long notReviewedProjectId = importReviewProject(token, tenantId, "未审核");
        Long userId = jdbcTemplate.queryForObject(
            "select created_by from review_project where id = ?", Long.class, actionRequiredProjectId);
        Long actionVersionId = jdbcTemplate.queryForObject(
            "select current_version_id from review_project where id = ?", Long.class, actionRequiredProjectId);
        Long readyVersionId = jdbcTemplate.queryForObject(
            "select current_version_id from review_project where id = ?", Long.class, readyForReviewProjectId);

        Long actionTaskId = insertCompletedReviewTask(tenantId, actionRequiredProjectId, actionVersionId, userId, "metric-action");
        Long readyTaskId = insertCompletedReviewTask(tenantId, readyForReviewProjectId, readyVersionId, userId, "metric-ready");
        jdbcTemplate.update("update review_task set status='RUNNING', report_markdown=null where id=?", actionTaskId);

        mockMvc.perform(get("/api/script-review/projects/metrics")
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(3)))
            .andExpect(jsonPath("$.data[?(@.projectId == %d)].versionCount".formatted(actionRequiredProjectId), hasItem(1)))
            .andExpect(jsonPath("$.data[?(@.projectId == %d)].reviewState".formatted(actionRequiredProjectId), hasItem("RUNNING")))
            .andExpect(jsonPath("$.data[?(@.projectId == %d)].reviewState".formatted(readyForReviewProjectId), hasItem("COMPLETED")))
            .andExpect(jsonPath("$.data[?(@.projectId == %d)].reviewState".formatted(notReviewedProjectId), hasItem("NOT_REVIEWED")));
    }

    @Test
    void scopesProgressiveReadsToCreatorTenantViewerAndActiveBoundProjectMember() throws Exception {
        String ownerToken = registerUser("13800017113", "Review Access Owner");
        Long tenantId = createTenant(ownerToken, "剧本审核访问团队");
        Long ownerId = userIdByMobile("13800017113");
        Long mainProjectId = createMainProject(ownerToken, tenantId, ownerId, "审核绑定项目", "REVIEW_ACCESS_PROJECT");
        Long personalDraftId = importReviewProject(ownerToken, tenantId, "创建者草稿");
        Long boundProjectId = importBoundReviewProject(ownerToken, tenantId, mainProjectId, "项目审核稿");

        String memberToken = registerUser("13800017114", "Review Project Member");
        Long memberId = userIdByMobile("13800017114");
        Long memberTenantMembershipId = addTenantMember(tenantId, memberId);
        Long projectMemberRoleId = jdbcTemplate.queryForObject(
            "select id from project_role where project_id = ? and code = 'MEMBER'", Long.class, mainProjectId);
        jdbcTemplate.update("""
            insert into project_member
              (tenant_id, project_id, user_id, role_id, joined_at, status, created_by, created_at, updated_at)
            values (?, ?, ?, ?, now(), 'ACTIVE', ?, now(), now())
            """, tenantId, mainProjectId, memberId, projectMemberRoleId, ownerId);

        String viewerToken = registerUser("13800017115", "Review Tenant Viewer");
        Long viewerId = userIdByMobile("13800017115");
        Long viewerMembershipId = addTenantMember(tenantId, viewerId);
        Long viewerRoleId = grantTenantWideProjectView(tenantId, ownerId, viewerMembershipId);

        String outsiderToken = registerUser("13800017116", "Review Outsider");
        Long outsiderId = userIdByMobile("13800017116");
        addTenantMember(tenantId, outsiderId);

        String crossTenantOwnerToken = registerUser("13800017117", "Review Cross Tenant");
        Long crossTenantId = createTenant(crossTenantOwnerToken, "跨租户审核团队");
        Long crossTenantProjectId = importReviewProject(crossTenantOwnerToken, crossTenantId, "跨租户剧本");

        assertProjectIds(ownerToken, tenantId, "/summaries", personalDraftId, boundProjectId);
        assertMetricProjectIds(ownerToken, tenantId, personalDraftId, boundProjectId);
        assertProjectIds(memberToken, tenantId, "/summaries", boundProjectId);
        assertMetricProjectIds(memberToken, tenantId, boundProjectId);
        assertProjectIds(viewerToken, tenantId, "/summaries", personalDraftId, boundProjectId);
        assertMetricProjectIds(viewerToken, tenantId, personalDraftId, boundProjectId);
        assertProjectIds(outsiderToken, tenantId, "/summaries");
        assertMetricProjectIds(outsiderToken, tenantId);

        jdbcTemplate.update("update project_member set status = 'REMOVED' where project_id = ? and user_id = ?",
            mainProjectId, memberId);
        assertProjectIds(memberToken, tenantId, "/summaries");
        assertMetricProjectIds(memberToken, tenantId);

        assertThat(viewerRoleId).isNotNull();
        assertThat(memberTenantMembershipId).isNotNull();
        assertThat(crossTenantProjectId).isNotEqualTo(personalDraftId);
    }

    @Test
    void retriesOnlyFailedDeepUnitsThenAggregationAndSupportsFullRegeneration() throws Exception {
        String token = registerUser("13800017010", "Review Retry");
        Long tenantId = createTenant(token, "剧本审核重试团队");
        seedTextModel();
        grantTeamPoints(tenantId, 10);
        Long modelId = jdbcTemplate.queryForObject(
            "select id from ai_model where code <> 'review-test-text-model' order by id limit 1", Long.class);

        MvcResult imported = mockMvc.perform(multipart("/api/script-review/projects")
                .file(new MockMultipartFile("content", "", MediaType.TEXT_PLAIN_VALUE,
                    "没有分集标题的完整剧本正文".getBytes()))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk()).andReturn();
        Long projectId = readLong(imported, "$.data.project.id");
        Long versionId = readLong(imported, "$.data.versions[0].id");
        MvcResult created = mockMvc.perform(post("/api/script-review/projects/%d/tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"versionId":%d,"reviewMode":"DEEP","selectedDimensions":["台词合理性"],
                     "reviewScopeType":"ALL","reviewScope":{}}
                    """.formatted(versionId)))
            .andExpect(status().isAccepted()).andReturn();
        Long executionId = readLong(created, "$.data.id");
        Long taskId = readLong(created, "$.data.businessId");
        jdbcTemplate.update("""
            insert into review_fanout_snapshot
              (tenant_id, project_id, task_id, script_version_id, attempt_no, agent_code,
               agent_revision, skill_revisions_json, model_id, review_mode,
               selected_dimensions_json, review_scope_json, version_hash, scope_hash,
               dimensions_hash, unit_set_hash, status, total_units, completed_units,
               failed_units, max_concurrency, aggregation_status, created_at, updated_at)
            values (?, ?, ?, ?, 1, 'script-review', 1, '[]', ?, 'DEEP',
                    '["台词合理性"]', '{}', 'version', 'scope', 'dimensions', 'units',
                    'PARTIAL_FAILED', 1, 0, 1, 2, 'PENDING', now(), now())
            """, tenantId, projectId, taskId, versionId, modelId);
        Long snapshotId = jdbcTemplate.queryForObject(
            "select max(id) from review_fanout_snapshot where task_id = ?", Long.class, taskId);
        jdbcTemplate.update("""
            insert into review_fanout_unit
              (snapshot_id, unit_no, unit_key, stage_type, dimension, scope_json, start_offset, end_offset,
               content_fingerprint, status, attempt_no, report_saved, created_at, updated_at)
            values (?, 1, 'dimension-dialogue', 'DIMENSION_MARKDOWN', '台词合理性', '{}',
                    0, 12, 'fingerprint', 'FAILED', 1, false, now(), now())
            """, snapshotId);
        Long userId = jdbcTemplate.queryForObject(
            "select created_by from review_task where id=?", Long.class, taskId);
        jdbcTemplate.update("""
            insert into ai_workflow_agent_run
              (agent_code, run_type, tenant_id, user_id, project_id, task_id, status, model_id,
               temperature, max_tokens, max_steps, prompt_snapshot, started_at, finished_at, created_at)
            values ('script-review', 'REVIEW_CHILD', ?, ?, ?, ?, 'SUCCEEDED', ?,
                    0.1, 4096, 20, '', now(), now(), now())
            """, tenantId, userId, projectId, taskId, modelId);
        Long runId = jdbcTemplate.queryForObject(
            "select max(id) from ai_workflow_agent_run where task_id=?", Long.class, taskId);
        Long unitId = jdbcTemplate.queryForObject(
            "select id from review_fanout_unit where snapshot_id=?", Long.class, snapshotId);
        jdbcTemplate.update("update review_fanout_unit set child_run_id=? where id=?", runId, unitId);
        jdbcTemplate.update("""
            insert into ai_call_log
              (tenant_id, user_id, provider, service_type, model, business_scene, status,
               duration_ms, prompt_tokens, completion_tokens, total_tokens, cached_input_tokens,
               cache_write_tokens, prompt_cache_key, created_at)
            values (?, ?, 'OpenAI', 'TEXT', 'gpt-5.6-terra', 'SCRIPT_REVIEW', 'SUCCESS',
                    321, 9631, 5, 9636, 8960, 512, 'review:test', now())
            """, tenantId, userId);
        Long callLogId = jdbcTemplate.queryForObject("select max(id) from ai_call_log", Long.class);
        jdbcTemplate.update("""
            insert into ai_workflow_agent_run_step
              (run_id, step_no, step_type, status, ai_call_log_id, started_at, finished_at, created_at)
            values (?, 1, 'MODEL', 'SUCCEEDED', ?, now(), now(), now())
            """, runId, callLogId);
        jdbcTemplate.update("update review_task set status='FAILED', fanout_snapshot_id=? where id=?",
            snapshotId, taskId);
        jdbcTemplate.update("update ai_execution_task set status='FAILED' where id=?", executionId);

        mockMvc.perform(get("/api/script-review/tasks/%d".formatted(taskId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.fanout.units[0].stageType", is("DIMENSION_MARKDOWN")))
            .andExpect(jsonPath("$.data.fanout.units[0].dimension", is("台词合理性")))
            .andExpect(jsonPath("$.data.fanout.units[0].attemptNo", is(1)))
            .andExpect(jsonPath("$.data.fanout.units[0].status", is("FAILED")))
            .andExpect(jsonPath("$.data.observability.cacheUsage.promptTokens", is(9631)))
            .andExpect(jsonPath("$.data.observability.cacheUsage.ordinaryInputTokens", is(159)))
            .andExpect(jsonPath("$.data.observability.cacheUsage.cachedInputTokens", is(8960)))
            .andExpect(jsonPath("$.data.observability.cacheUsage.cacheWriteTokens", is(512)))
            .andExpect(jsonPath("$.data.observability.cacheUsage.outputTokens", is(5)))
            .andExpect(jsonPath("$.data.observability.cacheUsage.latencyMs", is(321)))
            .andExpect(jsonPath("$.data.observability.cacheUsage.cacheObservable", is(true)))
            .andExpect(jsonPath("$.data.fanout.units[0].cacheUsage.cachedInputTokens", is(8960)));

        jdbcTemplate.update(
            "update ai_call_log set cached_input_tokens=null, cache_write_tokens=null where id=?",
            callLogId);
        mockMvc.perform(get("/api/script-review/tasks/%d".formatted(taskId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.observability.cacheUsage.cacheObservable", is(false)))
            .andExpect(jsonPath("$.data.observability.cacheUsage.cachedInputTokens").doesNotExist())
            .andExpect(jsonPath("$.data.observability.cacheUsage.cacheWriteTokens").doesNotExist())
            .andExpect(jsonPath("$.data.observability.cacheUsage.promptTokens", is(9631)));

        mockMvc.perform(post("/api/script-review/tasks/%d/retry".formatted(taskId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isAccepted());
        assertThat(jdbcTemplate.queryForObject("select retry_kind from review_task where id=?", String.class, taskId))
            .isEqualTo("FAILED_UNITS");

        jdbcTemplate.update("update review_fanout_unit set status='SUCCEEDED', report_saved=true where snapshot_id=?",
            snapshotId);
        jdbcTemplate.update("update review_task set status='FAILED' where id=?", taskId);
        jdbcTemplate.update("update ai_execution_task set status='FAILED' where id=?", executionId);
        mockMvc.perform(post("/api/script-review/tasks/%d/retry".formatted(taskId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isAccepted());
        assertThat(jdbcTemplate.queryForObject("select retry_kind from review_task where id=?", String.class, taskId))
            .isEqualTo("AGGREGATION_ONLY");

        jdbcTemplate.update("update review_task set status='FAILED' where id=?", taskId);
        jdbcTemplate.update("update ai_execution_task set status='FAILED' where id=?", executionId);
        mockMvc.perform(post("/api/script-review/tasks/%d/retry?fullRegeneration=true".formatted(taskId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isAccepted());
        assertThat(jdbcTemplate.queryForObject("select retry_kind from review_task where id=?", String.class, taskId))
            .isEqualTo("FULL_REGENERATION");
        assertThat(jdbcTemplate.queryForObject("select fanout_snapshot_id from review_task where id=?", Long.class, taskId))
            .isNull();
        assertThat(jdbcTemplate.queryForObject("select status from review_fanout_snapshot where id=?", String.class, snapshotId))
            .isEqualTo("STALE");
    }

    @Test
    void importsStandaloneScriptAndCreatesIdempotentReviewTask() throws Exception {
        String token = registerUser("13800017001", "Review Owner");
        Long tenantId = createTenant(token, "剧本审核团队");
        seedTextModel();
        grantTeamPoints(tenantId, 10);

        MockMultipartFile name = new MockMultipartFile(
            "name",
            "",
            MediaType.TEXT_PLAIN_VALUE,
            "name-confusion-sample".getBytes()
        );
        MockMultipartFile content = new MockMultipartFile(
            "content",
            "",
            MediaType.TEXT_PLAIN_VALUE,
            "Episode 1\nLin Wan calls Zhou Ye, then Zhou Ye again.".getBytes()
        );
        MvcResult imported = mockMvc.perform(multipart("/api/script-review/projects")
                .file(name)
                .file(content)
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.project.name", is("name-confusion-sample")))
            .andExpect(jsonPath("$.data.versions", hasSize(1)))
            .andReturn();
        Long projectId = readLong(imported, "$.data.project.id");
        Long versionId = readLong(imported, "$.data.versions[0].id");

        String body = """
            {
              "versionId":%d,
              "reviewMode":"QUICK",
              "selectedDimensions":["台词合理性","人物关系一致性"],
              "reviewScopeType":"ALL",
              "reviewScope":{}
            }
            """.formatted(versionId);
        MvcResult firstTask = mockMvc.perform(post("/api/script-review/projects/%d/tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.status", is("PENDING")))
            .andExpect(jsonPath("$.data.businessType", is("REVIEW_TASK")))
            .andReturn();
        Long executionId = readLong(firstTask, "$.data.id");
        Long firstTaskId = readLong(firstTask, "$.data.businessId");

        mockMvc.perform(post("/api/script-review/projects/%d/tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.id", is(executionId.intValue())));

        when(workflowAgentRunner.runFormal(any(), any())).thenAnswer(invocation ->
            workflowResult(invocation.getArgument(1), "# 审核报告", false));
        aiExecutionWorker.run(executionId);
        ArgumentCaptor<com.antshorttv.workflowagent.run.WorkflowAgentRunInput> invocationRequest =
            ArgumentCaptor.forClass(com.antshorttv.workflowagent.run.WorkflowAgentRunInput.class);
        verify(workflowAgentRunner).runFormal(any(), invocationRequest.capture());
        assertThat(invocationRequest.getValue().executionId()).isEqualTo(executionId);
        assertThat(invocationRequest.getValue().attemptId()).isNotNull();
        assertThat(invocationRequest.getValue().reviewScope().phase()).isEqualTo("MARKDOWN_QUICK");
        assertThat(jdbcTemplate.queryForObject(
            "select status from review_task where id = ?", String.class, firstTaskId
        )).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject(
            "select status from ai_execution_task where id = ?", String.class, executionId
        )).isEqualTo("SUCCEEDED");
        assertThat(jdbcTemplate.queryForObject(
            "select settled_points from ai_point_reservation where execution_id = ?",
            java.math.BigDecimal.class, executionId
        )).isEqualByComparingTo("1");

        mockMvc.perform(get("/api/script-review/projects/%d".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.tasks", hasSize(1)))
            .andExpect(jsonPath("$.data.tasks[0].selectedDimensions", hasSize(2)));

        MvcResult exportResult = mockMvc.perform(post("/api/script-review/projects/%d/exports".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"versionId":%d,"exportType":"WORD"}
                    """.formatted(versionId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.fileName").value(org.hamcrest.Matchers.endsWith(".md")))
            .andReturn();
        String fileName = JsonPath.read(exportResult.getResponse().getContentAsString(), "$.data.fileName");
        org.assertj.core.api.Assertions.assertThat(
            Files.exists(Path.of("storage/review-exports").resolve(fileName))
        ).isTrue();

        mockMvc.perform(get("/api/script-review/exports/%s".formatted(fileName))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM));

        String markdown = "# 原样报告\n\n|位置|结论|\n|---|---|\n|第1集|保留 `字段`|";
        jdbcTemplate.update("update review_task set report_markdown=? where id=?",
            markdown, firstTaskId);
        mockMvc.perform(get("/api/script-review/tasks/%d".formatted(firstTaskId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.resultFormat").doesNotExist())
            .andExpect(jsonPath("$.data.reportMarkdown", is(markdown)));
        mockMvc.perform(get("/api/script-review/projects/%d".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.project.reviewState", is("COMPLETED")));
        MvcResult markdownExport = mockMvc.perform(post("/api/script-review/projects/%d/exports".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"versionId":%d,"exportType":"WORD"}
                    """.formatted(versionId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.fileName").value(org.hamcrest.Matchers.endsWith(".md")))
            .andReturn();
        String markdownFileName = JsonPath.read(markdownExport.getResponse().getContentAsString(), "$.data.fileName");
        assertThat(Files.readString(Path.of("storage/review-exports").resolve(markdownFileName))).isEqualTo(markdown);

        jdbcTemplate.update("update review_task set status='FAILED' where id=?", firstTaskId);
        mockMvc.perform(get("/api/script-review/projects/%d".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.project.reviewState", is("NOT_REVIEWED")))
            .andExpect(jsonPath("$.data.project.actionLabel", is("重试审核")));
    }

    @Test
    void rejectsEditingRunningTaskConfiguration() throws Exception {
        String token = registerUser("13800017002", "Review Runner");
        Long tenantId = createTenant(token, "剧本审核锁定团队");
        seedTextModel();
        grantTeamPoints(tenantId, 10);

        MvcResult imported = mockMvc.perform(multipart("/api/script-review/projects")
                .file(new MockMultipartFile("content", "", MediaType.TEXT_PLAIN_VALUE, "Episode 1\nA".getBytes()))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andReturn();
        Long projectId = readLong(imported, "$.data.project.id");
        Long versionId = readLong(imported, "$.data.versions[0].id");

        MvcResult createdTask = mockMvc.perform(post("/api/script-review/projects/%d/tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"versionId":%d,"reviewMode":"QUICK","selectedDimensions":["台词合理性"],"reviewScopeType":"ALL","reviewScope":{}}
                    """.formatted(versionId)))
            .andExpect(status().isAccepted())
            .andReturn();
        Long taskId = readLong(createdTask, "$.data.businessId");

        jdbcTemplate.update("""
            update review_task
               set status = 'RUNNING',
                   current_stage = 'AI_REVIEW',
                   current_action = '正在生成审核问题'
             where id = ?
            """, taskId);

        mockMvc.perform(put("/api/script-review/tasks/%d/config".formatted(taskId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reviewMode":"DEEP","selectedDimensions":["人物关系一致性"],"reviewScopeType":"EPISODES","reviewScope":{"episodeNos":[1]}}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorMessage").value(org.hamcrest.Matchers.containsString("运行中")));
    }

    @Test
    void returnsVersionHistoryAndRoundMappingsForSelectedVersion() throws Exception {
        String token = registerUser("13800017003", "Review Historian");
        Long tenantId = createTenant(token, "剧本审核历史团队");
        seedTextModel();
        grantTeamPoints(tenantId, 10);

        MvcResult imported = mockMvc.perform(multipart("/api/script-review/projects")
                .file(new MockMultipartFile("content", "", MediaType.TEXT_PLAIN_VALUE, "第1集\n林晚说：别走。".getBytes()))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andReturn();
        Long projectId = readLong(imported, "$.data.project.id");
        Long firstVersionId = readLong(imported, "$.data.versions[0].id");

        MvcResult savedVersion = mockMvc.perform(put("/api/script-review/projects/%d/versions".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"content":"第1集\\n林晚说：别走。\\n周野说：我会回来。","fileName":"history-sample.md","sourceType":"MANUAL_EDIT"}
                    """))
            .andExpect(status().isOk())
            .andReturn();
        Long secondVersionId = readLong(savedVersion, "$.data.id");

        MvcResult createdTask = mockMvc.perform(post("/api/script-review/projects/%d/tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"versionId":%d,"reviewMode":"QUICK","selectedDimensions":["台词合理性"],"reviewScopeType":"ALL","reviewScope":{}}
                    """.formatted(secondVersionId)))
            .andExpect(status().isAccepted())
            .andReturn();
        Long taskId = readLong(createdTask, "$.data.businessId");

        jdbcTemplate.update("""
            update review_task
               set status = 'COMPLETED',
                   current_stage = null,
                   current_action = '审核已完成',
                   overall_progress = 100,
                   completed_at = now(),
                   report_markdown = '# 审核报告'
             where id = ?
            """, taskId);
        mockMvc.perform(get("/api/script-review/projects/%d/versions/%d/history".formatted(projectId, secondVersionId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.selectedVersion.id", is(secondVersionId.intValue())))
            .andExpect(jsonPath("$.data.versions", hasSize(2)))
            .andExpect(jsonPath("$.data.roundHistory", hasSize(1)))
            .andExpect(jsonPath("$.data.issueMappings").doesNotExist())
            .andExpect(jsonPath("$.data.diffLines", hasSize(org.hamcrest.Matchers.greaterThanOrEqualTo(1))));
    }

    @Test
    void rejectsEmptyMarkdownAndPreservesProviderEvidence() throws Exception {
        String token = registerUser("13800017004", "Review Logger");
        Long tenantId = createTenant(token, "剧本审核日志团队");
        seedTextModel();
        grantTeamPoints(tenantId, 10);

        MvcResult imported = mockMvc.perform(multipart("/api/script-review/projects")
                .file(new MockMultipartFile("content", "", MediaType.TEXT_PLAIN_VALUE, "第1集\n林晚说：别走。".getBytes()))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andReturn();
        Long projectId = readLong(imported, "$.data.project.id");
        Long versionId = readLong(imported, "$.data.versions[0].id");

        MvcResult createdTask = mockMvc.perform(post("/api/script-review/projects/%d/tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"versionId":%d,"reviewMode":"QUICK","selectedDimensions":["台词合理性"],"reviewScopeType":"ALL","reviewScope":{}}
                    """.formatted(versionId)))
            .andExpect(status().isAccepted())
            .andReturn();
        Long executionId = readLong(createdTask, "$.data.id");

        seedTextModel();
        when(workflowAgentRunner.runFormal(any(), any())).thenAnswer(invocation ->
            workflowResult(invocation.getArgument(1), "", false));

        aiExecutionWorker.run(executionId);

        assertThat(jdbcTemplate.queryForObject(
            "select business_outcome from ai_call_log where execution_id=?", String.class, executionId))
            .isEqualTo("SUCCESS");
        assertThat(jdbcTemplate.queryForObject(
            "select status from review_task where execution_id=?", String.class, executionId)).isEqualTo("FAILED");
    }

    @Test
    void preservesFailedReviewInvocationEvidenceForSharedRetry() throws Exception {
        String token = registerUser("13800017007", "Review Retry Evidence");
        Long tenantId = createTenant(token, "审核失败证据团队");
        seedTextModel();
        grantTeamPoints(tenantId, 10);
        MvcResult imported = mockMvc.perform(multipart("/api/script-review/projects")
                .file(new MockMultipartFile("content", "", MediaType.TEXT_PLAIN_VALUE, "第1集\n林晚说：别走。".getBytes()))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andReturn();
        Long projectId = readLong(imported, "$.data.project.id");
        Long versionId = readLong(imported, "$.data.versions[0].id");
        MvcResult submitted = mockMvc.perform(post("/api/script-review/projects/%d/tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"versionId":%d,"reviewMode":"QUICK","selectedDimensions":["台词合理性"],"reviewScopeType":"ALL","reviewScope":{}}
                    """.formatted(versionId)))
            .andExpect(status().isAccepted())
            .andReturn();
        Long executionId = readLong(submitted, "$.data.id");
        when(workflowAgentRunner.runFormal(any(), any())).thenAnswer(invocation ->
            workflowResult(invocation.getArgument(1), "", true));

        aiExecutionWorker.run(executionId);

        assertThat(jdbcTemplate.queryForObject(
            "select status from ai_execution_task where id = ?", String.class, executionId
        )).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject(
            "select ai_call_log_id from ai_execution_attempt where execution_id = ?",
            Long.class, executionId
        )).isEqualTo(jdbcTemplate.queryForObject(
            "select id from ai_call_log where execution_id=?", Long.class, executionId));
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from ai_usage_line where execution_id = ? and metric = 'CALL'",
            Integer.class, executionId
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "select status from ai_point_reservation where execution_id = ?",
            String.class, executionId
        )).isEqualTo("RESERVED");
    }

    @Test
    void settlesReviewReservationAfterBusinessFailureExhaustsRetries() throws Exception {
        String token = registerUser("13800017008", "Review Retry Exhaustion");
        Long tenantId = createTenant(token, "审核重试耗尽团队");
        seedTextModel();
        grantTeamPoints(tenantId, 10);
        MvcResult imported = mockMvc.perform(multipart("/api/script-review/projects")
                .file(new MockMultipartFile("content", "", MediaType.TEXT_PLAIN_VALUE, "第1集\n林晚说：别走。".getBytes()))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andReturn();
        Long projectId = readLong(imported, "$.data.project.id");
        Long versionId = readLong(imported, "$.data.versions[0].id");
        MvcResult submitted = mockMvc.perform(post("/api/script-review/projects/%d/tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"versionId":%d,"reviewMode":"QUICK","selectedDimensions":["台词合理性"],"reviewScopeType":"ALL","reviewScope":{}}
                    """.formatted(versionId)))
            .andExpect(status().isAccepted())
            .andReturn();
        Long executionId = readLong(submitted, "$.data.id");
        when(workflowAgentRunner.runFormal(any(), any())).thenAnswer(invocation ->
            workflowResult(invocation.getArgument(1), "", true));

        for (int attempt = 1; attempt <= 3; attempt++) {
            aiExecutionWorker.run(executionId);
            if (attempt < 3) {
                assertThat(jdbcTemplate.queryForObject(
                    "select status from ai_point_reservation where execution_id = ?",
                    String.class, executionId
                )).isEqualTo("RESERVED");
                jdbcTemplate.update("update ai_execution_task set next_run_at = null where id = ?", executionId);
            }
        }

        assertThat(jdbcTemplate.queryForObject(
            "select status from ai_execution_task where id = ?", String.class, executionId
        )).isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from ai_usage_line where execution_id = ? and metric = 'CALL'",
            Integer.class, executionId
        )).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
            "select status from ai_point_reservation where execution_id = ?",
            String.class, executionId
        )).isEqualTo("SETTLED");
        assertThat(jdbcTemplate.queryForObject(
            "select settled_points from ai_point_reservation where execution_id = ?",
            java.math.BigDecimal.class, executionId
        )).isEqualByComparingTo("3");
    }

    @Test
    void releasesReviewReservationAfterProviderRejectionExhaustsRetries() throws Exception {
        String token = registerUser("13800017009", "Review Provider Rejection");
        Long tenantId = createTenant(token, "审核供应商拒绝团队");
        seedTextModel();
        grantTeamPoints(tenantId, 10);
        MvcResult imported = mockMvc.perform(multipart("/api/script-review/projects")
                .file(new MockMultipartFile("content", "", MediaType.TEXT_PLAIN_VALUE, "第1集\n林晚说：别走。".getBytes()))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andReturn();
        Long projectId = readLong(imported, "$.data.project.id");
        Long versionId = readLong(imported, "$.data.versions[0].id");
        MvcResult submitted = mockMvc.perform(post("/api/script-review/projects/%d/tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"versionId":%d,"reviewMode":"QUICK","selectedDimensions":["台词合理性"],"reviewScopeType":"ALL","reviewScope":{}}
                    """.formatted(versionId)))
            .andExpect(status().isAccepted())
            .andReturn();
        Long executionId = readLong(submitted, "$.data.id");
        when(workflowAgentRunner.runFormal(any(), any())).thenThrow(
            new com.antshorttv.ai.AiGatewayException(com.antshorttv.common.ErrorCode.AI_PROVIDER_ERROR, "provider rejected"),
            new com.antshorttv.ai.AiGatewayException(com.antshorttv.common.ErrorCode.AI_PROVIDER_ERROR, "provider rejected"),
            new com.antshorttv.ai.AiGatewayException(com.antshorttv.common.ErrorCode.AI_PROVIDER_ERROR, "provider rejected")
        );

        for (int attempt = 1; attempt <= 3; attempt++) {
            aiExecutionWorker.run(executionId);
            if (attempt < 3) {
                jdbcTemplate.update("update ai_execution_task set next_run_at = null where id = ?", executionId);
            }
        }

        assertThat(jdbcTemplate.queryForObject(
            "select status from ai_execution_task where id = ?", String.class, executionId
        )).isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForObject(
            "select status from ai_point_reservation where execution_id = ?",
            String.class, executionId
        )).isEqualTo("RELEASED");
        assertThat(jdbcTemplate.queryForObject(
            "select released_points from ai_point_reservation where execution_id = ?",
            java.math.BigDecimal.class, executionId
        )).isEqualByComparingTo("1");
    }

    @Test
    void supportsCancelRetryAndRejectsRetiredMutationEndpoints() throws Exception {
        String token = registerUser("13800017005", "Review Lifecycle");
        Long tenantId = createTenant(token, "剧本审核生命周期团队");
        seedTextModel();
        grantTeamPoints(tenantId, 10);

        MvcResult imported = mockMvc.perform(multipart("/api/script-review/projects")
                .file(new MockMultipartFile("content", "", MediaType.TEXT_PLAIN_VALUE, "第1集\n第2集".getBytes()))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andReturn();
        Long projectId = readLong(imported, "$.data.project.id");
        Long versionId = readLong(imported, "$.data.versions[0].id");

        MvcResult taskForCancel = mockMvc.perform(post("/api/script-review/projects/%d/tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"versionId":%d,"reviewMode":"QUICK","selectedDimensions":["台词合理性"],"reviewScopeType":"ALL","reviewScope":{}}
                    """.formatted(versionId)))
            .andExpect(status().isAccepted())
            .andReturn();
        Long cancelTaskId = readLong(taskForCancel, "$.data.businessId");

        mockMvc.perform(post("/api/script-review/tasks/%d/cancel".formatted(cancelTaskId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("CANCELED")));

        MvcResult taskForRetry = mockMvc.perform(post("/api/script-review/projects/%d/tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"versionId":%d,"reviewMode":"DEEP","selectedDimensions":["人物关系一致性"],"reviewScopeType":"ALL","reviewScope":{}}
                    """.formatted(versionId)))
            .andExpect(status().isAccepted())
            .andReturn();
        Long retryExecutionId = readLong(taskForRetry, "$.data.id");
        Long retryTaskId = readLong(taskForRetry, "$.data.businessId");
        jdbcTemplate.update("update review_task set status = 'FAILED', error_code = 'AI_RESPONSE_INVALID', error_message = 'bad json' where id = ?", retryTaskId);
        jdbcTemplate.update("update ai_execution_task set status = 'FAILED', error_code = 'AI_RESPONSE_INVALID', error_message = 'bad json' where id = ?", retryExecutionId);

        mockMvc.perform(post("/api/script-review/tasks/%d/retry".formatted(retryTaskId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.status", is("PENDING")));

        for (String endpoint : java.util.List.of(
            "/api/script-review/issues/1/resolve",
            "/api/script-review/tasks/" + retryTaskId + "/batch-repair")) {
            mockMvc.perform(post(endpoint)
                    .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                    .header("X-Tenant-Id", tenantId)
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        }
        org.mockito.Mockito.verifyNoInteractions(workflowAgentRunner);
        mockMvc.perform(post("/api/script-review/projects/%d/rollback".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"versionId\":%d}".formatted(versionId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.versionNo", is(2)));
    }

    @Test
    void routesMultiEpisodeQuickAndDeepToFrozenMarkdownPhases() throws Exception {
        String token = registerUser("13800017006", "Review Index");
        Long tenantId = createTenant(token, "剧本审核索引团队");
        grantTeamPoints(tenantId, 10);

        MvcResult imported = mockMvc.perform(multipart("/api/script-review/projects")
                .file(new MockMultipartFile("content", "", MediaType.TEXT_PLAIN_VALUE, "EP1\nA\n\nEP2\nB".getBytes()))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andReturn();
        Long projectId = readLong(imported, "$.data.project.id");
        Long versionId = readLong(imported, "$.data.versions[0].id");

        seedTextModel();
        when(workflowAgentRunner.runFormal(any(), any())).thenAnswer(invocation ->
            workflowResult(invocation.getArgument(1), "# 审核报告", false));

        MvcResult quickTask = mockMvc.perform(post("/api/script-review/projects/%d/tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"versionId":%d,"reviewMode":"QUICK","selectedDimensions":["台词合理性"],"reviewScopeType":"ALL","reviewScope":{}}
                    """.formatted(versionId)))
            .andExpect(status().isAccepted())
            .andReturn();
        Long quickExecutionId = readLong(quickTask, "$.data.id");

        MvcResult deepTask = mockMvc.perform(post("/api/script-review/projects/%d/tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"versionId":%d,"reviewMode":"DEEP","selectedDimensions":["人物关系一致性"],"reviewScopeType":"ALL","reviewScope":{}}
                    """.formatted(versionId)))
            .andExpect(status().isAccepted())
            .andReturn();
        Long deepExecutionId = readLong(deepTask, "$.data.id");

        aiExecutionWorker.run(quickExecutionId);
        aiExecutionWorker.run(deepExecutionId);

        ArgumentCaptor<com.antshorttv.workflowagent.run.WorkflowAgentRunInput> captor =
            ArgumentCaptor.forClass(com.antshorttv.workflowagent.run.WorkflowAgentRunInput.class);
        verify(workflowAgentRunner, org.mockito.Mockito.atLeast(3)).runFormal(any(), captor.capture());
        assertThat(captor.getAllValues()).extracting(input -> input.reviewScope().phase())
            .contains("MARKDOWN_QUICK", "MARKDOWN_DEEP_CHILD", "MARKDOWN_DEEP_AGGREGATION");
        assertThat(captor.getAllValues()).allSatisfy(input -> {
            assertThat(input.reviewScope().versionId()).isEqualTo(versionId);
            assertThat(input.executionId()).isIn(quickExecutionId, deepExecutionId);
            assertThat(input.attemptId()).isNotNull();
        });
    }

    private void seedTextModel() {
        Long providerId = jdbcTemplate.queryForObject("select id from ai_provider where code = 'OpenAI' limit 1", Long.class);
        jdbcTemplate.update("update ai_model set is_default = false where service_type = 'TEXT'");
        if (jdbcTemplate.queryForObject("select count(*) from ai_model where code='review-test-text-model'",
            Integer.class) == 0) {
        jdbcTemplate.update("""
            insert into ai_model
              (provider_id, code, name, model_code, service_type, status, is_default, sort, created_at, updated_at)
            values
              (?, 'review-test-text-model', 'Review Test Text Model', 'review-test-text-model', 'TEXT', 'ENABLED', true, 999, now(), now())
            """, providerId);
        }
        jdbcTemplate.update("update ai_model set is_default=true where code='review-test-text-model'");
        Long modelId = jdbcTemplate.queryForObject(
            "select id from ai_model where code = 'review-test-text-model'", Long.class
        );
        if (jdbcTemplate.queryForObject(
            "select count(*) from ai_model_capability where model_id=? and capability='TOOL_CALLING'",
            Integer.class, modelId) == 0) {
            jdbcTemplate.update("""
                insert into ai_model_capability (model_id, capability, status, created_at, updated_at)
                values (?, 'TOOL_CALLING', 'ENABLED', now(), now())
                """, modelId);
        }
        reviewAgentBootstrap.run(null);
        com.antshorttv.support.ModelBillingTestSupport.publish(
            jdbcTemplate, modelId, "CALL", java.math.BigDecimal.ONE, java.math.BigDecimal.ONE
        );
    }

    private com.antshorttv.workflowagent.run.WorkflowAgentRunResult workflowResult(
        com.antshorttv.workflowagent.run.WorkflowAgentRunInput input, String report, boolean fail
    ) {
        jdbcTemplate.update("""
            insert into ai_workflow_agent_run
              (agent_code, run_type, tenant_id, user_id, project_id, task_id, status, model_id,
               temperature, max_tokens, max_steps, prompt_snapshot, started_at, finished_at, created_at)
            values ('script-review', 'REVIEW_CHILD', ?, ?, ?, ?, ?, ?, 0.1, 4096, 20, '', now(), now(), now())
            """, input.tenantId(), input.userId(), input.projectId(), input.taskId(),
            fail ? "FAILED" : "SUCCEEDED", input.modelIdOverride());
        Long runId = jdbcTemplate.queryForObject(
            "select max(id) from ai_workflow_agent_run where task_id=?", Long.class, input.taskId());
        Long providerId = jdbcTemplate.queryForObject("select provider_id from ai_model where id=?",
            Long.class, input.modelIdOverride());
        jdbcTemplate.update("""
            insert into ai_call_log
              (tenant_id, user_id, provider, provider_id, service_type, model, model_id, business_scene,
               status, duration_ms, prompt_tokens, completion_tokens, total_tokens, execution_id,
               attempt_id, transport_outcome, business_outcome, created_at)
            values (?, ?, 'OpenAI', ?, 'TEXT', 'review-test-text-model', ?, 'SCRIPT_REVIEW',
                    'SUCCESS', 12, 0, 0, 0, ?, ?, 'SUCCESS', ?, now())
            """, input.tenantId(), input.userId(), providerId, input.modelIdOverride(),
            input.executionId(), input.attemptId(), fail ? "BUSINESS_FAILED" : "SUCCESS");
        Long callId = jdbcTemplate.queryForObject("select max(id) from ai_call_log where execution_id=?",
            Long.class, input.executionId());
        jdbcTemplate.update("""
            insert into ai_workflow_agent_run_step
              (run_id, step_no, step_type, status, ai_call_log_id, started_at, finished_at, created_at)
            values (?, 1, 'MODEL', ?, ?, now(), now(), now())
            """, runId, fail ? "FAILED" : "SUCCEEDED", callId);
        if (fail) {
            throw new com.antshorttv.common.BusinessException(
                com.antshorttv.common.ErrorCode.AI_RESPONSE_INVALID, "审核 Agent 返回的 Markdown 报告为空。");
        }
        return new com.antshorttv.workflowagent.run.WorkflowAgentRunResult(runId, report);
    }

    private void grantTeamPoints(Long tenantId, int amount) {
        jdbcTemplate.update("""
            insert into team_point_account
              (tenant_id, balance, total_granted, total_consumed, created_at, updated_at)
            values (?, ?, ?, 0, now(), now())
            """, tenantId, amount, amount);
    }

    private Long importReviewProject(String token, Long tenantId, String name) throws Exception {
        MvcResult imported = mockMvc.perform(multipart("/api/script-review/projects")
                .file(new MockMultipartFile("content", "", MediaType.TEXT_PLAIN_VALUE,
                    ("第一集\\n" + name).getBytes()))
                .param("name", name)
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andReturn();
        return readLong(imported, "$.data.project.id");
    }

    private Long insertCompletedReviewTask(
        Long tenantId,
        Long projectId,
        Long versionId,
        Long userId,
        String idempotencyKey
    ) {
        jdbcTemplate.update("""
            insert into review_task
              (tenant_id, project_id, script_version_id, round_no, review_mode,
               selected_dimensions_json, review_scope_type, report_markdown, status,
               overall_progress, idempotency_key, created_by, created_at, updated_at, completed_at)
            values (?, ?, ?, 1, 'QUICK', '[]', 'ALL', '# 审核报告', 'COMPLETED',
                    100, ?, ?, now(), now(), now())
            """, tenantId, projectId, versionId, idempotencyKey, userId);
        return jdbcTemplate.queryForObject(
            "select max(id) from review_task where project_id = ?", Long.class, projectId);
    }

    private void assertProjectIds(String token, Long tenantId, String path, Long... projectIds) throws Exception {
        ResultActions request = mockMvc.perform(get("/api/script-review/projects" + path)
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk());
        if (projectIds.length == 0) {
            request.andExpect(jsonPath("$.data", hasSize(0)));
            return;
        }
        request.andExpect(jsonPath("$.data[*].id", containsInAnyOrder(
            java.util.Arrays.stream(projectIds).map(Long::intValue).toArray(Integer[]::new))));
    }

    private void assertMetricProjectIds(String token, Long tenantId, Long... projectIds) throws Exception {
        ResultActions request = mockMvc.perform(get("/api/script-review/projects/metrics")
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk());
        if (projectIds.length == 0) {
            request.andExpect(jsonPath("$.data", hasSize(0)));
            return;
        }
        request.andExpect(jsonPath("$.data[*].projectId", containsInAnyOrder(
            java.util.Arrays.stream(projectIds).map(Long::intValue).toArray(Integer[]::new))));
    }

    private Long createMainProject(String token, Long tenantId, Long ownerId, String name, String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"%s","code":"%s","description":"审核访问测试","ownerId":%d}
                    """.formatted(name, code, ownerId)))
            .andExpect(status().isOk())
            .andReturn();
        return readLong(result, "$.data.id");
    }

    private Long importBoundReviewProject(String token, Long tenantId, Long mainProjectId, String name) throws Exception {
        MvcResult imported = mockMvc.perform(multipart("/api/script-review/projects")
                .file(new MockMultipartFile("content", "", MediaType.TEXT_PLAIN_VALUE,
                    ("第一集\\n" + name).getBytes()))
                .param("name", name)
                .param("mainProjectId", mainProjectId.toString())
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andReturn();
        return readLong(imported, "$.data.project.id");
    }

    private Long addTenantMember(Long tenantId, Long userId) {
        jdbcTemplate.update("""
            insert into tenant_member (tenant_id, user_id, member_type, status, joined_at, created_at, updated_at)
            values (?, ?, 'MEMBER', 'ACTIVE', now(), now(), now())
            """, tenantId, userId);
        return jdbcTemplate.queryForObject(
            "select id from tenant_member where tenant_id = ? and user_id = ?", Long.class, tenantId, userId);
    }

    private Long grantTenantWideProjectView(Long tenantId, Long creatorId, Long membershipId) {
        jdbcTemplate.update("""
            insert into `role`
              (tenant_id, code, name, description, role_type, status, is_default, created_by, created_at, updated_at)
            values (?, 'REVIEW_LIST_VIEWER', '审核列表查看者', null, 'CUSTOM', 'ACTIVE', false, ?, now(), now())
            """, tenantId, creatorId);
        Long roleId = jdbcTemplate.queryForObject(
            "select id from `role` where tenant_id = ? and code = 'REVIEW_LIST_VIEWER'", Long.class, tenantId);
        Long permissionId = jdbcTemplate.queryForObject(
            "select id from permission where code = 'PROJECT:VIEW_ALL'", Long.class);
        jdbcTemplate.update("insert into role_permission (role_id, permission_id, created_at) values (?, ?, now())",
            roleId, permissionId);
        jdbcTemplate.update("insert into member_role (member_id, role_id, created_by, created_at) values (?, ?, ?, now())",
            membershipId, roleId, creatorId);
        return roleId;
    }

    private Long userIdByMobile(String mobile) {
        return jdbcTemplate.queryForObject("select id from app_user where mobile = ?", Long.class, mobile);
    }

    private String registerUser(String mobile, String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mobile":"%s","verificationCode":"123456","nickname":"%s","password":"Password123"}
                    """.formatted(mobile, nickname)))
            .andExpect(status().isOk())
            .andReturn();
        return com.antshorttv.support.SessionTestSupport.sessionCredential(result);
    }

    private Long createTenant(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tenants")
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"%s","type":"STUDIO","description":"剧本审核测试"}
                    """.formatted(name)))
            .andExpect(status().isOk())
            .andReturn();
        return readLong(result, "$.data.id");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private Long readLong(MvcResult result, String path) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return value.longValue();
    }
}
