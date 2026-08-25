package com.antshorttv.review;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.antshorttv.ai.AiBusinessScene;
import com.antshorttv.ai.AiCapability;
import com.antshorttv.ai.AiInvocationResult;
import com.antshorttv.ai.AiInvocationService;
import com.antshorttv.ai.AiInvocationRequest;
import com.antshorttv.ai.AiTextResponse;
import com.antshorttv.execution.AiExecutionWorker;
import com.antshorttv.points.TeamPointService;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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

    @MockBean
    private AiInvocationService aiInvocationService;

    @MockBean
    private TeamPointService teamPointService;

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

        when(aiInvocationService.invokeText(any())).thenReturn(successfulReviewInvocation(880L));
        aiExecutionWorker.run(executionId);
        ArgumentCaptor<AiInvocationRequest> invocationRequest = ArgumentCaptor.forClass(AiInvocationRequest.class);
        verify(aiInvocationService).invokeText(invocationRequest.capture());
        assertThat(invocationRequest.getValue().executionId()).isEqualTo(executionId);
        assertThat(invocationRequest.getValue().attemptId()).isNotNull();
        assertThat(invocationRequest.getValue().phase()).isEqualTo("AI_REVIEW");
        assertThat(invocationRequest.getValue().idempotencyKey()).contains("execution:" + executionId);
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
            .andExpect(jsonPath("$.data.fileName").value(org.hamcrest.Matchers.endsWith(".docx")))
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
                   result_json = '{"overallScore":88,"overallConclusion":"PASS","summary":"ok"}'
             where id = ?
            """, taskId);
        jdbcTemplate.update("""
            insert into review_issue
              (tenant_id, project_id, task_id, script_version_id, round_no, issue_no, dimension, severity, title, position_json,
               excerpt, problem, evidence_json, suggestion, status, related_issue_no, manually_resolved, created_at, updated_at)
            values
              (?, ?, ?, ?, 1, 'R1-01', '台词合理性', 'P1', '人名混乱', '{"episode":1,"scene":"1"}',
               '林晚说：别走。', '同一句台词里称呼不一致', '["林晚和周野称呼混乱"]', '统一称呼', 'persists', null, false, now(), now())
            """, tenantId, projectId, taskId, secondVersionId);
        Long issueId = jdbcTemplate.queryForObject("select max(id) from review_issue where task_id = ?", Long.class, taskId);
        jdbcTemplate.update("""
            insert into review_issue_hit
              (tenant_id, project_id, task_id, issue_id, hit_no, episode_no, scene_no, shot_no, line_no, anchor_label, excerpt,
               entity_name, selected, replacement_text, created_at, updated_at)
            values
              (?, ?, ?, ?, 1, 1, '1', null, 2, '台词', '林晚说：别走。', '林晚', true, '林晚说：别走。', now(), now())
            """, tenantId, projectId, taskId, issueId);

        mockMvc.perform(get("/api/script-review/projects/%d/versions/%d/history".formatted(projectId, secondVersionId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.selectedVersion.id", is(secondVersionId.intValue())))
            .andExpect(jsonPath("$.data.versions", hasSize(2)))
            .andExpect(jsonPath("$.data.roundHistory", hasSize(1)))
            .andExpect(jsonPath("$.data.issueMappings", hasSize(1)))
            .andExpect(jsonPath("$.data.diffLines", hasSize(org.hamcrest.Matchers.greaterThanOrEqualTo(1))));
    }

    @Test
    void marksAiCallLogAsBusinessFailureWhenReviewOutputIsInvalidJson() throws Exception {
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
        Long taskId = readLong(createdTask, "$.data.businessId");

        seedTextModel();
        when(aiInvocationService.invokeText(any())).thenReturn(new AiInvocationResult<>(
            AiCapability.TEXT,
            AiBusinessScene.SCRIPT_REVIEW.code(),
            new AiTextResponse("not-json", "req-review", 0, 0, 0, 12L, java.util.Map.of()),
            "not-json",
            777L,
            "req-review",
            1L,
            1L,
            "OpenAI",
            0,
            0,
            0,
            12L,
            "SUCCESS",
            null,
            null
        ));

        reviewWorkbenchService.executeTask(taskId);

        verify(aiInvocationService).markBusinessFailure(777L, com.antshorttv.common.ErrorCode.AI_RESPONSE_INVALID, "剧本审核结果不是有效 JSON。");
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
        when(aiInvocationService.invokeText(any())).thenReturn(invalidReviewInvocation(881L));

        aiExecutionWorker.run(executionId);

        assertThat(jdbcTemplate.queryForObject(
            "select status from ai_execution_task where id = ?", String.class, executionId
        )).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject(
            "select ai_call_log_id from ai_execution_attempt where execution_id = ?",
            Long.class, executionId
        )).isEqualTo(881L);
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
        when(aiInvocationService.invokeText(any())).thenReturn(
            invalidReviewInvocation(882L),
            invalidReviewInvocation(883L),
            invalidReviewInvocation(884L)
        );

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
        )).isEqualByComparingTo("1");
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
        when(aiInvocationService.invokeText(any())).thenThrow(
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
    void supportsCancelRetryResolveAndRollbackLifecycle() throws Exception {
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

        jdbcTemplate.update("""
            insert into review_issue
              (tenant_id, project_id, task_id, script_version_id, round_no, issue_no, dimension, severity, title, position_json,
               excerpt, problem, evidence_json, suggestion, status, related_issue_no, manually_resolved, created_at, updated_at)
            values
              (?, ?, ?, ?, 1, 'R1-01', '台词合理性', 'P1', '人名混乱', '{"episode":1,"scene":"1"}',
               '第1集', '称呼不一致', '["称呼混乱"]', '统一称呼', 'persists', null, false, now(), now())
            """, tenantId, projectId, retryTaskId, versionId);
        Long issueId = jdbcTemplate.queryForObject("select max(id) from review_issue where task_id = ?", Long.class, retryTaskId);

        mockMvc.perform(post("/api/script-review/issues/%d/resolve".formatted(issueId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"人工确认\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.manuallyResolved", is(true)));

        Integer eventCount = jdbcTemplate.queryForObject("select count(*) from review_issue_event where issue_id = ?", Integer.class, issueId);
        org.assertj.core.api.Assertions.assertThat(eventCount).isEqualTo(1);

        mockMvc.perform(post("/api/script-review/projects/%d/rollback".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"versionId":%d}
                    """.formatted(versionId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.versionNo", is(2)));
    }

    @Test
    void sendsSharedGlobalIndexForMultiEpisodeQuickAndDeepReviewRuns() throws Exception {
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
        when(aiInvocationService.invokeText(any())).thenReturn(new AiInvocationResult<>(
            AiCapability.TEXT,
            AiBusinessScene.SCRIPT_REVIEW.code(),
            new AiTextResponse("{\"overallScore\":90,\"overallConclusion\":\"PASS\",\"summary\":\"ok\",\"issues\":[]}", "req-review", 0, 0, 0, 12L, java.util.Map.of()),
            "{\"overallScore\":90,\"overallConclusion\":\"PASS\",\"summary\":\"ok\",\"issues\":[]}",
            778L,
            "req-review",
            1L,
            1L,
            "OpenAI",
            0,
            0,
            0,
            12L,
            "SUCCESS",
            null,
            null
        ));

        MvcResult quickTask = mockMvc.perform(post("/api/script-review/projects/%d/tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"versionId":%d,"reviewMode":"QUICK","selectedDimensions":["台词合理性"],"reviewScopeType":"ALL","reviewScope":{}}
                    """.formatted(versionId)))
            .andExpect(status().isAccepted())
            .andReturn();
        Long quickTaskId = readLong(quickTask, "$.data.businessId");

        MvcResult deepTask = mockMvc.perform(post("/api/script-review/projects/%d/tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"versionId":%d,"reviewMode":"DEEP","selectedDimensions":["人物关系一致性"],"reviewScopeType":"ALL","reviewScope":{}}
                    """.formatted(versionId)))
            .andExpect(status().isAccepted())
            .andReturn();
        Long deepTaskId = readLong(deepTask, "$.data.businessId");

        reviewWorkbenchService.executeTask(quickTaskId);
        reviewWorkbenchService.executeTask(deepTaskId);

        ArgumentCaptor<AiInvocationRequest> captor = ArgumentCaptor.forClass(AiInvocationRequest.class);
        verify(aiInvocationService, times(2)).invokeText(captor.capture());
        AiInvocationRequest quickRequest = captor.getAllValues().get(0);
        AiInvocationRequest deepRequest = captor.getAllValues().get(1);
        org.assertj.core.api.Assertions.assertThat(quickRequest.templateVariables().get("globalIndex").toString())
            .contains("episodeCount=2");
        org.assertj.core.api.Assertions.assertThat(deepRequest.templateVariables().get("globalIndex").toString())
            .contains("episodeCount=2");
        org.assertj.core.api.Assertions.assertThat(quickRequest.templateVariables().get("reviewMode")).isEqualTo("QUICK");
        org.assertj.core.api.Assertions.assertThat(deepRequest.templateVariables().get("reviewMode")).isEqualTo("DEEP");
    }

    private void seedTextModel() {
        Long providerId = jdbcTemplate.queryForObject("select id from ai_provider where code = 'OpenAI' limit 1", Long.class);
        jdbcTemplate.update("update ai_model set is_default = false where service_type = 'TEXT'");
        jdbcTemplate.update("delete from ai_model where code = 'review-test-text-model'");
        jdbcTemplate.update("""
            insert into ai_model
              (provider_id, code, name, model_code, service_type, status, is_default, sort, created_at, updated_at)
            values
              (?, 'review-test-text-model', 'Review Test Text Model', 'review-test-text-model', 'TEXT', 'ENABLED', true, 999, now(), now())
            """, providerId);
    }

    private AiInvocationResult<AiTextResponse> successfulReviewInvocation(Long callLogId) {
        String json = "{\"overallScore\":90,\"overallConclusion\":\"PASS\",\"summary\":\"ok\",\"issues\":[]}";
        return new AiInvocationResult<>(
            AiCapability.TEXT,
            AiBusinessScene.SCRIPT_REVIEW.code(),
            new AiTextResponse(json, "req-review", 0, 0, 0, 12L, java.util.Map.of()),
            json,
            callLogId,
            "req-review",
            1L,
            1L,
            "OpenAI",
            0,
            0,
            0,
            12L,
            "SUCCESS",
            null,
            null
        );
    }

    private AiInvocationResult<AiTextResponse> invalidReviewInvocation(Long callLogId) {
        return new AiInvocationResult<>(
            AiCapability.TEXT,
            AiBusinessScene.SCRIPT_REVIEW.code(),
            new AiTextResponse("not-json", "req-review-invalid", 0, 0, 0, 12L, java.util.Map.of()),
            "not-json",
            callLogId,
            "req-review-invalid",
            1L,
            1L,
            "OpenAI",
            0,
            0,
            0,
            12L,
            "SUCCESS",
            null,
            null
        );
    }

    private void grantTeamPoints(Long tenantId, int amount) {
        jdbcTemplate.update("""
            insert into team_point_account
              (tenant_id, balance, total_granted, total_consumed, created_at, updated_at)
            values (?, ?, ?, 0, now(), now())
            """, tenantId, amount, amount);
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
