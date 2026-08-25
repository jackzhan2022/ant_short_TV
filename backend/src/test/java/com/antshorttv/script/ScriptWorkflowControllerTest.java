package com.antshorttv.script;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.antshorttv.user.UserEntity;
import com.antshorttv.user.UserMapper;
import com.antshorttv.execution.AiExecutionWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ScriptWorkflowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AiExecutionWorker aiExecutionWorker;

    @Autowired
    private ScriptAnalysisExecutionCoordinator scriptAnalysisExecutionCoordinator;

    @Test
    void returnsEmptyWorkspaceForProjectWithoutScript() throws Exception {
        String token = registerUser("13800013001", "Script Owner");
        Long tenantId = createTenant(token, "剧本工作流团队");
        Long ownerId = userIdByMobile("13800013001");
        Long projectId = createProject(token, tenantId, ownerId, "归来后我执掌豪门", "SCRIPT_WORKFLOW_EMPTY");

        mockMvc.perform(get("/api/projects/%d/script-workspace".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.projectId", is(projectId.intValue())))
            .andExpect(jsonPath("$.data.script", is((Object) null)))
            .andExpect(jsonPath("$.data.characters", hasSize(0)))
            .andExpect(jsonPath("$.data.storyboards", hasSize(0)));
    }

    @Test
    void returnsParsedEpisodesInScriptWorkspace() throws Exception {
        String token = registerUser("13800013020", "Episode Owner");
        Long tenantId = createTenant(token, "分集团队");
        Long ownerId = userIdByMobile("13800013020");
        Long projectId = createProject(token, tenantId, ownerId, "分集项目", "SCRIPT_EPISODES");

        mockMvc.perform(put("/api/projects/%d/scripts/current".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title":"分集项目",
                      "content":"第1集：开端\\n主角回家。\\n\\nEP02: 冲突\\n对手出现。",
                      "status":"DRAFT"
                    }
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/projects/%d/script-workspace".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.episodes", hasSize(2)))
            .andExpect(jsonPath("$.data.episodes[0].episodeNo", is(1)))
            .andExpect(jsonPath("$.data.episodes[0].title", is("第1集：开端")))
            .andExpect(jsonPath("$.data.episodes[0].content", is("主角回家。")))
            .andExpect(jsonPath("$.data.episodes[1].episodeNo", is(2)))
            .andExpect(jsonPath("$.data.episodes[1].content", is("对手出现。")));
    }

    @Test
    void createsAndReanalyzesInitialScriptTasks() throws Exception {
        String token = registerUser("13800013021", "Analysis Owner");
        Long tenantId = createTenant(token, "分析任务团队");
        Long ownerId = userIdByMobile("13800013021");
        createDefaultTextService(tenantId, ownerId);
        grantTeamPoints(tenantId, 20);
        Long projectId = createProject(
            token,
            tenantId,
            ownerId,
            "分析项目",
            "SCRIPT_ANALYSIS_TASKS",
            "第1集\n主角回到故乡。\n第2集\n新的冲突出现。"
        );

        MvcResult initial = mockMvc.perform(get("/api/projects/%d/script-analysis/current".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("PENDING")))
            .andExpect(jsonPath("$.data.stages", hasSize(4)))
            .andReturn();
        Long initialTaskId = readLong(initial, "$.data.id");

        MvcResult submitted = mockMvc.perform(post("/api/projects/%d/script-analysis/current/reanalyze".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.status", is("PENDING")))
            .andExpect(jsonPath("$.data.businessType", is("SCRIPT_ANALYSIS_TASK")))
            .andExpect(jsonPath("$.data.businessId").isNumber())
            .andReturn();
        Long executionId = readLong(submitted, "$.data.id");
        Long analysisTaskId = readLong(submitted, "$.data.businessId");

        aiExecutionWorker.run(executionId);

        assertThat(jdbcTemplate.queryForMap(
            "select status, error_code, error_message, "
                + "(select count(*) from ai_execution_attempt where execution_id = ?) attempt_count "
                + "from ai_execution_task where id = ?",
            executionId,
            executionId
        )).containsEntry("STATUS", "SUCCEEDED");
        assertThat(jdbcTemplate.queryForObject(
            "select status from script_analysis_task where id = ?", String.class, analysisTaskId
        )).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from script_analysis_result where task_id = ? and execution_id = ? and status = 'SUCCEEDED'",
            Integer.class, analysisTaskId, executionId
        )).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from ai_call_log where execution_id = ? and attempt_id is not null",
            Integer.class, executionId
        )).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from ai_call_log where execution_id = ? and phase like 'EPISODE_SUMMARY:%'",
            Integer.class, executionId
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
            "select count(distinct attempt_id) from ai_call_log where execution_id = ? and phase like 'EPISODE_SUMMARY:%'",
            Integer.class, executionId
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "select count(distinct idempotency_key) from ai_call_log where execution_id = ? and phase like 'EPISODE_SUMMARY:%'",
            Integer.class, executionId
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
            "select settled_points from ai_point_reservation where execution_id = ?",
            java.math.BigDecimal.class, executionId
        )).isEqualByComparingTo("4");
        assertThat(jdbcTemplate.queryForObject(
            "select released_points from ai_point_reservation where execution_id = ?",
            java.math.BigDecimal.class, executionId
        )).isEqualByComparingTo("1");

        MvcResult current = mockMvc.perform(get("/api/projects/%d/script-analysis/current".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stages", hasSize(4)))
            .andReturn();
        Long latestTaskId = readLong(current, "$.data.id");
        org.assertj.core.api.Assertions.assertThat(latestTaskId).isNotEqualTo(initialTaskId);
    }

    @Test
    void automaticAnalysisWaitsForPointsAndResumesTheSameDomainTask() throws Exception {
        String token = registerUser("13800013031", "Deferred Analysis Owner");
        Long tenantId = createTenant(token, "延迟分析团队");
        Long ownerId = userIdByMobile("13800013031");
        createDefaultTextService(tenantId, ownerId);
        Long projectId = createProject(
            token, tenantId, ownerId, "零积分分析项目", "SCRIPT_ANALYSIS_DEFERRED",
            "第1集\n主角回到故乡。"
        );
        Long taskId = jdbcTemplate.queryForObject(
            "select id from script_analysis_task where tenant_id = ? and project_id = ?",
            Long.class, tenantId, projectId
        );

        scriptAnalysisExecutionCoordinator.scheduleAutomaticAnalysis();

        Map<String, Object> waiting = jdbcTemplate.queryForMap(
            "select status, execution_id, error_code from script_analysis_task where id = ?", taskId
        );
        assertThat(waiting).containsEntry("STATUS", "PENDING");
        assertThat(waiting.get("execution_id")).isNull();
        assertThat(waiting.get("error_code")).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from ai_execution_task where business_type = 'SCRIPT_ANALYSIS_TASK' and business_id = ?",
            Integer.class, taskId
        )).isZero();

        grantTeamPoints(tenantId, 10);
        scriptAnalysisExecutionCoordinator.scheduleAutomaticAnalysis();

        Long executionId = jdbcTemplate.queryForObject(
            "select execution_id from script_analysis_task where id = ?", Long.class, taskId
        );
        assertThat(executionId).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
            "select status from ai_point_reservation where execution_id = ?", String.class, executionId
        )).isEqualTo("RESERVED");
    }

    @Test
    void currentAnalysisIgnoresOlderTasksAfterSavingAnewVersion() throws Exception {
        String token = registerUser("13800013023", "Version Owner");
        Long tenantId = createTenant(token, "版本隔离团队");
        Long ownerId = userIdByMobile("13800013023");
        Long projectId = createProject(
            token,
            tenantId,
            ownerId,
            "版本隔离项目",
            "SCRIPT_VERSION_ISOLATION",
            "第1集\n主角回家。"
        );

        mockMvc.perform(put("/api/projects/%d/scripts/current".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"版本二","content":"第1集\\n新的正文。","status":"DRAFT"}
                    """))
            .andExpect(status().isOk());

        Integer taskCount = jdbcTemplate.queryForObject(
            "select count(*) from script_analysis_task where tenant_id = ? and project_id = ?",
            Integer.class,
            tenantId,
            projectId
        );
        assertThat(taskCount).isEqualTo(1);

        mockMvc.perform(get("/api/projects/%d/script-analysis/current".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", nullValue()));
    }

    @Test
    void retryAnalysisResetsTargetAndLaterStagesWithoutClearingEarlierSuccess() throws Exception {
        String token = registerUser("13800013024", "Retry Owner");
        Long tenantId = createTenant(token, "重试团队");
        Long ownerId = userIdByMobile("13800013024");
        createDefaultTextService(tenantId, ownerId);
        grantTeamPoints(tenantId, 10);
        Long projectId = createProject(
            token,
            tenantId,
            ownerId,
            "重试项目",
            "SCRIPT_RETRY_FLOW",
            "第1集\n主角回家。"
        );

        Long taskId = jdbcTemplate.queryForObject(
            "select id from script_analysis_task where tenant_id = ? and project_id = ? order by id desc limit 1",
            Long.class,
            tenantId,
            projectId
        );
        Long stage1Id = stageId(taskId, "GLOBAL_UNDERSTANDING");
        Long stage2Id = stageId(taskId, "EPISODE_SPLITTING");
        Long stage3Id = stageId(taskId, "EPISODE_SUMMARY");
        Long stage4Id = stageId(taskId, "CHARACTER_SCENE_RECOGNITION");
        jdbcTemplate.update("""
            update script_analysis_task
               set status = 'FAILED',
                   current_stage = 'EPISODE_SPLITTING',
                   current_action = '分析失败',
                   overall_progress = 25,
                   error_code = 'AI_RESPONSE_INVALID',
                   error_message = 'bad split'
             where id = ?
            """, taskId);
        jdbcTemplate.update("""
            update script_analysis_stage
               set status = 'SUCCEEDED',
                   progress_percent = 100,
                   completed_units = 1,
                   total_units = 1,
                   current_action = '已完成',
                   retryable = false
             where id = ?
            """, stage1Id);
        jdbcTemplate.update("""
            update script_analysis_stage
               set status = 'FAILED',
                   progress_percent = 60,
                   current_action = '出错',
                   error_code = 'AI_RESPONSE_INVALID',
                   error_message = 'bad split',
                   retryable = true
             where id = ?
            """, stage2Id);
        jdbcTemplate.update("""
            update script_analysis_stage
               set status = 'SUCCEEDED',
                   progress_percent = 100,
                   completed_units = 1,
                   total_units = 1,
                   current_action = '已完成',
                   retryable = false
             where id = ?
            """, stage3Id);
        jdbcTemplate.update("""
            update script_analysis_stage
               set status = 'SUCCEEDED',
                   progress_percent = 100,
                   completed_units = 1,
                   total_units = 1,
                   current_action = '已完成',
                   retryable = false
             where id = ?
            """, stage4Id);
        jdbcTemplate.update("""
            insert into script_analysis_result
              (task_id, stage_id, result_type, schema_version, status, raw_response, normalized_json, provider_request_id, ai_call_log_id, duration_ms, error_code, error_message, retryable, created_at, updated_at)
            values (?, ?, 'GLOBAL_UNDERSTANDING', 'v1', 'SUCCEEDED', ?, ?, 'req-stage1', 701, 33, null, null, false, now(), now())
            """, taskId, stage1Id, "{\"logline\":\"主角回家\"}", "{\"logline\":\"主角回家\"}");

        mockMvc.perform(post("/api/projects/%d/script-analysis/current/retry/EPISODE_SPLITTING".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.status", is("PENDING")))
            .andExpect(jsonPath("$.data.businessType", is("SCRIPT_ANALYSIS_TASK")))
            .andExpect(jsonPath("$.data.businessId", is(taskId.intValue())));
        assertThat(jdbcTemplate.queryForObject(
            "select status from script_analysis_stage where id = ?", String.class, stage1Id
        )).isEqualTo("SUCCEEDED");
        assertThat(jdbcTemplate.queryForObject(
            "select normalized_json from script_analysis_result where stage_id = ? order by id desc limit 1",
            String.class, stage1Id
        )).isEqualTo("{\"logline\":\"主角回家\"}");
    }

    @Test
    void confirmsPendingReviewCharacterFromAnalysisDrafts() throws Exception {
        String token = registerUser("13800013025", "Confirm Owner");
        Long tenantId = createTenant(token, "确认团队");
        Long ownerId = userIdByMobile("13800013025");
        Long projectId = createProject(
            token,
            tenantId,
            ownerId,
            "确认项目",
            "SCRIPT_CONFIRM_FLOW",
            "第1集\n主角回家。"
        );

        jdbcTemplate.update("""
            insert into character_asset
              (tenant_id, project_id, name, role_type, gender, age_range, identity, personality, appearance, relationship_text, plot_function, prompt, status, merge_target_id, created_by, created_at, updated_at)
            values (?, ?, '林晚', 'SUPPORTING', null, null, null, null, null, null, null, null, 'PENDING_REVIEW', null, ?, now(), now())
            """, tenantId, projectId, ownerId);
        Long characterId = jdbcTemplate.queryForObject(
            "select id from character_asset where tenant_id = ? and project_id = ? and name = '林晚' order by id desc limit 1",
            Long.class,
            tenantId,
            projectId
        );

        mockMvc.perform(put("/api/projects/%d/script-elements/CHARACTER/%d/confirm".formatted(projectId, characterId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.characters[0].name", is("林晚")))
            .andExpect(jsonPath("$.data.characters[0].status", is("CONFIRMED")));
    }

    @Test
    void currentAnalysisCannotReadProjectFromAnotherTenant() throws Exception {
        String ownerToken = registerUser("13800013026", "Tenant Owner");
        Long ownerTenantId = createTenant(ownerToken, "租户A");
        Long ownerId = userIdByMobile("13800013026");
        Long projectId = createProject(
            ownerToken,
            ownerTenantId,
            ownerId,
            "租户隔离项目",
            "SCRIPT_TENANT_ISOLATION",
            "第1集\n主角回家。"
        );

        String otherToken = registerUser("13800013027", "Other Tenant Owner");
        Long otherTenantId = createTenant(otherToken, "租户B");

        mockMvc.perform(get("/api/projects/%d/script-analysis/current".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(otherToken))
                .header("X-Tenant-Id", otherTenantId))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode", is("PROJECT_ACCESS_DENIED")));
    }

    @Test
    void exposesAnalysisResultMetadataFromCurrentAnalysis() throws Exception {
        String token = registerUser("13800013022", "Metadata Owner");
        Long tenantId = createTenant(token, "元数据团队");
        Long ownerId = userIdByMobile("13800013022");
        Long projectId = createProject(token, tenantId, ownerId, "元数据项目", "SCRIPT_ANALYSIS_METADATA");

        mockMvc.perform(put("/api/projects/%d/scripts/current".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"元数据剧本","content":"第1集\\n主角回家。","status":"DRAFT"}
                    """))
            .andExpect(status().isOk());

        Long scriptId = jdbcTemplate.queryForObject(
            "select id from script where tenant_id = ? and project_id = ? and deleted_at is null",
            Long.class,
            tenantId,
            projectId
        );
        Long versionId = jdbcTemplate.queryForObject(
            "select id from script_version where tenant_id = ? and project_id = ? order by id desc limit 1",
            Long.class,
            tenantId,
            projectId
        );
        jdbcTemplate.update("""
            insert into script_analysis_task
              (tenant_id, project_id, script_id, script_version_id, workflow_code, status, current_stage, overall_progress, current_action, idempotency_key, created_by, created_at, updated_at)
            values (?, ?, ?, ?, 'SCRIPT_INITIAL_ANALYSIS', 'RUNNING', 'GLOBAL_UNDERSTANDING', 25, '正在理解剧情主线、人物关系和核心冲突', 'SCRIPT_INITIAL_ANALYSIS:%d', ?, now(), now())
            """.formatted(versionId), tenantId, projectId, scriptId, versionId, ownerId);
        Long taskId = jdbcTemplate.queryForObject(
            "select id from script_analysis_task where tenant_id = ? and project_id = ? and script_version_id = ? order by id desc limit 1",
            Long.class,
            tenantId,
            projectId,
            versionId
        );
        jdbcTemplate.update("""
            insert into script_analysis_stage
              (task_id, stage_code, stage_order, status, progress_percent, completed_units, total_units, current_action, attempt_no, retryable, created_at, updated_at)
            values (?, 'GLOBAL_UNDERSTANDING', 1, 'SUCCEEDED', 100, 1, 1, '已完成', 1, false, now(), now())
            """, taskId);
        Long stageId = jdbcTemplate.queryForObject(
            "select id from script_analysis_stage where task_id = ? and stage_code = 'GLOBAL_UNDERSTANDING'",
            Long.class,
            taskId
        );
        jdbcTemplate.update("""
            insert into script_analysis_result
              (task_id, stage_id, result_type, schema_version, status, raw_response, normalized_json, provider_request_id, ai_call_log_id, duration_ms, error_code, error_message, retryable, created_at, updated_at)
            values (?, ?, 'GLOBAL_UNDERSTANDING', 'v1', 'SUCCEEDED', ?, ?, ?, ?, ?, null, null, false, now(), now())
            """, taskId, stageId, "{\"logline\":\"主角回家\"}", "{\"logline\":\"主角回家\"}", "req-metadata", 9901L, 1234L);

        mockMvc.perform(get("/api/projects/%d/script-analysis/current".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.stages[0].providerRequestId", is("req-metadata")))
            .andExpect(jsonPath("$.data.stages[0].aiCallLogId", is(9901)))
            .andExpect(jsonPath("$.data.stages[0].durationMs", is(1234)))
            .andExpect(jsonPath("$.data.stages[0].resultRetryable", is(false)));
    }

    @Test
    void generatesScriptDraftAndWorkspaceData() throws Exception {
        String token = registerUser("13800013002", "Generate Owner");
        Long tenantId = createTenant(token, "AI剧本团队");
        createDefaultTextService(tenantId, userIdByMobile("13800013002"));
        grantTeamPoints(tenantId, 5);
        Long ownerId = userIdByMobile("13800013002");
        Long projectId = createProject(token, tenantId, ownerId, "豪门逆袭", "SCRIPT_WORKFLOW_GENERATE");

        Long executionId = submitAndRun(
            token, tenantId, projectId, "/scripts/ai-generate",
            """
                {
                  "title":"归来千金",
                  "storyIdea":"落魄千金重回豪门后发现当年的陷害另有隐情",
                  "genre":"逆袭",
                  "episodeCount":12,
                  "duration":90,
                  "styleRequirement":"强冲突"
                }
                """,
            "generate-workspace-data"
        );
        assertExecutionSucceeded(executionId);

        mockMvc.perform(get("/api/projects/%d/script-workspace".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.script.title", is("归来千金")))
            .andExpect(jsonPath("$.data.versions[0].sourceType", is("AI_GENERATE")));
    }

    @Test
    void acceptsIdempotentDurableScriptGeneration() throws Exception {
        String token = registerUser("13800013028", "Async Script Owner");
        Long tenantId = createTenant(token, "异步剧本团队");
        Long ownerId = userIdByMobile("13800013028");
        createDefaultTextService(tenantId, ownerId);
        grantTeamPoints(tenantId, 5);
        Long projectId = createProject(token, tenantId, ownerId, "异步剧本", "SCRIPT_ASYNC_GENERATE");
        String body = """
            {"title":"异步生成","storyIdea":"任务提交后由持久化执行器生成剧本","genre":"悬疑"}
            """;

        MvcResult first = mockMvc.perform(post("/api/projects/%d/scripts/ai-generate".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .header("Idempotency-Key", "script-generate-contract")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.status", is("PENDING")))
            .andExpect(jsonPath("$.data.businessType", is("SCRIPT_AI_OPERATION")))
            .andReturn();
        Long executionId = readLong(first, "$.data.id");

        mockMvc.perform(post("/api/projects/%d/scripts/ai-generate".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .header("Idempotency-Key", "script-generate-contract")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.id", is(executionId.intValue())));

        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from script_ai_operation where tenant_id = ? and idempotency_key = ?",
            Integer.class,
            tenantId,
            "script-generate-contract"
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from ai_execution_task where tenant_id = ? and client_idempotency_key = ?",
            Integer.class,
            tenantId,
            "script-generate-contract"
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from ai_point_reservation where execution_id = ?",
            Integer.class,
            executionId
        )).isEqualTo(1);

        aiExecutionWorker.run(executionId);

        Map<String, Object> executionState = jdbcTemplate.queryForMap(
            "select status, error_code, error_message, (select count(*) from ai_execution_attempt where execution_id = ?) attempt_count from ai_execution_task where id = ?",
            executionId,
            executionId
        );
        assertThat(executionState).containsEntry("STATUS", "SUCCEEDED");
        assertThat(jdbcTemplate.queryForObject(
            "select status from script_ai_operation where execution_id = ?",
            String.class,
            executionId
        )).isEqualTo("SUCCEEDED");
        mockMvc.perform(get("/api/projects/%d/script-workspace".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.script.title", is("异步生成")))
            .andExpect(jsonPath("$.data.versions[0].sourceType", is("AI_GENERATE")));
        assertThat(jdbcTemplate.queryForObject(
            "select status from ai_point_reservation where execution_id = ?",
            String.class,
            executionId
        )).isEqualTo("SETTLED");
    }

    @Test
    void durableScriptOperationsPreserveExistingDomainResults() throws Exception {
        String token = registerUser("13800013029", "Async Workflow Owner");
        Long tenantId = createTenant(token, "异步剧本全链路团队");
        Long ownerId = userIdByMobile("13800013029");
        createDefaultTextService(tenantId, ownerId);
        grantTeamPoints(tenantId, 20);
        Long projectId = createProject(
            token,
            tenantId,
            ownerId,
            "异步全链路",
            "SCRIPT_ASYNC_WORKFLOW",
            "第1集\n主角在雨夜回到林家老宅门口，拿出股权协议。"
        );

        Long rewriteExecutionId = submitAndRun(
            token, tenantId, projectId, "/scripts/ai-rewrite",
            "{\"rewriteType\":\"冲突增强\",\"requirement\":\"强化开场\",\"outputLength\":\"KEEP\"}",
            "async-rewrite"
        );
        assertExecutionSucceeded(rewriteExecutionId);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from script_version where execution_id = ? and source_type = 'AI_REWRITE'",
            Integer.class,
            rewriteExecutionId
        )).isEqualTo(1);

        Long extractExecutionId = submitAndRun(
            token, tenantId, projectId, "/scripts/ai-extract-elements",
            "{\"elementType\":\"ALL\"}",
            "async-extract"
        );
        assertExecutionSucceeded(extractExecutionId);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from character_asset where tenant_id = ? and project_id = ? and deleted_at is null",
            Integer.class,
            tenantId,
            projectId
        )).isGreaterThan(0);

        Long storyboardExecutionId = submitAndRun(
            token, tenantId, projectId, "/storyboards/ai-breakdown",
            "{\"scope\":\"FULL\"}",
            "async-storyboard"
        );
        assertExecutionSucceeded(storyboardExecutionId);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from storyboard where tenant_id = ? and project_id = ? and deleted_at is null",
            Integer.class,
            tenantId,
            projectId
        )).isEqualTo(3);

        Long promptExecutionId = submitAndRun(
            token, tenantId, projectId, "/prompts/ai-generate",
            "{\"targetType\":\"ALL\"}",
            "async-prompts"
        );
        assertExecutionSucceeded(promptExecutionId);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from character_asset where tenant_id = ? and project_id = ? and prompt like '角色定妆提示词：%'",
            Integer.class,
            tenantId,
            projectId
        )).isGreaterThan(0);
    }

    private Long submitAndRun(
        String token,
        Long tenantId,
        Long projectId,
        String path,
        String body,
        String idempotencyKey
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/%d%s".formatted(projectId, path))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.status", is("PENDING")))
            .andReturn();
        Long executionId = readLong(result, "$.data.id");
        aiExecutionWorker.run(executionId);
        return executionId;
    }

    private void assertExecutionSucceeded(Long executionId) {
        assertThat(jdbcTemplate.queryForObject(
            "select status from ai_execution_task where id = ?",
            String.class,
            executionId
        )).isEqualTo("SUCCEEDED");
        assertThat(jdbcTemplate.queryForObject(
            "select status from script_ai_operation where execution_id = ?",
            String.class,
            executionId
        )).isEqualTo("SUCCEEDED");
    }

    @Test
    void generatesScriptWithGlobalTextServiceFromAnotherTenant() throws Exception {
        String configToken = registerUser("13800013012", "Global Config Owner");
        Long configTenantId = createTenant(configToken, "全局配置来源团队");
        createDefaultTextService(configTenantId, userIdByMobile("13800013012"));

        String projectToken = registerUser("13800013013", "Global Project Owner");
        Long projectTenantId = createTenant(projectToken, "全局配置使用团队");
        grantTeamPoints(projectTenantId, 5);
        Long ownerId = userIdByMobile("13800013013");
        Long projectId = createProject(projectToken, projectTenantId, ownerId, "跨团队剧本", "SCRIPT_GLOBAL_AI_CONFIG");

        Long executionId = submitAndRun(
            projectToken, projectTenantId, projectId, "/scripts/ai-generate",
            """
                {
                  "title":"全局服务剧本",
                  "storyIdea":"团队未单独配置 AI 服务时仍可生成剧本",
                  "genre":"逆袭"
                }
                """,
            "global-service-generate"
        );
        assertExecutionSucceeded(executionId);

        mockMvc.perform(get("/api/projects/%d/script-workspace".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(projectToken))
                .header("X-Tenant-Id", projectTenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.script.title", is("全局服务剧本")))
            .andExpect(jsonPath("$.data.versions", hasSize(1)));
    }

    @Test
    void aiScriptGenerationConsumesTeamPoint() throws Exception {
        String token = registerUser("13800013014", "Point Script Owner");
        Long tenantId = createTenant(token, "剧本积分团队");
        Long ownerId = userIdByMobile("13800013014");
        createDefaultTextService(tenantId, ownerId);
        grantTeamPoints(tenantId, 2);
        Long projectId = createProject(token, tenantId, ownerId, "积分剧本", "SCRIPT_POINTS_CONSUME");

        Long executionId = submitAndRun(
            token, tenantId, projectId, "/scripts/ai-generate",
            """
                {
                  "title":"积分生成剧本",
                  "storyIdea":"每次AI生成剧本都要扣减团队积分",
                  "genre":"逆袭"
                }
                """,
            "point-script-generate"
        );
        assertExecutionSucceeded(executionId);

        Integer balance = jdbcTemplate.queryForObject(
            "select balance from team_point_account where tenant_id = ?",
            Integer.class,
            tenantId
        );
        Integer settledReservationCount = jdbcTemplate.queryForObject(
            "select count(*) from ai_point_reservation where tenant_id = ? and execution_id = ? and status = 'SETTLED'",
            Integer.class,
            tenantId,
            executionId
        );
        org.assertj.core.api.Assertions.assertThat(balance).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(settledReservationCount).isEqualTo(1);
    }

    @Test
    void aiScriptGenerationRequiresTeamPoint() throws Exception {
        String token = registerUser("13800013015", "No Point Owner");
        Long tenantId = createTenant(token, "无积分剧本团队");
        Long ownerId = userIdByMobile("13800013015");
        createDefaultTextService(tenantId, ownerId);
        Long projectId = createProject(token, tenantId, ownerId, "无积分剧本", "SCRIPT_POINTS_REQUIRED");

        mockMvc.perform(post("/api/projects/%d/scripts/ai-generate".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title":"不能生成",
                      "storyIdea":"团队没有积分时不能发起AI生成剧本",
                      "genre":"逆袭"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode", is("TEAM_POINTS_INSUFFICIENT")));
    }

    @Test
    void extractsCharactersAndScenesFromCurrentScript() throws Exception {
        String token = registerUser("13800013003", "Extract Owner");
        Long tenantId = createTenant(token, "AI元素团队");
        createDefaultTextService(tenantId, userIdByMobile("13800013003"));
        grantTeamPoints(tenantId, 5);
        Long ownerId = userIdByMobile("13800013003");
        Long projectId = createProject(token, tenantId, ownerId, "豪门元素", "SCRIPT_WORKFLOW_EXTRACT");

        submitAndRun(
            token, tenantId, projectId, "/scripts/ai-generate",
            """
                {
                  "title":"归来千金",
                  "storyIdea":"落魄千金重回豪门，雨夜在林家老宅门口拿出股权协议",
                  "genre":"逆袭"
                }
                """,
            "extract-source-generate"
        );

        submitAndRun(
            token, tenantId, projectId, "/scripts/ai-extract-elements",
            "{\"elementType\":\"CHARACTER\"}",
            "extract-characters"
        );
        mockMvc.perform(get("/api/projects/%d/script-workspace".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.characters", hasSize(2)))
            .andExpect(jsonPath("$.data.characters[0].name", is("主角")));

        submitAndRun(
            token, tenantId, projectId, "/scripts/ai-extract-elements",
            "{\"elementType\":\"SCENE\"}",
            "extract-scenes"
        );
        mockMvc.perform(get("/api/projects/%d/script-workspace".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.scenes", hasSize(2)))
            .andExpect(jsonPath("$.data.scenes[0].name", is("林家老宅门口")));
    }

    @Test
    void completesTextWorkflowEditingStoryboardPromptsAndLogs() throws Exception {
        String token = registerUser("13800013004", "Workflow Owner");
        Long tenantId = createTenant(token, "AI文本全链路团队");
        Long ownerId = userIdByMobile("13800013004");
        createDefaultTextService(tenantId, ownerId);
        grantTeamPoints(tenantId, 20);
        Long projectId = createProject(token, tenantId, ownerId, "全链路短剧", "SCRIPT_WORKFLOW_FULL");

        submitAndRun(
            token, tenantId, projectId, "/scripts/ai-generate",
            "{\"storyIdea\":\"落魄千金雨夜回归豪门\",\"genre\":\"逆袭\",\"episodeCount\":12,\"duration\":90}",
            "full-workflow-generate"
        );
        MvcResult generatedWorkspace = mockMvc.perform(get("/api/projects/%d/script-workspace".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.versions", hasSize(1)))
            .andReturn();
        Long versionId = readLong(generatedWorkspace, "$.data.versions[0].id");

        submitAndRun(
            token, tenantId, projectId, "/scripts/ai-rewrite",
            "{\"rewriteType\":\"冲突增强\",\"requirement\":\"强化前三秒钩子\",\"outputLength\":\"KEEP\"}",
            "full-workflow-rewrite"
        );
        mockMvc.perform(get("/api/projects/%d/script-workspace".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.versions", hasSize(2)))
            .andExpect(jsonPath("$.data.versions[0].sourceType", is("AI_REWRITE")));

        mockMvc.perform(put("/api/projects/%d/scripts/current".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"手工整理版","content":"第一集：主角在雨夜回到林家老宅门口。","status":"CONFIRMED"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.script.title", is("手工整理版")))
            .andExpect(jsonPath("$.data.script.status", is("CONFIRMED")));

        mockMvc.perform(put("/api/projects/%d/scripts/versions/%d/apply".formatted(projectId, versionId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.script.currentVersionId", is(versionId.intValue())));

        submitAndRun(
            token, tenantId, projectId, "/scripts/ai-extract-elements",
            "{\"elementType\":\"ALL\"}",
            "full-workflow-extract"
        );
        MvcResult extractResult = mockMvc.perform(get("/api/projects/%d/script-workspace".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.characters", hasSize(2)))
            .andExpect(jsonPath("$.data.scenes", hasSize(2)))
            .andExpect(jsonPath("$.data.props", hasSize(1)))
            .andReturn();
        Long characterId = readLong(extractResult, "$.data.characters[0].id");

        mockMvc.perform(put("/api/projects/%d/script-elements/CHARACTER/%d".formatted(projectId, characterId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"林晚","roleType":"LEAD","gender":"女","ageRange":"25-30","identity":"回归千金","personality":["冷静","果断"],"appearance":"黑色风衣","prompt":"林晚角色定妆照","status":"CONFIRMED"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.characters[0].name", is("林晚")));

        submitAndRun(
            token, tenantId, projectId, "/storyboards/ai-breakdown",
            "{\"scope\":\"FULL\"}",
            "full-workflow-storyboard"
        );
        mockMvc.perform(get("/api/projects/%d/script-workspace".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.storyboards", hasSize(3)))
            .andExpect(jsonPath("$.data.storyboards[0].imagePrompt", Matchers.containsString("首帧")));

        MvcResult storyboardResult = mockMvc.perform(post("/api/projects/%d/storyboards".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"episodeNo":1,"shotNo":9,"shotType":"特写","visualDescription":"股权协议签名特写","characters":"林晚","scene":"宴会厅","dialogue":"这一次轮到我了。","durationSeconds":4,"imagePrompt":"协议特写首帧","videoPrompt":"镜头推进"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.storyboards", hasSize(4)))
            .andReturn();
        Long storyboardId = readLong(storyboardResult, "$.data.storyboards[3].id");

        mockMvc.perform(put("/api/projects/%d/storyboards/%d".formatted(projectId, storyboardId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"episodeNo":1,"shotNo":10,"shotType":"近景","visualDescription":"林晚抬眼看向众人","characters":"林晚","scene":"宴会厅","dialogue":"我回来了。","durationSeconds":5,"imagePrompt":"林晚近景首帧","videoPrompt":"慢慢推近","status":"CONFIRMED"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.storyboards[3].shotNo", is(10)));

        submitAndRun(
            token, tenantId, projectId, "/prompts/ai-generate",
            "{\"targetType\":\"ALL\"}",
            "full-workflow-prompts"
        );
        mockMvc.perform(get("/api/projects/%d/script-workspace".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.characters[0].prompt", Matchers.containsString("角色")))
            .andExpect(jsonPath("$.data.storyboards[0].videoPrompt", Matchers.containsString("竖屏短剧")));

        Integer callCount = jdbcTemplate.queryForObject(
            "select count(*) from ai_call_log where tenant_id = ? and business_scene in ('script_generate','script_rewrite','character_extract','scene_extract','prop_extract','storyboard_breakdown','prompt_generate')",
            Integer.class,
            tenantId
        );
        org.assertj.core.api.Assertions.assertThat(callCount).isGreaterThanOrEqualTo(7);
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
                    {"name":"%s","type":"STUDIO","description":"剧本工作流测试"}
                    """.formatted(name)))
            .andExpect(status().isOk())
            .andReturn();
        return readLong(result, "$.data.id");
    }

    private Long createProject(String token, Long tenantId, Long ownerId, String name, String code) throws Exception {
        return createProject(token, tenantId, ownerId, name, code, null);
    }

    private Long createProject(
        String token,
        Long tenantId,
        Long ownerId,
        String name,
        String code,
        String initialScriptContent
    ) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("code", code);
        body.put("description", "剧本工作流项目");
        body.put("ownerId", ownerId);
        if (initialScriptContent != null) {
            body.put("initialScriptContent", initialScriptContent);
        }
        MvcResult result = mockMvc.perform(post("/api/projects")
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andReturn();
        return readLong(result, "$.data.id");
    }

    private Long userIdByMobile(String mobile) {
        UserEntity user = userMapper.selectByMobile(mobile);
        return user.getId();
    }

    private Long readLong(MvcResult result, String path) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return value.longValue();
    }

    private Long stageId(Long taskId, String stageCode) {
        return jdbcTemplate.queryForObject(
            "select id from script_analysis_stage where task_id = ? and stage_code = ?",
            Long.class,
            taskId,
            stageCode
        );
    }

    private void createDefaultTextService(Long tenantId, Long userId) {
        Long providerId = jdbcTemplate.queryForObject(
            "select id from ai_provider where code = 'OpenAI' limit 1", Long.class
        );
        jdbcTemplate.update("update ai_provider set status = 'ENABLED' where id = ?", providerId);
        jdbcTemplate.update("update ai_provider_config set api_key_cipher = 'test-key', base_url = 'https://example.com/v1', status = 'ENABLED' where provider_id = ?", providerId);
        String modelCode = "test-text-" + tenantId;
        jdbcTemplate.update("update ai_model set is_default = false where service_type = 'TEXT'");
        jdbcTemplate.update("delete from ai_model_capability where model_id in (select id from ai_model where code = ?)", modelCode);
        jdbcTemplate.update("delete from ai_model where code = ?", modelCode);
        jdbcTemplate.update("""
            insert into ai_model
              (provider_id, code, name, model_code, service_type, status, is_default, sort, created_at, updated_at)
            values (?, ?, 'Test Text Model', 'gpt-4.1-mini', 'TEXT', 'ENABLED', true, 100, now(), now())
            """, providerId, modelCode);
        Long modelId = jdbcTemplate.queryForObject("select id from ai_model where code = ?", Long.class, modelCode);
        jdbcTemplate.update("""
            insert into ai_model_capability (model_id, capability, status, created_at, updated_at)
            values (?, 'TEXT_GENERATION', 'ENABLED', now(), now())
            """, modelId);
    }

    private void grantTeamPoints(Long tenantId, int amount) {
        jdbcTemplate.update("""
            insert into team_point_account
              (tenant_id, balance, total_granted, total_consumed, created_at, updated_at)
            values (?, ?, ?, 0, now(), now())
            """, tenantId, amount, amount);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
