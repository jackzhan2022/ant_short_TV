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
import java.util.List;
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

    @Autowired private AutoStoryboardEventRepository autoStoryboardEvents;
    @Autowired private ScriptAiOperationService scriptAiOperations;
    @Autowired private com.antshorttv.rbac.RbacPermissionService rbacPermissions;
    @Autowired private com.antshorttv.workflowagent.agent.WorkflowAgentRepository workflowAgents;
    @org.springframework.boot.test.mock.mockito.SpyBean private com.antshorttv.ai.XiongXiongAiAdapter controlledProvider;

    @Test
    void automaticStoryboardResumesFundsAndRecoversSubmissionWithoutDoubleReservation() throws Exception {
        String token = registerUser("13800013991", "Automatic Storyboard Owner");
        Long tenantId = createTenant(token, "自动分镜恢复团队");
        Long ownerId = userIdByMobile("13800013991");
        createDefaultTextService(tenantId, ownerId);
        Long projectId = createProject(token, tenantId, ownerId, "恢复项目", "AUTO_RECOVERY",
            "第1集\n主角走进客厅。");
        jdbcTemplate.update("update project set visual_style='真人写实' where id=?",projectId);
        mockMvc.perform(put("/api/projects/%d/scripts/current".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id",tenantId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"恢复项目\",\"content\":\"第1集\\n主角走进客厅。\",\"status\":\"DRAFT\"}"))
            .andExpect(status().isOk());
        var episode = jdbcTemplate.queryForMap("select id,script_id,stable_key,content_fingerprint from script_episode where project_id=?", projectId);
        Long episodeId = ((Number) episode.get("id")).longValue();
        Long scriptId = ((Number) episode.get("script_id")).longValue();
        jdbcTemplate.update("""
            insert into script_episode_asset_analysis
              (tenant_id,project_id,script_id,episode_id,schema_version,content_fingerprint,
               content_json,created_by,updated_by,created_at,updated_at)
            values (?,?,?,?,1,?,'{}',?,?,now(),now())
            """, tenantId, projectId, scriptId, episodeId, episode.get("content_fingerprint"), ownerId, ownerId);
        Long analysisId = jdbcTemplate.queryForObject("select id from script_episode_asset_analysis where episode_id=?", Long.class, episodeId);
        long eventId = autoStoryboardEvents.recordPending(new AutoStoryboardEventRepository.Draft(
            tenantId,projectId,scriptId,episodeId,String.valueOf(episode.get("stable_key")),
            String.valueOf(episode.get("content_fingerprint")),analysisId,null,ownerId));
        var dispatcher = new AutoStoryboardDispatchService(autoStoryboardEvents, scriptAiOperations, rbacPermissions, jdbcTemplate, 120);
        dispatcher.dispatchOne();
        assertThat(jdbcTemplate.queryForObject("select status from episode_auto_storyboard_event where id=?", String.class, eventId)).isEqualTo("BLOCKED_FUNDS");
        assertThat(jdbcTemplate.queryForObject("select count(*) from ai_point_reservation where tenant_id=?", Integer.class, tenantId)).isZero();
        grantTeamPoints(tenantId, 3);
        jdbcTemplate.update("update episode_auto_storyboard_event set next_attempt_at=now() where id=?", eventId);
        var crashing = org.mockito.Mockito.mock(AutoStoryboardEventRepository.class,
            org.mockito.AdditionalAnswers.delegatesTo(autoStoryboardEvents));
        org.mockito.Mockito.doThrow(new AssertionError("crash after committed operation"))
            .when(crashing).markDispatched(org.mockito.ArgumentMatchers.eq(eventId), org.mockito.ArgumentMatchers.anyLong());
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            new AutoStoryboardDispatchService(crashing, scriptAiOperations, rbacPermissions, jdbcTemplate,120).dispatchOne())
            .isInstanceOf(AssertionError.class);
        jdbcTemplate.update("update episode_auto_storyboard_event set updated_at=dateadd('SECOND',-300,now()) where id=?", eventId);
        dispatcher.dispatchOne();
        assertThat(jdbcTemplate.queryForObject("select status from episode_auto_storyboard_event where id=?", String.class,eventId)).isEqualTo("DISPATCHED");
        assertThat(jdbcTemplate.queryForObject("select count(*) from ai_execution_task where tenant_id=? and scene='storyboard_breakdown'",Integer.class,tenantId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from ai_point_reservation where tenant_id=?",Integer.class,tenantId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from script_episode_asset_analysis where episode_id=?",Integer.class,episodeId)).isEqualTo(1);
        Long executionId=jdbcTemplate.queryForObject("select id from ai_execution_task where tenant_id=? and scene='storyboard_breakdown'",Long.class,tenantId);
        Map<String,Object> successfulAssets=jdbcTemplate.queryForMap("select * from script_episode_asset_analysis where id=?",analysisId);
        configureStoryboardProviderWithOneFailure(tenantId,ownerId,episodeId);
        aiExecutionWorker.run(executionId);
        assertThat(jdbcTemplate.queryForObject("select status from ai_execution_attempt where execution_id=? order by id limit 1",String.class,executionId)).isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForMap("select * from script_episode_asset_analysis where id=?",analysisId)).isEqualTo(successfulAssets);
        // Let the actual worker reclaim the scheduled retry, without rewriting execution state in SQL.
        long retryDeadline=System.nanoTime()+java.time.Duration.ofSeconds(15).toNanos();
        while ("PENDING".equals(jdbcTemplate.queryForObject("select status from ai_execution_task where id=?",String.class,executionId))
            && System.nanoTime()<retryDeadline) {
            Thread.sleep(100);
            aiExecutionWorker.run(executionId);
        }
        assertExecutionSucceeded(executionId);
        assertThat(jdbcTemplate.queryForList("select status from ai_execution_attempt where execution_id=? order by id",String.class,executionId))
            .containsExactly("FAILED","SUCCEEDED");
        assertThat(jdbcTemplate.queryForMap("select * from script_episode_asset_analysis where id=?",analysisId)).isEqualTo(successfulAssets);
        assertThat(jdbcTemplate.queryForObject("select count(*) from storyboard where episode_id=? and generated_by_run_id is not null and deleted_at is null",Integer.class,episodeId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from ai_point_reservation where tenant_id=?",Integer.class,tenantId)).isEqualTo(1);
        mockMvc.perform(post("/api/projects/%d/storyboards".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id",tenantId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"episodeNo\":1,\"shotNo\":1,\"visualDescription\":\"人工分镜\"}"))
            .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("select episode_id from storyboard where project_id=? and generated_by_run_id is null and deleted_at is null",Long.class,projectId)).isEqualTo(episodeId);
        jdbcTemplate.update("update episode_auto_storyboard_event set status='PENDING',next_attempt_at=null where id=?",eventId);
        dispatcher.dispatchOne();
        assertThat(jdbcTemplate.queryForObject("select status from episode_auto_storyboard_event where id=?",String.class,eventId)).isEqualTo("PROTECTED");
        assertThat(jdbcTemplate.queryForObject("select count(*) from ai_point_reservation where tenant_id=?",Integer.class,tenantId)).isEqualTo(1);
    }

    @Test
    void scopedAssetHttpValidationAndPreflightNeverCreateBillableWork() throws Exception {
        String token=registerUser("13800013992","Scoped Owner");
        Long tenantId=createTenant(token,"范围边界团队");
        Long ownerId=userIdByMobile("13800013992");
        createDefaultTextService(tenantId,ownerId);
        Long projectId=createProject(token,tenantId,ownerId,"边界项目","SCOPED_HTTP","第1集\n主角归来。");
        String outsider=registerUser("13800013993","Outsider");
        Map<String,Integer> before=billableCounts(tenantId);
        for (String body : List.of("{\"targetType\":\"UNKNOWN\",\"promptPolicy\":\"FILL_EMPTY\"}",
            "{\"targetType\":\"ALL\",\"promptPolicy\":\"UNKNOWN\"}")) {
            mockMvc.perform(post("/api/projects/%d/asset-reextraction".formatted(projectId))
                    .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                    .header("X-Tenant-Id",tenantId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
            assertThat(billableCounts(tenantId)).isEqualTo(before);
        }
        mockMvc.perform(post("/api/projects/%d/asset-reextraction".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(outsider))
                .header("X-Tenant-Id",tenantId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetType\":\"ALL\",\"promptPolicy\":\"FILL_EMPTY\"}"))
            .andExpect(status().isForbidden());
        assertThat(billableCounts(tenantId)).isEqualTo(before);
        for (String scope : List.of("ALL","CHARACTER","SCENE","PROP")) {
            mockMvc.perform(get("/api/projects/%d/asset-reextraction/preflight".formatted(projectId))
                    .param("targetType",scope).with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                    .header("X-Tenant-Id",tenantId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.targetType",is(scope)));
            assertThat(billableCounts(tenantId)).isEqualTo(before);
        }
    }

    private Map<String,Integer> billableCounts(Long tenantId) {
        Map<String,Integer> result=new LinkedHashMap<>();
        for (String table : List.of("script_ai_operation","ai_execution_task","ai_point_reservation"))
            result.put(table,jdbcTemplate.queryForObject("select count(*) from "+table+" where tenant_id=?",Integer.class,tenantId));
        return result;
    }

    @Test
    void returnsEmptyWorkspaceForProjectWithoutScript() throws Exception {
        String token = registerUser("13800013001", "Script Owner");
        Long tenantId = createTenant(token, "剧本工作流团队");
        Long ownerId = userIdByMobile("13800013001");
        Long projectId = createProject(token, tenantId, ownerId, "归来后我执掌豪门", "SCRIPT_WORKFLOW_EMPTY");

        mockMvc.perform(get("/api/projects/%d/script-page-workspace".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.projectId", is(projectId.intValue())))
            .andExpect(jsonPath("$.data.script", is((Object) null)))
            .andExpect(jsonPath("$.data.characters").doesNotExist())
            .andExpect(jsonPath("$.data.storyboards").doesNotExist());

        mockMvc.perform(get("/api/projects/%d/asset-settings-summary".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.characters", hasSize(0)))
            .andExpect(jsonPath("$.data.scenes", hasSize(0)))
            .andExpect(jsonPath("$.data.props", hasSize(0)));
    }

    @Test
    void returnsLightweightAssetSettingsWorkspaceWithoutScriptFields() throws Exception {
        String token = registerUser("13800013002", "Asset Settings Owner");
        Long tenantId = createTenant(token, "资产设定团队");
        Long ownerId = userIdByMobile("13800013002");
        Long projectId = createProject(token, tenantId, ownerId, "资产设定项目", "ASSET_SETTINGS");
        jdbcTemplate.update("""
            insert into character_asset
              (tenant_id, project_id, name, role_type, status, created_by, created_at, updated_at)
            values (?, ?, '林晚', 'SUPPORTING', 'CONFIRMED', ?, now(), now())
            """, tenantId, projectId, ownerId);

        mockMvc.perform(get("/api/projects/%d/asset-settings-summary".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.projectId", is(projectId.intValue())))
            .andExpect(jsonPath("$.data.characters", hasSize(1)))
            .andExpect(jsonPath("$.data.characters[0].name", is("林晚")))
            .andExpect(jsonPath("$.data.characters[0].visual").doesNotExist())
            .andExpect(jsonPath("$.data.scenes", hasSize(0)))
            .andExpect(jsonPath("$.data.props", hasSize(0)))
            .andExpect(jsonPath("$.data.script").doesNotExist())
            .andExpect(jsonPath("$.data.versions").doesNotExist())
            .andExpect(jsonPath("$.data.episodes").doesNotExist())
            .andExpect(jsonPath("$.data.storyboards").doesNotExist())
            .andExpect(jsonPath("$.data.analysis").doesNotExist());
    }

    @Test
    void returnsFocusedPageWorkspacesWithoutUnrelatedCollections() throws Exception {
        String token = registerUser("13800013003", "Focused Workspace Owner");
        Long tenantId = createTenant(token, "按需加载团队");
        Long ownerId = userIdByMobile("13800013003");
        Long projectId = createProject(token, tenantId, ownerId, "按需加载项目", "FOCUSED_WORKSPACE");
        mockMvc.perform(put("/api/projects/%d/scripts/current".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"按需加载项目\",\"content\":\"第1集：开始\\n正文\",\"status\":\"DRAFT\"}"))
            .andExpect(status().isOk());
        Long episodeId = jdbcTemplate.queryForObject(
            "select id from script_episode where tenant_id = ? and project_id = ? order by id desc limit 1",
            Long.class, tenantId, projectId);

        mockMvc.perform(get("/api/projects/%d/script-page-workspace".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.script.content").doesNotExist())
            .andExpect(jsonPath("$.data.versions[0].content").doesNotExist())
            .andExpect(jsonPath("$.data.episodes[0].content").doesNotExist())
            .andExpect(jsonPath("$.data.characters").doesNotExist())
            .andExpect(jsonPath("$.data.storyboards").doesNotExist());

        mockMvc.perform(get("/api/projects/%d/script-content".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content", is("第1集：开始\n正文")));

        mockMvc.perform(get("/api/projects/%d/script-episodes/%d".formatted(projectId, episodeId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content", is("正文")));

        mockMvc.perform(get("/api/projects/%d/asset-settings-summary".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.characters", hasSize(0)));

        mockMvc.perform(get("/api/projects/%d/storyboard-workspace?episodeNo=1&current=1&pageSize=999".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.current", is(1)))
            .andExpect(jsonPath("$.data.pageSize", is(100)))
            .andExpect(jsonPath("$.data.storyboards", hasSize(0)));
    }

    @Test
    void returnsFocusedDetailsAndBoundedStoryboardPages() throws Exception {
        String token = registerUser("13800013034", "Focused Detail Owner");
        Long tenantId = createTenant(token, "按需详情团队");
        Long ownerId = userIdByMobile("13800013034");
        Long projectId = createProject(
            token, tenantId, ownerId, "按需详情项目", "FOCUSED_DETAIL",
            "第1集：开端\n主角进入房间。"
        );
        Long scriptId = jdbcTemplate.queryForObject(
            "select id from script where tenant_id = ? and project_id = ?", Long.class, tenantId, projectId);
        Long versionId = jdbcTemplate.queryForObject(
            "select id from script_version where tenant_id = ? and project_id = ? order by id desc limit 1",
            Long.class, tenantId, projectId);
        jdbcTemplate.update("""
            insert into character_asset
              (tenant_id, project_id, name, role_type, status, created_by, created_at, updated_at)
            values (?, ?, '林晚', 'LEAD', 'CONFIRMED', ?, now(), now())
            """, tenantId, projectId, ownerId);
        Long characterId = jdbcTemplate.queryForObject(
            "select id from character_asset where tenant_id = ? and project_id = ?", Long.class, tenantId, projectId);
        jdbcTemplate.update("""
            insert into ai_image_result
              (tenant_id, project_id, task_id, target_type, target_id, image_url, storage_path, thumbnail_url,
               is_selected, status, created_at, updated_at)
            values (?, ?, 1, 'VISUAL_VARIANT', ?, '/images/original.png', 'images/original.png',
                    '/images/thumbnail.png', true, 'ACTIVE', now(), now())
            """, tenantId, projectId, characterId);
        Long imageResultId = jdbcTemplate.queryForObject(
            "select id from ai_image_result where tenant_id = ? and project_id = ?", Long.class, tenantId, projectId);
        jdbcTemplate.update("update character_asset set main_image_result_id = ? where id = ?",
            imageResultId, characterId);
        jdbcTemplate.update("""
            insert into asset_visual_variant
              (tenant_id, project_id, asset_type, asset_id, name, source_type, generation_status,
               current_image_result_id, current_image_url, is_primary, created_by, created_at, updated_at)
            values (?, ?, 'CHARACTER', ?, '默认形象', 'AI_GENERATED', 'COMPLETED', ?,
                    '/images/original.png', true, ?, now(), now())
            """, tenantId, projectId, characterId, imageResultId, ownerId);
        for (int shotNo = 1; shotNo <= 3; shotNo++) {
            jdbcTemplate.update("""
                insert into storyboard
                  (tenant_id, project_id, script_id, episode_no, shot_no, visual_description,
                   status, created_by, created_at, updated_at)
                values (?, ?, ?, 1, ?, ?, 'CONFIRMED', ?, now(), now())
                """, tenantId, projectId, scriptId, shotNo, "镜头" + shotNo, ownerId);
        }

        mockMvc.perform(get("/api/projects/%d/script-versions/%d".formatted(projectId, versionId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id", is(versionId.intValue())))
            .andExpect(jsonPath("$.data.content", is("第1集：开端\n主角进入房间。")));
        mockMvc.perform(get("/api/projects/%d/asset-settings-summary".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.characters[0].name", is("林晚")))
            .andExpect(jsonPath("$.data.characters[0].mainImageThumbnailUrl",
                is("/api/projects/%d/ai-image-results/%d/thumbnail".formatted(projectId, imageResultId))))
            .andExpect(jsonPath("$.data.characters[0].visual").doesNotExist());
        mockMvc.perform(get("/api/projects/%d/script-elements/CHARACTER/%d/visual-workspace".formatted(projectId, characterId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.variantCount", is(1)))
            .andExpect(jsonPath("$.data.variants[0].currentImageUrl", is("/images/original.png")))
            .andExpect(jsonPath("$.data.variants[0].currentImageThumbnailUrl", is("/images/thumbnail.png")));
        mockMvc.perform(get("/api/projects/%d/storyboard-workspace?episodeNo=1&current=1&pageSize=2".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total", is(3)))
            .andExpect(jsonPath("$.data.current", is(1)))
            .andExpect(jsonPath("$.data.pageSize", is(2)))
            .andExpect(jsonPath("$.data.storyboards", hasSize(2)))
            .andExpect(jsonPath("$.data.storyboards[0].shotNo", is(1)));
        mockMvc.perform(get("/api/projects/%d/storyboard-workspace?episodeNo=1&current=2&pageSize=2".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.storyboards", hasSize(1)))
            .andExpect(jsonPath("$.data.storyboards[0].shotNo", is(3)));
    }

    @Test
    void focusedEndpointsRejectCrossTenantAndInvalidResources() throws Exception {
        String ownerToken = registerUser("13800013035", "Focused Access Owner");
        Long ownerTenantId = createTenant(ownerToken, "按需隔离团队A");
        Long ownerId = userIdByMobile("13800013035");
        Long projectId = createProject(
            ownerToken, ownerTenantId, ownerId, "按需隔离项目", "FOCUSED_ACCESS",
            "第1集\n隔离正文。"
        );
        Long versionId = jdbcTemplate.queryForObject(
            "select id from script_version where tenant_id = ? and project_id = ? order by id desc limit 1",
            Long.class, ownerTenantId, projectId);
        String otherToken = registerUser("13800013036", "Focused Access Other");
        Long otherTenantId = createTenant(otherToken, "按需隔离团队B");

        for (String path : List.of(
            "/script-page-workspace",
            "/script-content",
            "/asset-settings-summary",
            "/storyboard-workspace",
            "/script-versions/" + versionId,
            "/script-episodes/999999999"
        )) {
            mockMvc.perform(get("/api/projects/%d%s".formatted(projectId, path))
                    .with(com.antshorttv.support.SessionTestSupport.authenticated(otherToken))
                    .header("X-Tenant-Id", otherTenantId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", is("PROJECT_ACCESS_DENIED")));
        }
        mockMvc.perform(get("/api/projects/%d/script-elements/UNKNOWN/1/visual-workspace".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(ownerToken))
                .header("X-Tenant-Id", ownerTenantId))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/projects/%d/script-versions/999999999/".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(ownerToken))
                .header("X-Tenant-Id", ownerTenantId))
            .andExpect(status().isNotFound());
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

        mockMvc.perform(get("/api/projects/%d/script-page-workspace".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.episodes", hasSize(2)))
            .andExpect(jsonPath("$.data.episodes[0].episodeId").isNumber())
            .andExpect(jsonPath("$.data.episodes[0].episodeNo", is(1)))
            .andExpect(jsonPath("$.data.episodes[0].title", is("第1集：开端")))
            .andExpect(jsonPath("$.data.episodes[0].content").doesNotExist())
            .andExpect(jsonPath("$.data.episodes[1].episodeNo", is(2)))
            .andExpect(jsonPath("$.data.episodes[1].episodeId").isNumber())
            .andExpect(jsonPath("$.data.episodes[1].content").doesNotExist());
    }

    @Test
    void createsAndReanalyzesInitialScriptTasks() throws Exception {
        String token = registerUser("13800013021", "Analysis Owner");
        Long tenantId = createTenant(token, "分析任务团队");
        Long ownerId = userIdByMobile("13800013021");
        createDefaultTextService(tenantId, ownerId);
        configureAnalysisAgents(tenantId, ownerId);
        grantTeamPoints(tenantId, 20);
        Long projectId = createProject(
            token,
            tenantId,
            ownerId,
            "分析项目",
            "SCRIPT_ANALYSIS_TASKS",
            "第1集\n主角回到故乡。\n第2集\n主角面对新的冲突。"
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
        awaitExecutionTerminal(executionId);

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
            "select count(*) from script_episode where tenant_id = ? and project_id = ? and retired_at is null",
            Integer.class, tenantId, projectId
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from script_analysis_stage where task_id=? and status='SUCCEEDED'",
            Integer.class,analysisTaskId)).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from script_global_understanding where tenant_id=? and project_id=? and last_agent_run_id is not null",
            Integer.class,tenantId,projectId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from script_episode_summary where tenant_id=? and project_id=? and generated_by_run_id is not null",
            Integer.class,tenantId,projectId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from script_episode_asset_analysis where tenant_id=? and project_id=? and generated_by_run_id is not null",
            Integer.class,tenantId,projectId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from character_asset where tenant_id=? and project_id=? and name='主角' and source='AI' and deleted_at is null",
            Integer.class,tenantId,projectId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from ai_workflow_agent_run where task_id=? and tenant_id=? and project_id=? and status='SUCCESS'",
            Integer.class,analysisTaskId,tenantId,projectId)).isEqualTo(6);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from ai_call_log where execution_id=? and attempt_id is not null",
            Integer.class,executionId)).isEqualTo(6);
        assertThat(jdbcTemplate.queryForObject(
            "select count(distinct idempotency_key) from ai_call_log where execution_id=?",
            Integer.class,executionId)).isEqualTo(6);
        assertThat(jdbcTemplate.queryForObject(
            "select settled_points from ai_point_reservation where execution_id=?",
            java.math.BigDecimal.class,executionId)).isPositive();

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
    void assetSettingsWorkspaceCannotReadProjectFromAnotherTenant() throws Exception {
        String ownerToken = registerUser("13800013028", "Asset Tenant Owner");
        Long ownerTenantId = createTenant(ownerToken, "资产租户A");
        Long ownerId = userIdByMobile("13800013028");
        Long projectId = createProject(
            ownerToken,
            ownerTenantId,
            ownerId,
            "资产隔离项目",
            "ASSET_SETTINGS_TENANT_ISOLATION",
            "第1集\n主角回家。"
        );

        String otherToken = registerUser("13800013029", "Asset Other Tenant Owner");
        Long otherTenantId = createTenant(otherToken, "资产租户B");

        mockMvc.perform(get("/api/projects/%d/asset-settings-summary".formatted(projectId))
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

        mockMvc.perform(get("/api/projects/%d/script-page-workspace".formatted(projectId))
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
        mockMvc.perform(get("/api/projects/%d/script-page-workspace".formatted(projectId))
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

        seedFormalAssets(tenantId, projectId, ownerId);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from character_asset where tenant_id = ? and project_id = ? and deleted_at is null",
            Integer.class,
            tenantId,
            projectId
        )).isGreaterThan(0);

        configurePromptBackfillProvider(tenantId, projectId);
        Long promptExecutionId = submitAndRun(
            token, tenantId, projectId, "/prompts/ai-generate",
            "{\"targetType\":\"ALL\"}",
            "async-prompts"
        );
        assertExecutionSucceeded(promptExecutionId);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from character_asset where tenant_id = ? and project_id = ? and prompt like '受控补全%'",
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
        awaitExecutionTerminal(executionId);
        return executionId;
    }

    private void awaitExecutionTerminal(Long executionId) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            String status = jdbcTemplate.queryForObject(
                "select status from ai_execution_task where id = ?",
                String.class,
                executionId
            );
            if (!"PENDING".equals(status) && !"RUNNING".equals(status)) {
                return;
            }
            Thread.sleep(20);
        }
    }

    private void assertExecutionSucceeded(Long executionId) {
        assertThat(jdbcTemplate.queryForMap(
            "select status,error_code,error_message from ai_execution_task where id = ?", executionId
        )).containsEntry("status", "SUCCEEDED");
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

        mockMvc.perform(get("/api/projects/%d/script-page-workspace".formatted(projectId))
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
        var billing = jdbcTemplate.queryForMap("""
            select cost_price_version_id, point_price_version_id, usage_cost_status
              from ai_execution_task where id = ?
            """, executionId);
        org.assertj.core.api.Assertions.assertThat(billing.get("cost_price_version_id")).isNotNull();
        org.assertj.core.api.Assertions.assertThat(billing.get("point_price_version_id")).isNotNull();
        org.assertj.core.api.Assertions.assertThat(billing.get("usage_cost_status")).isEqualTo("PRICED");
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForList(
            "select metric from ai_usage_line where execution_id = ? order by id",
            String.class,
            executionId
        )).containsExactly("CALL");
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
            "select count(*) from ai_usage_cost_line where execution_id = ? and pricing_status = 'PRICED'",
            Integer.class,
            executionId
        )).isEqualTo(1);
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
        MvcResult generatedWorkspace = mockMvc.perform(get("/api/projects/%d/script-page-workspace".formatted(projectId))
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
        mockMvc.perform(get("/api/projects/%d/script-page-workspace".formatted(projectId))
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
            .andExpect(jsonPath("$.data").value(nullValue()));
        mockMvc.perform(get("/api/projects/%d/script-page-workspace".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.script.title", is("手工整理版")))
            .andExpect(jsonPath("$.data.script.status", is("CONFIRMED")));

        mockMvc.perform(put("/api/projects/%d/scripts/versions/%d/apply".formatted(projectId, versionId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(nullValue()));
        mockMvc.perform(get("/api/projects/%d/script-page-workspace".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.script.currentVersionId", is(versionId.intValue())));

        seedFormalAssets(tenantId, projectId, ownerId);
        MvcResult extractResult = mockMvc.perform(get("/api/projects/%d/asset-settings-summary".formatted(projectId))
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
            .andExpect(jsonPath("$.data").value(nullValue()));
        mockMvc.perform(get("/api/projects/%d/asset-settings-summary".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.characters[0].name", is("林晚")));

        mockMvc.perform(post("/api/projects/%d/storyboards".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"episodeNo":1,"shotNo":9,"shotType":"特写","visualDescription":"股权协议签名特写","characters":"林晚","scene":"宴会厅","dialogue":"这一次轮到我了。","durationSeconds":4,"imagePrompt":"协议特写首帧","videoPrompt":"镜头推进"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(nullValue()));
        MvcResult storyboardResult = mockMvc.perform(get("/api/projects/%d/storyboard-workspace?episodeNo=1".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.storyboards", hasSize(1))).andReturn();
        Long storyboardId = readLong(storyboardResult, "$.data.storyboards[0].id");

        mockMvc.perform(put("/api/projects/%d/storyboards/%d".formatted(projectId, storyboardId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"episodeNo":1,"shotNo":10,"shotType":"近景","visualDescription":"林晚抬眼看向众人","characters":"林晚","scene":"宴会厅","dialogue":"我回来了。","durationSeconds":5,"imagePrompt":"林晚近景首帧","videoPrompt":"慢慢推近","status":"CONFIRMED"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").value(nullValue()));
        mockMvc.perform(get("/api/projects/%d/storyboard-workspace?episodeNo=1".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.storyboards[0].shotNo", is(10)));

        configurePromptBackfillProvider(tenantId, projectId);
        Long promptExecutionId = submitAndRun(
            token, tenantId, projectId, "/prompts/ai-generate",
            "{\"targetType\":\"ALL\"}",
            "full-workflow-prompts"
        );
        assertExecutionSucceeded(promptExecutionId);
        mockMvc.perform(get("/api/projects/%d/asset-settings-summary".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.characters[?(@.id == %d)].prompt".formatted(characterId), Matchers.contains("林晚角色定妆照")));
        assertThat(jdbcTemplate.queryForObject("select video_prompt from storyboard where id = ?",
            String.class, storyboardId)).isEqualTo("慢慢推近");

        Integer callCount = jdbcTemplate.queryForObject(
            "select count(*) from ai_call_log where tenant_id = ? and business_scene in ('script_generate','script_rewrite','prompt_generate')",
            Integer.class,
            tenantId
        );
        org.assertj.core.api.Assertions.assertThat(callCount).isGreaterThanOrEqualTo(3);
    }

    @Test
    void storyboardBreakdownRequiresOwnedActiveEpisodeAndIsIdempotent() throws Exception {
        String token = registerUser("13800013032", "Storyboard Scope Owner");
        Long tenantId = createTenant(token, "分镜范围团队");
        Long ownerId = userIdByMobile("13800013032");
        createDefaultTextService(tenantId, ownerId);
        grantTeamPoints(tenantId, 5);
        Long projectId = createProject(
            token, tenantId, ownerId, "分镜范围项目", "STORYBOARD_SCOPE",
            "第1集\n主角推开房门。"
        );
        mockMvc.perform(put("/api/projects/%d/scripts/current".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"分镜范围剧本","content":"第1集\\n主角推开房门。","status":"CONFIRMED"}
                    """))
            .andExpect(status().isOk());
        Long episodeId = jdbcTemplate.queryForObject(
            "select id from script_episode where tenant_id = ? and project_id = ? and retired_at is null",
            Long.class, tenantId, projectId
        );

        mockMvc.perform(post("/api/projects/%d/storyboards/ai-breakdown".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scope\":\"FULL\"}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/projects/%d/storyboards/ai-breakdown".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"episodeId\":999999999}"))
            .andExpect(status().isBadRequest());

        jdbcTemplate.update("update script_episode set status = 'RETIRED', retired_at = now() where id = ?", episodeId);
        mockMvc.perform(post("/api/projects/%d/storyboards/ai-breakdown".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"episodeId\":%d}".formatted(episodeId)))
            .andExpect(status().isBadRequest());
        jdbcTemplate.update("update script_episode set status = 'ACTIVE', retired_at = null where id = ?", episodeId);

        String body = "{\"episodeId\":%d}".formatted(episodeId);
        MvcResult first = mockMvc.perform(post("/api/projects/%d/storyboards/ai-breakdown".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .header("Idempotency-Key", "storyboard-scope-once")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.status", is("PENDING")))
            .andReturn();
        MvcResult duplicate = mockMvc.perform(post("/api/projects/%d/storyboards/ai-breakdown".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .header("Idempotency-Key", "storyboard-scope-once")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isAccepted())
            .andReturn();

        Long executionId = readLong(first, "$.data.id");
        assertThat(readLong(duplicate, "$.data.id")).isEqualTo(executionId);
        mockMvc.perform(get("/api/tenants/%d/ai-executions/%d".formatted(tenantId, executionId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id", is(executionId.intValue())))
            .andExpect(jsonPath("$.data.status", is("PENDING")))
            .andExpect(jsonPath("$.data.businessType", is("SCRIPT_AI_OPERATION")));
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from script_ai_operation where tenant_id = ? and idempotency_key = ?",
            Integer.class, tenantId, "storyboard-scope-once"
        )).isEqualTo(1);
    }

    @Test
    void storyboardBatchPersistsEpisodeExecutionsAndReturnsIdempotentSummary() throws Exception {
        String token = registerUser("13800013033", "Storyboard Batch Owner");
        Long tenantId = createTenant(token, "分镜批次团队");
        Long ownerId = userIdByMobile("13800013033");
        createDefaultTextService(tenantId, ownerId);
        grantTeamPoints(tenantId, 10);
        Long projectId = createProject(
            token, tenantId, ownerId, "分镜批次项目", "STORYBOARD_BATCH",
            "第1集：开端\n主角推开房门。\n\n第2集：冲突\n对手走进客厅。"
        );
        mockMvc.perform(put("/api/projects/%d/scripts/current".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "title", "分镜批次剧本",
                    "content", "第1集：开端\n主角推开房门。\n\n第2集：冲突\n对手走进客厅。",
                    "status", "CONFIRMED"
                ))))
            .andExpect(status().isOk());
        List<Long> episodeIds = jdbcTemplate.queryForList("""
            select id from script_episode
             where tenant_id = ? and project_id = ? and status = 'ACTIVE' and retired_at is null
             order by episode_no
            """, Long.class, tenantId, projectId);
        String body = objectMapper.writeValueAsString(Map.of("episodeIds", episodeIds));

        MvcResult first = mockMvc.perform(post("/api/projects/%d/storyboard-batches".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .header("Idempotency-Key", "storyboard-batch-once")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.total", is(2)))
            .andExpect(jsonPath("$.data.pending", is(2)))
            .andExpect(jsonPath("$.data.items", hasSize(2)))
            .andReturn();
        Long batchId = readLong(first, "$.data.id");

        mockMvc.perform(post("/api/projects/%d/storyboard-batches".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .header("Idempotency-Key", "storyboard-batch-once")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.id", is(batchId.intValue())));

        mockMvc.perform(get("/api/projects/%d/storyboard-batches/latest".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id", is(batchId.intValue())))
            .andExpect(jsonPath("$.data.businessCallCount", is(0)))
            .andExpect(jsonPath("$.data.technicalRetryCount", is(0)));

        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from storyboard_batch where tenant_id = ? and project_id = ?",
            Integer.class, tenantId, projectId
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from storyboard_batch_item where batch_id = ?",
            Integer.class, batchId
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from ai_execution_task where tenant_id = ? and scene = 'storyboard_breakdown'",
            Integer.class, tenantId
        )).isEqualTo(2);
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

    private void configureStoryboardProviderWithOneFailure(Long tenantId,Long ownerId,Long episodeId) {
        Long modelId=jdbcTemplate.queryForObject("select id from ai_model where code=?",Long.class,"test-text-"+tenantId);
        workflowAgents.create(new com.antshorttv.workflowagent.agent.WorkflowAgentCommand(
            "short-drama-storyboard","Controlled storyboard","Controller retry","Save formal episode storyboards.",modelId,
            new java.math.BigDecimal("0.1"),4096,14,"ENABLED",List.of(),
            com.antshorttv.workflowagent.agent.StoryboardAgentBootstrap.TOOLS),ownerId);
        var failFirst=new java.util.concurrent.atomic.AtomicBoolean(true);
        org.mockito.Mockito.doAnswer(call -> {
            if (failFirst.getAndSet(false)) throw new com.antshorttv.ai.AiGatewayException(
                com.antshorttv.common.ErrorCode.AI_PROVIDER_ERROR,"Controlled first storyboard provider failure");
            var episode=jdbcTemplate.queryForMap("select content,content_fingerprint from script_episode where id=?",episodeId);
            var segments=new com.antshorttv.workflowagent.tool.EpisodeSourceSegmenter().segment(String.valueOf(episode.get("content")));
            var payload=objectMapper.createObjectNode().put("schemaVersion",3)
                .put("episodeFingerprint",String.valueOf(episode.get("content_fingerprint")));
            var board=payload.putArray("storyboards").addObject().put("storyboardNo",1)
                .put("sourceTo",segments.get(segments.size()-1).id()).put("time","日").put("lighting","自然光");
            var materials=board.putObject("usedAssetKeys");
            for (String type : List.of("characters","scenes","props")) materials.putArray(type);
            var shots=board.putArray("shots");
            for (int i=1;i<=4;i++) shots.addObject().put("shotNo",i).put("durationSeconds",i==4?3.8:3.0)
                .put("positioning","客厅内").put("action","镜头展示主角进入客厅");
            return new com.antshorttv.ai.AiTextResponse(null,"controlled-storyboard",10,10,20,1L,
                Map.of("mode","controlled-test"),"tool_calls",false,
                List.of(new com.antshorttv.ai.AiToolCall("save-storyboard","save_episode_storyboards",payload.toString())));
        }).when(controlledProvider).text(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString());
    }

    private void configureAnalysisAgents(Long tenantId, Long ownerId) {
        Long modelId = jdbcTemplate.queryForObject("select id from ai_model where code=?",Long.class,"test-text-"+tenantId);
        Map<String,List<String>> contracts = Map.of(
            "short-drama-global-understanding",List.of("read_current_script","save_global_understanding"),
            "short-drama-episode-splitting",List.of("read_current_script","save_episode_splitting"),
            "short-drama-episode-summary",List.of("read_current_episode","save_episode_summary"),
            "short-drama-asset-recognition",List.of("read_current_episode","save_episode_assets"));
        contracts.forEach((code,tools) -> workflowAgents.create(new com.antshorttv.workflowagent.agent.WorkflowAgentCommand(
            code,code,"Controller controlled Agent","Read then save formal data.",modelId,
            new java.math.BigDecimal("0.1"),4096,8,"ENABLED",List.of(),tools),ownerId));
        org.mockito.Mockito.doAnswer(call -> {
            var request = call.getArgument(3,com.antshorttv.ai.AiTextRequest.class);
            String terminal = request.tools().stream().map(com.antshorttv.ai.AiToolDefinition::code)
                .filter(code -> code.startsWith("save_")).findFirst().orElseThrow();
            String payload = switch (terminal) {
                case "save_global_understanding" -> """
                    {"schemaVersion":1,"content":{"logline":"主角归乡面对冲突","synopsis":"主角回到故乡，面对新的冲突。",
                    "genres":[],"themes":[],"worldSetting":"故乡","coreConflict":"归乡后的冲突","relationships":[],
                    "turningPoints":[],"ending":"面对冲突","endingHook":"冲突如何解决","narrativeStyle":"顺叙","targetAudience":"短剧观众"}}
                    """;
                case "save_episode_splitting" -> """
                    {"schemaVersion":2,"episodes":[{"title":"归乡","startSegmentId":"S0001","endSegmentId":"S0002"},
                    {"title":"冲突","startSegmentId":"S0003","endSegmentId":"S0004"}]}
                    """;
                case "save_episode_summary" -> """
                    {"schemaVersion":1,"summary":"主角面对故乡和新的冲突。","highlights":["主角归乡","冲突出现"],"endingHook":"主角如何应对"}
                    """;
                case "save_episode_assets" -> """
                    {"schemaVersion":1,"characters":[{"localKey":"c1","assetKey":null,"name":"主角","aliases":[],
                    "prompt":"主角清晰定妆照","evidence":"主角"}],"characterLooks":[],"scenes":[],"props":[],"propVariants":[]}
                    """;
                default -> throw new AssertionError("Unexpected controller Agent tool: " + terminal);
            };
            var calls = new java.util.ArrayList<com.antshorttv.ai.AiToolCall>();
            // Episode agents preload their trusted read on the server; script agents read explicitly.
            if (terminal.equals("save_global_understanding") || terminal.equals("save_episode_splitting"))
                calls.add(new com.antshorttv.ai.AiToolCall("read","read_current_script","{}"));
            calls.add(new com.antshorttv.ai.AiToolCall("save",terminal,payload));
            return new com.antshorttv.ai.AiTextResponse(null,"controlled-"+java.util.UUID.randomUUID(),10,10,20,1L,
                Map.of("mode","controlled-test"),"tool_calls",false,calls);
        }).when(controlledProvider).text(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString());
    }

    private void configurePromptBackfillProvider(Long tenantId, Long projectId) {
        org.mockito.Mockito.doAnswer(call -> {
            var result = objectMapper.createObjectNode();
            Map<String,String> tables = Map.of("characters","character_asset","scenes","scene_asset","props","prop_asset");
            tables.forEach((field,table) -> {
                var rows = result.putArray(field);
                jdbcTemplate.queryForList("select id from "+table+" where tenant_id=? and project_id=? and deleted_at is null",Long.class,tenantId,projectId)
                    .forEach(id -> rows.addObject().put("id",id).put("prompt","受控补全提示词"));
            });
            var storyboards = result.putArray("storyboards");
            jdbcTemplate.queryForList("select id from storyboard where tenant_id=? and project_id=? and deleted_at is null",Long.class,tenantId,projectId)
                .forEach(id -> storyboards.addObject().put("id",id).put("imagePrompt","受控补全首帧").put("videoPrompt","受控补全视频"));
            return new com.antshorttv.ai.AiTextResponse(result.toString(),"controlled-prompt",10,10,20,1L,Map.of("mode","controlled-test"));
        }).when(controlledProvider).text(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString());
    }

    private void seedFormalAssets(Long tenantId, Long projectId, Long ownerId) {
        for (String name : List.of("主角", "反派")) {
            jdbcTemplate.update("""
                insert into character_asset
                  (tenant_id, project_id, name, role_type, status, created_by, created_at, updated_at)
                values (?, ?, ?, 'SUPPORTING', 'CONFIRMED', ?, now(), now())
                """, tenantId, projectId, name, ownerId);
        }
        for (String name : List.of("林家老宅门口", "室内场景")) {
            jdbcTemplate.update("""
                insert into scene_asset
                  (tenant_id, project_id, name, scene_type, status, created_by, created_at, updated_at)
                values (?, ?, ?, 'INDOOR', 'CONFIRMED', ?, now(), now())
                """, tenantId, projectId, name, ownerId);
        }
        jdbcTemplate.update("""
            insert into prop_asset
              (tenant_id, project_id, name, prop_type, status, created_by, created_at, updated_at)
            values (?, ?, '股权协议', 'OTHER', 'CONFIRMED', ?, now(), now())
            """, tenantId, projectId, ownerId);
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
            values (?, 'TOOL_CALLING', 'ENABLED', now(), now())
            """, modelId);
        jdbcTemplate.update("""
            insert into ai_model_capability (model_id, capability, status, created_at, updated_at)
            values (?, 'TEXT_GENERATION', 'ENABLED', now(), now())
            """, modelId);
        com.antshorttv.support.ModelBillingTestSupport.publish(
            jdbcTemplate, modelId, "CALL", java.math.BigDecimal.ONE, java.math.BigDecimal.ONE
        );
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
