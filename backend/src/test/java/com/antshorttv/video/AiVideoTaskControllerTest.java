package com.antshorttv.video;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.antshorttv.ai.AiSecretCodec;
import com.antshorttv.user.UserEntity;
import com.antshorttv.user.UserMapper;
import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.antshorttv.execution.AiExecutionPools;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = {
    "ai.video.max-concurrent-per-tenant=1",
    "ai.video.task-timeout-minutes=20",
    "app.public-base-url=https://app.example"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AiVideoTaskControllerTest extends com.antshorttv.support.RegistrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AiVideoTaskService aiVideoTaskService;

    @Autowired
    private AiSecretCodec aiSecretCodec;

    @MockBean
    private AiExecutionPools executionPools;

    @SpyBean
    private AiVideoProviderAdapter providerAdapter;

    @BeforeEach
    void executeReservedVideoSlotsDeterministically() throws Exception {
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return invocation.callRealMethod();
        }).when(providerAdapter).submit(any(), any(), any(), any());
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return invocation.callRealMethod();
        }).when(providerAdapter).poll(any(), any(), any(), any());
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return invocation.callRealMethod();
        }).when(providerAdapter).downloadTo(any(), any());
        when(executionPools.tryAcquire(any())).thenAnswer(invocation -> {
            AiExecutionPools.Slot slot = mock(AiExecutionPools.Slot.class);
            when(slot.canContinue()).thenReturn(true);
            doAnswer(submission -> {
                submission.getArgument(0, Runnable.class).run();
                return null;
            }).when(slot).submit(any(Runnable.class));
            return slot;
        });
    }

    @Test
    void pendingFormalImagesExplainHowToUnblockVideoWithoutSubmittingOrReservingPoints() throws Exception {
        String token = registerUser("13800016023", "Pending Image Owner");
        Long tenantId = createTenant(token, "待生成参考图片团队");
        Long ownerId = userIdByMobile("13800016023");
        Long projectId = createProject(token, tenantId, ownerId, "待生成参考图片项目", "SEEDANCE_PENDING_IMAGE");
        Long modelId = enableSeedanceMini();
        Long storyboardId = createStoryboard(tenantId, projectId, ownerId);
        long variantId = seedStoryboardImageReference(tenantId, projectId, storyboardId, ownerId);
        Long imageId = jdbc.queryForObject(
            "select current_image_result_id from asset_visual_variant where id = ?", Long.class, variantId);
        jdbc.update("update asset_visual_variant set current_image_result_id = null, current_image_url = null where id = ?", variantId);
        jdbc.update("""
            insert into storyboard_asset_reference
              (tenant_id, project_id, storyboard_id, asset_type, asset_id, variant_id,
               reference_role, sort_order, resolution_status, source_type, source_name,
               created_by, created_at, updated_at)
            values (?, ?, ?, 'CHARACTER', 1, ?, 'VISIBLE', 0, 'ASSET_PENDING', 'MANUAL', '林晚', ?, now(), now())
            """, tenantId, projectId, storyboardId, variantId, ownerId);
        String document = jdbc.queryForObject("select prompt_document_json from storyboard where id = ?", String.class, storyboardId)
            .replace("\"displayName\":\"林晚\"", "\"displayName\":\"林晚\",\"bindingSource\":\"FORMAL\"");
        jdbc.update("update storyboard set prompt_document_json = ? where id = ?", document, storyboardId);
        String data = """
            {"storyboardId":%d,"modelId":%d,"prompt":"林晚完成广告","durationSeconds":11,
             "aspectRatio":"9:16","resolution":"720p"}
            """.formatted(storyboardId, modelId);
        String operationUrl = "/api/projects/%d/ai-video-tasks".formatted(projectId);
        String reason = "参考图片尚不可用，请先生成或重新绑定图片，或确认继续纯文生视频。";
        String estimate = "{\"operations\":[{\"url\":\"" + operationUrl + "\",\"data\":" + data + "}]}";
        for (Long pendingVariantId : java.util.Arrays.asList(variantId, null)) {
            jdbc.update("update storyboard_asset_reference set variant_id=? where storyboard_id=?", pendingVariantId, storyboardId);
            jdbc.update("update storyboard set prompt_document_json=? where id=?", pendingVariantId == null
                ? "{\"version\":2,\"nodes\":[{\"type\":\"text\",\"text\":\"林晚完成广告\"}]}" : document, storyboardId);
            for (String url : java.util.List.of("/api/tenants/%d/points/estimate".formatted(tenantId), operationUrl)) {
                mockMvc.perform(post(url)
                        .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(url.equals(operationUrl) ? data : estimate))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode", is("AI_VIDEO_STORYBOARD_FIRST_FRAME_REQUIRED")))
                    .andExpect(jsonPath("$.errorMessage", is(reason)));
            }
        }
        jdbc.update("update storyboard_asset_reference set variant_id=? where storyboard_id=?", variantId, storyboardId);
        jdbc.update("update storyboard set prompt_document_json=? where id=?", document, storyboardId);
        assertThat(jdbc.queryForObject("select count(*) from ai_video_task where tenant_id = ?", Integer.class, tenantId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from ai_point_reservation where tenant_id = ?", Integer.class, tenantId)).isZero();
        grantTeamPoints(tenantId, 5);
        String fallbackData = data.trim().replaceFirst("\\}\\s*$", ",\"textOnly\":true}");
        mockMvc.perform(post("/api/tenants/%d/points/estimate".formatted(tenantId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"operations\":[{\"url\":\"" + operationUrl + "\",\"data\":" + fallbackData + "}]}"))
            .andExpect(status().isOk());
        MvcResult fallback = mockMvc.perform(post(operationUrl)
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId).contentType(MediaType.APPLICATION_JSON).content(fallbackData))
            .andExpect(status().isOk()).andReturn();
        Long fallbackId = readLong(fallback, "$.data.id");
        assertThat(jdbc.queryForObject("select request_snapshot_json from ai_video_task where id=?", String.class, fallbackId))
            .contains("\"textOnly\":true", "\"references\":[]").doesNotContain("图片1");
        assertThat(jdbc.queryForObject("select prompt_document_json from storyboard where id=?", String.class, storyboardId)).isEqualTo(document);
        assertThat(jdbc.queryForObject("select resolution_status from storyboard_asset_reference where storyboard_id=?", String.class, storyboardId))
            .isEqualTo("ASSET_PENDING");
        mockMvc.perform(post(operationUrl + "/" + fallbackId + "/regenerate")
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token)).header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk());
        jdbc.update("update ai_video_task set status='CANCELED' where id=?", fallbackId);
        int taskCount = jdbc.queryForObject("select count(*) from ai_video_task where tenant_id=?", Integer.class, tenantId);
        int reservationCount = jdbc.queryForObject("select count(*) from ai_point_reservation where tenant_id=?", Integer.class, tenantId);
        jdbc.update("update asset_visual_variant set current_image_result_id = ?, current_image_url = 'https://cdn.example/seedance-frame.png' where id = ?", imageId, variantId);
        jdbc.update("update storyboard_asset_reference set resolution_status = 'RESOLVED' where storyboard_id = ?", storyboardId);
        mockMvc.perform(post("/api/tenants/%d/points/estimate".formatted(tenantId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(estimate))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.lines[0].label", is("生成分镜视频")));
        assertThat(jdbc.queryForObject("select count(*) from ai_video_task where tenant_id = ?", Integer.class, tenantId)).isEqualTo(taskCount);
        assertThat(jdbc.queryForObject("select count(*) from ai_point_reservation where tenant_id = ?", Integer.class, tenantId)).isEqualTo(reservationCount);
    }

    @Test
    void estimatesAndCreatesTextOnlySeedanceVideoWithoutAnAssetOrFirstFrame() throws Exception {
        String token = registerUser("13800016024", "Text Only Owner");
        Long tenantId = createTenant(token, "纯文生视频团队");
        Long ownerId = userIdByMobile("13800016024");
        Long projectId = createProject(token, tenantId, ownerId, "纯文生视频项目", "SEEDANCE_TEXT_ONLY");
        Long modelId = enableSeedanceMini();
        Long storyboardId = createStoryboard(tenantId, projectId, ownerId);
        jdbc.update("update storyboard set first_frame_url = null, prompt_document_json = null where id = ?", storyboardId);
        grantTeamPoints(tenantId, 5);
        String data = """
            {"storyboardId":%d,"modelId":%d,"prompt":"王大山在卧室醒来","durationSeconds":11,
             "aspectRatio":"9:16","resolution":"720p"}
            """.formatted(storyboardId, modelId);
        mockMvc.perform(post("/api/tenants/%d/points/estimate".formatted(tenantId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"operations\":[{\"url\":\"/api/projects/" + projectId + "/ai-video-tasks\",\"data\":" + data + "}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.lines[0].label", is("生成分镜视频")));
        assertThat(jdbc.queryForObject("select count(*) from ai_video_task where tenant_id=?", Integer.class, tenantId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from ai_point_reservation where tenant_id=?", Integer.class, tenantId)).isZero();
        for (String invalid : java.util.List.of(data.replace("\"durationSeconds\":11", "\"durationSeconds\":3"),
                data.replace("\"resolution\":\"720p\"", "\"resolution\":\"1080p\""))) {
            mockMvc.perform(post("/api/projects/%d/ai-video-tasks".formatted(projectId))
                    .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                    .header("X-Tenant-Id", tenantId).contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest());
        }
        MvcResult created = mockMvc.perform(post("/api/projects/%d/ai-video-tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId).contentType(MediaType.APPLICATION_JSON).content(data))
            .andExpect(status().isOk()).andReturn();
        Long taskId = readLong(created, "$.data.id");
        assertThat(jdbc.queryForObject("select first_frame_url from ai_video_task where id=?", String.class, taskId)).isNull();
        String snapshot = jdbc.queryForObject("select request_snapshot_json from ai_video_task where id=?", String.class, taskId);
        assertThat(snapshot).contains("\"textOnly\":true", "\"references\":[]", "王大山在卧室醒来");
        assertThat(jdbc.queryForObject("select count(*) from ai_video_task_reference where task_id=?", Integer.class, taskId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from ai_point_reservation where tenant_id=?", Integer.class, tenantId)).isEqualTo(1);
        pollVideoTask(token, tenantId, projectId, taskId).andExpect(jsonPath("$.data.status", is("GENERATING")));
    }

    @Test
    void compilesAndFreezesSeedanceReferencesBeforeCreatingExecution() throws Exception {
        String token = registerUser("13800016021", "Seedance Owner");
        Long tenantId = createTenant(token, "Seedance 多模态团队");
        Long ownerId = userIdByMobile("13800016021");
        Long projectId = createProject(token, tenantId, ownerId, "Seedance 多模态项目", "SEEDANCE_MULTIMODAL");
        Long modelId = enableSeedanceMini();
        Long storyboardId = createStoryboard(tenantId, projectId, ownerId);
        long variantId = seedStoryboardImageReference(tenantId, projectId, storyboardId, ownerId);
        grantTeamPoints(tenantId, 5);

        MvcResult created = mockMvc.perform(post("/api/projects/%d/ai-video-tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "storyboardId":%d,
                      "modelId":%d,
                      "prompt":"界面展示提示词",
                      "durationSeconds":5,
                      "aspectRatio":"9:16",
                      "resolution":"720p",
                      "generateAudio":true,
                      "watermark":false
                    }
                    """.formatted(storyboardId, modelId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("PENDING")))
            .andReturn();
        Long taskId = readLong(created, "$.data.id");
        assertThat(jdbc.queryForObject("select count(*) from ai_execution_attempt where execution_id = (select execution_id from ai_video_task where id = ?)", Integer.class, taskId)).isZero();
        pollVideoTask(token, tenantId, projectId, taskId)
            .andExpect(jsonPath("$.data.status", is("GENERATING")));

        var task = jdbc.queryForMap("""
            select compiled_prompt, request_snapshot_json, generate_audio, watermark, execution_id
              from ai_video_task where id = ?
            """, taskId);
        assertThat(task.get("compiled_prompt")).isEqualTo("首帧使用图片1完成广告");
        assertThat(task.get("request_snapshot_json").toString())
            .contains("\"compiledPrompt\":\"首帧使用图片1完成广告\"", "\"resolution\":\"720p\"");
        assertThat(task.get("generate_audio")).isEqualTo(true);
        assertThat(task.get("watermark")).isEqualTo(false);
        assertThat(task.get("execution_id")).isNotNull();
        var reference = jdbc.queryForMap("select * from ai_video_task_reference where task_id = ?", taskId);
        assertThat(reference.get("source_id")).isEqualTo(variantId);
        assertThat(reference.get("compiled_label")).isEqualTo("图片1");
        assertThat(reference.get("provider_role")).isEqualTo("reference_image");
        assertThat(reference.get("provider_url").toString()).contains("seedance-frame.png");
        jdbc.update("update ai_image_result set image_url = 'https://cdn.example/replaced.png' where id = 1");
        assertThat(jdbc.queryForObject(
            "select provider_url from ai_video_task_reference where task_id = ?", String.class, taskId))
            .contains("seedance-frame.png");
        mockMvc.perform(post("/api/projects/%d/ai-video-tasks/%d/poll".formatted(projectId, taskId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("SUCCEEDED")));
        assertThat(jdbc.queryForObject(
            "select provider_result_metadata_json from ai_video_task where id = ?", String.class, taskId))
            .contains("\"resolution\":\"720p\"", "\"generateAudio\":true");
        assertThat(jdbc.queryForObject(
            "select duration_seconds from ai_video_result where task_id = ?", java.math.BigDecimal.class, taskId))
            .isEqualByComparingTo("5");

        Long invalidStoryboardId = createStoryboardWithShot(tenantId, projectId, ownerId, 2);
        jdbc.update("""
            update storyboard
               set prompt_document_json = '{"version":2,"nodes":[
                 {"type":"text","text":"无效"},
                 {"type":"mention","mediaType":"IMAGE","sourceType":"ASSET_VISUAL_VARIANT",
                  "sourceId":999999,"displayName":"不存在图片"}]}'
             where id = ?
            """, invalidStoryboardId);
        int taskCount = jdbc.queryForObject("select count(*) from ai_video_task", Integer.class);
        int executionCount = jdbc.queryForObject("select count(*) from ai_execution_task", Integer.class);

        mockMvc.perform(post("/api/projects/%d/ai-video-tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"storyboardId":%d,"modelId":%d,"prompt":"无效引用",
                     "durationSeconds":5,"aspectRatio":"9:16","resolution":"720p"}
                    """.formatted(invalidStoryboardId, modelId)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")));
        assertThat(jdbc.queryForObject("select count(*) from ai_video_task", Integer.class)).isEqualTo(taskCount);
        assertThat(jdbc.queryForObject("select count(*) from ai_execution_task", Integer.class)).isEqualTo(executionCount);
    }

    @Test
    void createsCompletesSavesBindsAndProtectsStoryboardVideoResult() throws Exception {
        String token = registerUser("13800016001", "Video Owner");
        Long tenantId = createTenant(token, "视频生成团队");
        Long ownerId = userIdByMobile("13800016001");
        Long projectId = createProject(token, tenantId, ownerId, "分镜视频项目", "AI_VIDEO_TASK_FLOW");
        Long modelId = createVideoService(token, tenantId);
        Long storyboardId = createStoryboard(tenantId, projectId, ownerId);
        grantTeamPoints(tenantId, 5);

        MvcResult createResult = mockMvc.perform(post("/api/projects/%d/ai-video-tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "storyboardId":%d,
                      "modelId":%d,
                      "prompt":"雨夜中女主缓慢推门进入，镜头轻微推近，情绪紧张",
                      "negativePrompt":"画面扭曲，人物畸形",
                      "firstFrameUrl":"https://cdn.example.com/first-frame.jpg",
                      "durationSeconds":5,
                      "aspectRatio":"9:16",
                      "resolution":"STANDARD",
                      "cameraMovement":"PUSH_IN",
                      "motionStrength":"MEDIUM"
                    }
                    """.formatted(storyboardId, modelId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id", notNullValue()))
            .andExpect(jsonPath("$.data.executionId", notNullValue()))
            .andExpect(jsonPath("$.data.status", is("PENDING")))
            .andReturn();

        Long taskId = readLong(createResult, "$.data.id");
        pollVideoTask(token, tenantId, projectId, taskId)
            .andExpect(jsonPath("$.data.status", is("GENERATING")));
        var inflight = jdbc.queryForMap("select status, phase, claim_token, claim_expires_at from ai_execution_task where id = (select execution_id from ai_video_task where id = ?)", taskId);
        assertThat(inflight.get("status")).isEqualTo("RUNNING");
        assertThat(inflight.get("phase")).isEqualTo("VIDEO_QUERY");
        assertThat(inflight.get("claim_token")).isNull();
        assertThat(inflight.get("claim_expires_at")).isNull();

        mockMvc.perform(post("/api/projects/%d/ai-video-tasks/%d/poll".formatted(projectId, taskId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("SUCCEEDED")))
            .andExpect(jsonPath("$.data.results", hasSize(1)))
            .andExpect(jsonPath("$.data.results[0].videoUrl", containsString(".mp4")));

        MvcResult detailResult = mockMvc.perform(get("/api/projects/%d/ai-video-tasks/%d".formatted(projectId, taskId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("SUCCEEDED")))
            .andExpect(jsonPath("$.data.results", hasSize(1)))
            .andReturn();
        Long resultId = readLong(detailResult, "$.data.results[0].id");

        mockMvc.perform(post("/api/projects/%d/ai-video-results/%d/save-material".formatted(projectId, resultId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.materialId", notNullValue()));

        mockMvc.perform(post("/api/projects/%d/ai-video-results/%d/bind-storyboard".formatted(projectId, resultId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.isSelected", is(true)));

        mockMvc.perform(delete("/api/projects/%d/ai-video-results/%d".formatted(projectId, resultId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode", is("AI_VIDEO_RESULT_IN_USE")));

        mockMvc.perform(get("/api/projects/%d/ai-video-tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .param("status", "SUCCEEDED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total", is(1)))
            .andExpect(jsonPath("$.data.data", hasSize(1)))
            .andExpect(jsonPath("$.data.data[0].id", is(taskId.intValue())));

        String storagePath = jdbc.queryForObject(
            "select storage_path from ai_video_result where task_id = ?",
            String.class,
            taskId
        );
        assertThat(storedObjectBytes(storagePath)).isNotEmpty();
    }

    @Test
    void returnsExistingActiveTaskForDuplicateCreateRequest() throws Exception {
        String token = registerUser("13800016004", "Idempotent Owner");
        Long tenantId = createTenant(token, "幂等视频团队");
        Long ownerId = userIdByMobile("13800016004");
        Long projectId = createProject(token, tenantId, ownerId, "幂等视频项目", "AI_VIDEO_IDEMPOTENT");
        Long modelId = createVideoService(token, tenantId);
        Long storyboardId = createStoryboard(tenantId, projectId, ownerId);
        grantTeamPoints(tenantId, 1);

        Long firstTaskId = createVideoTask(token, tenantId, projectId, storyboardId, modelId, "同一条视频提示词");
        Long secondTaskId = createVideoTask(token, tenantId, projectId, storyboardId, modelId, "同一条视频提示词");

        assert firstTaskId.equals(secondTaskId);
        Integer taskCount = jdbc.queryForObject(
            "select count(*) from ai_video_task where tenant_id = ? and project_id = ?",
            Integer.class,
            tenantId,
            projectId
        );
        assert taskCount == 1;
        Integer balance = jdbc.queryForObject("select balance from team_point_account where tenant_id = ?", Integer.class, tenantId);
        assert balance == 0;
    }

    @Test
    void queuesNewTaskWhenTenantConcurrencyLimitIsReached() throws Exception {
        String token = registerUser("13800016005", "Concurrency Owner");
        Long tenantId = createTenant(token, "并发视频团队");
        Long ownerId = userIdByMobile("13800016005");
        Long projectId = createProject(token, tenantId, ownerId, "并发视频项目", "AI_VIDEO_CONCURRENCY");
        Long modelId = createVideoService(token, tenantId);
        Long firstStoryboardId = createStoryboard(tenantId, projectId, ownerId);
        Long secondStoryboardId = createStoryboardWithShot(tenantId, projectId, ownerId, 2);
        grantTeamPoints(tenantId, 2);
        Long firstTaskId = createVideoTask(token, tenantId, projectId, firstStoryboardId, modelId, "第一条视频提示词");
        jdbc.update("update ai_video_task set next_poll_at = dateadd('minute', 10, now()) where id = ?", firstTaskId);

        mockMvc.perform(post("/api/projects/%d/ai-video-tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(videoTaskPayload(secondStoryboardId, modelId, "第二条视频提示词")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("PENDING")));
        aiVideoTaskService.pollDueTasks();
        assertThat(jdbc.queryForObject("select count(*) from ai_execution_task where tenant_id = ? and status = 'RUNNING'", Integer.class, tenantId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from ai_execution_task where tenant_id = ? and status = 'PENDING'", Integer.class, tenantId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from ai_execution_attempt where execution_id in (select execution_id from ai_video_task where tenant_id = ?)", Integer.class, tenantId)).isEqualTo(1);
    }

    @Test
    void ambiguousSubmissionFailureIsNotResubmittedByBackgroundPolling() throws Exception {
        AtomicInteger submissions = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/video/generations", exchange -> {
            submissions.incrementAndGet();
            writeJson(exchange, 503, "{\"message\":\"upstream outcome unknown\"}");
        });
        server.start();
        try {
            String token = registerUser("13800016024", "Unknown Submit Owner");
            Long tenantId = createTenant(token, "提交结果未知团队");
            Long ownerId = userIdByMobile("13800016024");
            Long projectId = createProject(token, tenantId, ownerId, "未知提交项目", "UNKNOWN_SUBMIT");
            Long modelId = createVideoService(token, tenantId, "http://127.0.0.1:" + server.getAddress().getPort());
            Long storyboardId = createStoryboard(tenantId, projectId, ownerId);
            grantTeamPoints(tenantId, 1);
            Long taskId = createVideoTask(token, tenantId, projectId, storyboardId, modelId, "只允许提交一次");
            aiVideoTaskService.pollDueTasks();
            aiVideoTaskService.pollDueTasks();
            assertThat(submissions.get()).isEqualTo(1);
            assertThat(jdbc.queryForObject("select status from ai_video_task where id = ?", String.class, taskId)).isEqualTo("FAILED");
            Long executionId = jdbc.queryForObject("select execution_id from ai_video_task where id = ?", Long.class, taskId);
            assertThat(jdbc.queryForObject("select status from ai_execution_task where id = ?", String.class, executionId)).isEqualTo("FAILED");
            assertThat(jdbc.queryForObject("select claim_token from ai_execution_task where id = ?", String.class, executionId)).isNull();
            assertThat(jdbc.queryForObject("select status from ai_point_reservation where execution_id = ?", String.class, executionId)).isEqualTo("SETTLEMENT_REVIEW_REQUIRED");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void cancelingQueuedVideoReleasesReservationWithoutProviderAttempt() throws Exception {
        String token = registerUser("13800016023", "Queued Cancel Owner");
        Long tenantId = createTenant(token, "排队取消团队");
        Long ownerId = userIdByMobile("13800016023");
        Long projectId = createProject(token, tenantId, ownerId, "排队取消项目", "QUEUED_CANCEL");
        Long modelId = createVideoService(token, tenantId);
        Long storyboardId = createStoryboard(tenantId, projectId, ownerId);
        grantTeamPoints(tenantId, 1);
        MvcResult created = mockMvc.perform(post("/api/projects/%d/ai-video-tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId).contentType(MediaType.APPLICATION_JSON)
                .content(videoTaskPayload(storyboardId, modelId, "尚未提交的视频")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status", is("PENDING"))).andReturn();
        Long taskId = readLong(created, "$.data.id");
        mockMvc.perform(post("/api/projects/%d/ai-video-tasks/%d/cancel".formatted(projectId, taskId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token)).header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status", is("CANCELED")));
        aiVideoTaskService.pollDueTasks();
        Long executionId = jdbc.queryForObject("select execution_id from ai_video_task where id = ?", Long.class, taskId);
        assertThat(jdbc.queryForObject("select count(*) from ai_execution_attempt where execution_id = ?", Integer.class, executionId)).isZero();
        assertThat(jdbc.queryForObject("select status from ai_point_reservation where execution_id = ?", String.class, executionId)).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("select claim_token from ai_execution_task where id = ?", String.class, executionId)).isNull();
        MvcResult regenerated = mockMvc.perform(post("/api/projects/%d/ai-video-tasks/%d/regenerate".formatted(projectId, taskId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token)).header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status", is("PENDING"))).andReturn();
        Long newTaskId = readLong(regenerated, "$.data.id");
        Long newExecutionId = readLong(regenerated, "$.data.executionId");
        assertThat(newTaskId).isNotEqualTo(taskId);
        assertThat(newExecutionId).isNotEqualTo(executionId);
        assertThat(jdbc.queryForObject("select count(*) from ai_point_reservation where tenant_id = ?", Integer.class, tenantId)).isEqualTo(2);
        pollVideoTask(token, tenantId, projectId, newTaskId).andExpect(jsonPath("$.data.status", is("GENERATING")));
        assertThat(jdbc.queryForObject("select status from ai_execution_task where id = ?", String.class, newExecutionId)).isEqualTo("RUNNING");
        assertThat(jdbc.queryForObject("select status from ai_point_reservation where execution_id = ?", String.class, newExecutionId)).isEqualTo("RESERVED");
    }

    @Test
    void regeneratingSuccessfulVideoCreatesFreshExecutionAndReservation() throws Exception {
        String token = registerUser("13800016025", "Successful Regenerate Owner");
        Long tenantId = createTenant(token, "成功重生成团队");
        Long ownerId = userIdByMobile("13800016025");
        Long projectId = createProject(token, tenantId, ownerId, "成功重生成项目", "SUCCESS_REGENERATE");
        Long modelId = createVideoService(token, tenantId);
        Long storyboardId = createStoryboard(tenantId, projectId, ownerId);
        grantTeamPoints(tenantId, 3);
        Long taskId = createVideoTask(token, tenantId, projectId, storyboardId, modelId, "重生成相同内容");
        pollVideoTask(token, tenantId, projectId, taskId).andExpect(jsonPath("$.data.status", is("SUCCEEDED")));
        Long oldExecutionId = jdbc.queryForObject("select execution_id from ai_video_task where id = ?", Long.class, taskId);
        MvcResult regenerated = mockMvc.perform(post("/api/projects/%d/ai-video-tasks/%d/regenerate".formatted(projectId, taskId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token)).header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status", is("PENDING"))).andReturn();
        Long newTaskId = readLong(regenerated, "$.data.id");
        Long newExecutionId = readLong(regenerated, "$.data.executionId");
        assertThat(newExecutionId).isNotEqualTo(oldExecutionId);
        assertThat(jdbc.queryForObject("select request_hash from ai_video_task where id = ?", String.class, newTaskId))
            .isEqualTo(jdbc.queryForObject("select request_hash from ai_video_task where id = ?", String.class, taskId));
        assertThat(jdbc.queryForObject("select count(*) from ai_point_reservation where tenant_id = ?", Integer.class, tenantId)).isEqualTo(2);
        pollVideoTask(token, tenantId, projectId, newTaskId).andExpect(jsonPath("$.data.status", is("GENERATING")));
    }

    @Test
    void expiredQueuedCancellationClaimDoesNotStrandPendingVideo() throws Exception {
        String token = registerUser("13800016026", "Expired Cancel Owner");
        Long tenantId = createTenant(token, "取消恢复团队");
        Long ownerId = userIdByMobile("13800016026");
        Long projectId = createProject(token, tenantId, ownerId, "取消恢复项目", "EXPIRED_CANCEL");
        Long modelId = createVideoService(token, tenantId);
        Long storyboardId = createStoryboard(tenantId, projectId, ownerId);
        grantTeamPoints(tenantId, 1);
        MvcResult queued = mockMvc.perform(post("/api/projects/%d/ai-video-tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId).contentType(MediaType.APPLICATION_JSON)
                .content(videoTaskPayload(storyboardId, modelId, "恢复取消中断的排队任务")))
            .andExpect(status().isOk()).andReturn();
        Long taskId = readLong(queued, "$.data.id");
        jdbc.update("update ai_video_task set execution_token = 'dead-cancel', execution_phase = 'VIDEO_CANCEL', execution_timeout_at = dateadd('minute', -1, now()) where id = ?", taskId);
        when(executionPools.tryAcquire(com.antshorttv.execution.AiExecutionPoolType.VIDEO_QUERY)).thenReturn(null);
        aiVideoTaskService.pollDueTasks();
        assertThat(jdbc.queryForObject("select status from ai_video_task where id = ?", String.class, taskId)).isEqualTo("GENERATING");
        assertThat(jdbc.queryForObject("select execution_token from ai_video_task where id = ?", String.class, taskId)).isNull();
        assertThat(jdbc.queryForObject("select count(*) from ai_execution_attempt where execution_id = (select execution_id from ai_video_task where id = ?)", Integer.class, taskId)).isEqualTo(1);
    }

    @Test
    void rejectsVideoTaskWhenTeamPointsAreInsufficient() throws Exception {
        String token = registerUser("13800016008", "Video Point Owner");
        Long tenantId = createTenant(token, "视频积分团队");
        Long ownerId = userIdByMobile("13800016008");
        Long projectId = createProject(token, tenantId, ownerId, "视频积分项目", "AI_VIDEO_NO_POINTS");
        Long modelId = createVideoService(token, tenantId);
        Long storyboardId = createStoryboard(tenantId, projectId, ownerId);

        mockMvc.perform(post("/api/projects/%d/ai-video-tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(videoTaskPayload(storyboardId, modelId, "积分不足的视频提示词")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode", is("TEAM_POINTS_INSUFFICIENT")));
    }

    @Test
    void rejectsVideoTaskBeforeProviderWhenCallBillingIsMissing() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/video/generations", exchange -> {
            calls.incrementAndGet();
            writeJson(exchange, 200, "{\"externalTaskId\":\"unexpected\",\"status\":\"ACCEPTED\"}");
        });
        server.start();
        try {
            String token = registerUser("13800016015", "Missing Video Billing Owner");
            Long tenantId = createTenant(token, "视频缺价团队");
            Long ownerId = userIdByMobile("13800016015");
            Long projectId = createProject(token, tenantId, ownerId, "视频缺价项目", "AI_VIDEO_MISSING_CALL_PRICE");
            Long modelId = createVideoService(
                token, tenantId, "http://127.0.0.1:%d".formatted(server.getAddress().getPort()));
            Long storyboardId = createStoryboard(tenantId, projectId, ownerId);
            grantTeamPoints(tenantId, 5);
            jdbc.update("""
                delete from ai_model_price_component
                 where price_version_id in (select id from ai_model_price_version where model_id = ?)
                   and metric = 'CALL'
                """, modelId);
            jdbc.update("""
                delete from ai_model_point_price_component
                 where price_version_id in (select id from ai_model_point_price_version where model_id = ?)
                   and metric = 'CALL'
                """, modelId);

            mockMvc.perform(post("/api/projects/%d/ai-video-tasks".formatted(projectId))
                    .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                    .header("X-Tenant-Id", tenantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(videoTaskPayload(storyboardId, modelId, "缺少调用价格")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", is("AI_MODEL_BILLING_MISSING")));

            assertThat(calls.get()).isZero();
            assertThat(jdbc.queryForObject(
                "select count(*) from ai_video_task where tenant_id = ?", Integer.class, tenantId)).isZero();
            assertThat(jdbc.queryForObject(
                "select count(*) from ai_point_reservation where tenant_id = ?", Integer.class, tenantId)).isZero();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void backgroundPollingCompletesDueGeneratingTasks() throws Exception {
        String token = registerUser("13800016006", "Polling Owner");
        Long tenantId = createTenant(token, "轮询视频团队");
        Long ownerId = userIdByMobile("13800016006");
        Long projectId = createProject(token, tenantId, ownerId, "轮询视频项目", "AI_VIDEO_POLLING");
        Long modelId = createVideoService(token, tenantId);
        Long storyboardId = createStoryboard(tenantId, projectId, ownerId);
        grantTeamPoints(tenantId, 1);
        Long taskId = createVideoTask(token, tenantId, projectId, storyboardId, modelId, "后台轮询视频提示词");

        aiVideoTaskService.pollDueTasks();
        aiVideoTaskService.pollDueTasks();

        mockMvc.perform(get("/api/projects/%d/ai-video-tasks/%d".formatted(projectId, taskId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("SUCCEEDED")))
            .andExpect(jsonPath("$.data.results", hasSize(1)));
        Integer resultCount = jdbc.queryForObject("select count(*) from ai_video_result where task_id = ?", Integer.class, taskId);
        Integer attemptCount = jdbc.queryForObject("""
            select count(*)
              from ai_execution_attempt a
              join ai_video_task t on t.execution_id = a.execution_id
             where t.id = ?
            """, Integer.class, taskId);
        var task = jdbc.queryForMap("select * from ai_video_task where id = ?", taskId);
        Long executionId = ((Number) task.get("execution_id")).longValue();
        org.assertj.core.api.Assertions.assertThat(resultCount).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(attemptCount).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(task.get("execution_token")).isNull();
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
            "select count(*) from ai_usage_cost_line where execution_id = ?",
            Integer.class,
            executionId
        )).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
            "select count(*) from point_ledger where execution_id = ? and entry_type = 'SETTLE'",
            Integer.class,
            executionId
        )).isEqualTo(1);
    }

    @Test
    void backgroundPollingFailsTimedOutTasks() throws Exception {
        String token = registerUser("13800016007", "Timeout Owner");
        Long tenantId = createTenant(token, "超时视频团队");
        Long ownerId = userIdByMobile("13800016007");
        Long projectId = createProject(token, tenantId, ownerId, "超时视频项目", "AI_VIDEO_TIMEOUT");
        Long modelId = createVideoService(token, tenantId);
        Long storyboardId = createStoryboard(tenantId, projectId, ownerId);
        grantTeamPoints(tenantId, 1);
        Long taskId = createVideoTask(token, tenantId, projectId, storyboardId, modelId, "超时视频提示词");
        jdbc.update("""
            update ai_video_task
            set submitted_at = dateadd('minute', -25, now()),
                started_at = dateadd('minute', -25, now())
            where id = ?
            """, taskId);

        aiVideoTaskService.pollDueTasks();
        aiVideoTaskService.pollDueTasks();

        mockMvc.perform(get("/api/projects/%d/ai-video-tasks/%d".formatted(projectId, taskId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("FAILED")))
            .andExpect(jsonPath("$.data.errorMessage", containsString("超时")));
        Long executionId = jdbc.queryForObject(
            "select execution_id from ai_video_task where id = ?", Long.class, taskId);
        assertThat(jdbc.queryForObject(
            "select status from ai_point_reservation where execution_id = ?",
            String.class,
            executionId
        )).isEqualTo("SETTLEMENT_REVIEW_REQUIRED");
        assertThat(jdbc.queryForObject(
            "select count(*) from point_ledger where execution_id = ? and entry_type = 'SETTLEMENT_REVIEW'",
            Integer.class,
            executionId
        )).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from point_ledger where execution_id = ? and entry_type = 'SETTLE'",
            Integer.class, executionId)).isZero();
        assertThat(jdbc.queryForObject("select reserved_points - settled_points - released_points from ai_point_reservation where execution_id = ?",
            java.math.BigDecimal.class, executionId)).isEqualByComparingTo("1");
    }

    @Test
    void rejectsCreationWhenStoryboardHasNoFirstFrame() throws Exception {
        String token = registerUser("13800016002", "No Frame Owner");
        Long tenantId = createTenant(token, "无首帧团队");
        Long ownerId = userIdByMobile("13800016002");
        Long projectId = createProject(token, tenantId, ownerId, "无首帧项目", "AI_VIDEO_NO_FRAME");
        createVideoService(token, tenantId);
        Long storyboardId = createStoryboardWithoutFirstFrame(tenantId, projectId, ownerId);

        mockMvc.perform(post("/api/projects/%d/ai-video-tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "storyboardId":%d,
                      "prompt":"女主走入大厅",
                      "durationSeconds":5,
                      "aspectRatio":"9:16"
                    }
                    """.formatted(storyboardId)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode", is("AI_VIDEO_STORYBOARD_FIRST_FRAME_REQUIRED")));
        Long readyStoryboardId = createStoryboardWithShot(tenantId, projectId, ownerId, 2);
        grantTeamPoints(tenantId, 5);
        mockMvc.perform(post("/api/projects/%d/ai-video-tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"storyboardId":%d,"prompt":"女主走入大厅","durationSeconds":5,
                     "aspectRatio":"9:16","textOnly":true}
                    """.formatted(readyStoryboardId)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")))
            .andExpect(jsonPath("$.errorMessage", is("所选视频模型暂不支持纯文生视频，请选择 Seedance 模型。")));

    }

    @Test
    void rejectsCreationWhenTenantHasNoVideoService() throws Exception {
        String token = registerUser("13800016003", "No Service Owner");
        Long tenantId = createTenant(token, "无视频服务团队");
        Long ownerId = userIdByMobile("13800016003");
        Long projectId = createProject(token, tenantId, ownerId, "无服务项目", "AI_VIDEO_NO_SERVICE");
        Long storyboardId = createStoryboard(tenantId, projectId, ownerId);
        mockMvc.perform(post("/api/projects/%d/ai-video-tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "storyboardId":%d,
                      "prompt":"女主走入大厅",
                      "durationSeconds":5,
                      "aspectRatio":"9:16"
                    }
                    """.formatted(storyboardId)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode", is("AI_MODEL_NOT_FOUND")));
    }

    @Test
    void rejectsVideoTaskWhenServiceKeyCannotBeDecrypted() throws Exception {
        String token = registerUser("13800016009", "Decrypt Owner");
        Long tenantId = createTenant(token, "解密失败团队");
        Long ownerId = userIdByMobile("13800016009");
        Long projectId = createProject(token, tenantId, ownerId, "解密失败项目", "AI_VIDEO_DECRYPT");
        Long modelId = createVideoService(token, tenantId, "http://127.0.0.1:0/v1");
        Long storyboardId = createStoryboard(tenantId, projectId, ownerId);
        grantTeamPoints(tenantId, 1);

        AtomicInteger callCount = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/video/generations", exchange -> handleVideoServerRequest(exchange, callCount));
        server.start();
        int port = server.getAddress().getPort();
        jdbc.update(
            "update ai_provider_config set base_url = ?, api_key_cipher = ? where provider_id = (select provider_id from ai_model where id = ?)",
            "http://127.0.0.1:" + port + "/v1",
            "not-a-valid-cipher",
            modelId
        );

        try {
            MvcResult queued = mockMvc.perform(post("/api/projects/%d/ai-video-tasks".formatted(projectId))
                    .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                    .header("X-Tenant-Id", tenantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(videoTaskPayload(storyboardId, modelId, "密钥坏掉的视频提示词")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PENDING"))).andReturn();
            pollVideoTask(token, tenantId, projectId, readLong(queued, "$.data.id"))
                .andExpect(jsonPath("$.data.status", is("FAILED")))
                .andExpect(jsonPath("$.data.errorMessage", containsString("AI 服务密钥解密失败")));
            org.junit.jupiter.api.Assertions.assertEquals(0, callCount.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsUndecryptableVideoServiceKeyWithoutCallingProvider() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/video/generations", exchange -> {
            calls.incrementAndGet();
            byte[] body = "{\"externalTaskId\":\"unexpected\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            String token = registerUser("13800016010", "Broken Key Owner");
            Long tenantId = createTenant(token, "坏密钥视频团队");
            Long ownerId = userIdByMobile("13800016010");
            Long projectId = createProject(token, tenantId, ownerId, "坏密钥视频项目", "AI_VIDEO_BROKEN_KEY");
            Long modelId = createVideoService(token, tenantId, "http://127.0.0.1:%d".formatted(server.getAddress().getPort()));
            jdbc.update(
                "update ai_provider_config set api_key_cipher = ? where provider_id = (select provider_id from ai_model where id = ?)",
                new com.antshorttv.ai.AiSecretCodec("other-secret").encrypt("sk-real-qwen"),
                modelId
            );
            Long storyboardId = createStoryboard(tenantId, projectId, ownerId);
            grantTeamPoints(tenantId, 5);

            MvcResult result = mockMvc.perform(post("/api/projects/%d/ai-video-tasks".formatted(projectId))
                    .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                    .header("X-Tenant-Id", tenantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "storyboardId":%d,
                          "modelId":%d,
                          "prompt":"雨夜中女主缓慢推门进入",
                          "firstFrameUrl":"https://cdn.example.com/first-frame.jpg",
                          "durationSeconds":5,
                          "aspectRatio":"9:16",
                          "resolution":"STANDARD",
                          "cameraMovement":"PUSH_IN",
                          "motionStrength":"MEDIUM"
                        }
                      """.formatted(storyboardId, modelId)))
                .andExpect(status().isOk())
                .andReturn();

            assertThat(calls.get()).isZero();
            assertThat((String) JsonPath.read(result.getResponse().getContentAsString(), "$.data.status")).isEqualTo("PENDING");
            pollVideoTask(token, tenantId, projectId, readLong(result, "$.data.id"))
                .andExpect(jsonPath("$.data.status", is("FAILED")));
            assertThat(calls.get()).isZero();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void retriesProviderPollingWithoutSubmittingTheVideoTwice() throws Exception {
        AtomicInteger submissions = new AtomicInteger();
        AtomicInteger queries = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/video/generations", exchange -> {
            submissions.incrementAndGet();
            writeJson(exchange, 200, "{\"externalTaskId\":\"retry-task\",\"status\":\"ACCEPTED\"}");
        });
        server.createContext("/video/tasks", exchange -> {
            if (queries.incrementAndGet() == 1) {
                writeJson(exchange, 503, "{\"message\":\"temporary\"}");
                return;
            }
            writeJson(exchange, 200, """
                {"status":"SUCCEEDED","videoUrl":"http://127.0.0.1:%d/generated.mp4"}
                """.formatted(server.getAddress().getPort()));
        });
        server.createContext("/generated.mp4", exchange -> {
            byte[] body = "video".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        try {
            String token = registerUser("13800016011", "Polling Retry Owner");
            Long tenantId = createTenant(token, "轮询重试团队");
            Long ownerId = userIdByMobile("13800016011");
            Long projectId = createProject(token, tenantId, ownerId, "轮询重试项目", "AI_VIDEO_POLL_RETRY");
            Long modelId = createVideoService(token, tenantId, "http://127.0.0.1:" + server.getAddress().getPort());
            Long storyboardId = createStoryboard(tenantId, projectId, ownerId);
            grantTeamPoints(tenantId, 1);
            Long taskId = createVideoTask(token, tenantId, projectId, storyboardId, modelId, "轮询失败后恢复");

            pollVideoTask(token, tenantId, projectId, taskId);
            pollVideoTask(token, tenantId, projectId, taskId)
                .andExpect(jsonPath("$.data.status", is("SUCCEEDED")));

            assertThat(submissions.get()).isEqualTo(1);
            assertThat(queries.get()).isEqualTo(2);
            assertThat(jdbc.queryForObject(
                """
                    select count(*)
                      from ai_execution_attempt a
                      join ai_video_task t on t.execution_id = a.execution_id
                     where t.id = ?
                    """,
                Integer.class,
                taskId
            )).isEqualTo(3);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void cancellationPreventsFurtherProviderPolling() throws Exception {
        AtomicInteger queries = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/video/generations", exchange ->
            writeJson(exchange, 200, "{\"externalTaskId\":\"cancel-task\",\"status\":\"ACCEPTED\"}"));
        server.createContext("/video/tasks", exchange -> {
            queries.incrementAndGet();
            writeJson(exchange, 200, "{\"status\":\"RUNNING\"}");
        });
        server.start();
        try {
            String token = registerUser("13800016012", "Cancel Owner");
            Long tenantId = createTenant(token, "取消视频团队");
            Long ownerId = userIdByMobile("13800016012");
            Long projectId = createProject(token, tenantId, ownerId, "取消视频项目", "AI_VIDEO_CANCEL");
            Long modelId = createVideoService(token, tenantId, "http://127.0.0.1:" + server.getAddress().getPort());
            Long storyboardId = createStoryboard(tenantId, projectId, ownerId);
            grantTeamPoints(tenantId, 1);
            Long taskId = createVideoTask(token, tenantId, projectId, storyboardId, modelId, "取消后不查询");

            mockMvc.perform(post("/api/projects/%d/ai-video-tasks/%d/cancel".formatted(projectId, taskId))
                    .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                    .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CANCELED")));
            aiVideoTaskService.pollDueTasks();

            assertThat(queries.get()).isZero();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void recordsRetryAttemptsWhenCompletedVideoCannotBeDownloaded() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/video/generations", exchange ->
            writeJson(exchange, 200, "{\"externalTaskId\":\"download-task\",\"status\":\"ACCEPTED\"}"));
        server.createContext("/video/tasks", exchange -> writeJson(exchange, 200, """
            {"status":"SUCCEEDED","videoUrl":"http://127.0.0.1:%d/missing.mp4"}
            """.formatted(server.getAddress().getPort())));
        server.createContext("/missing.mp4", exchange -> writeJson(exchange, 502, "{\"message\":\"missing\"}"));
        server.start();
        try {
            String token = registerUser("13800016013", "Download Failure Owner");
            Long tenantId = createTenant(token, "下载失败团队");
            Long ownerId = userIdByMobile("13800016013");
            Long projectId = createProject(token, tenantId, ownerId, "下载失败项目", "AI_VIDEO_DOWNLOAD_FAIL");
            Long modelId = createVideoService(token, tenantId, "http://127.0.0.1:" + server.getAddress().getPort());
            Long storyboardId = createStoryboard(tenantId, projectId, ownerId);
            grantTeamPoints(tenantId, 1);
            Long taskId = createVideoTask(token, tenantId, projectId, storyboardId, modelId, "下载失败重试");

            pollVideoTask(token, tenantId, projectId, taskId);
            pollVideoTask(token, tenantId, projectId, taskId);
            pollVideoTask(token, tenantId, projectId, taskId)
                .andExpect(jsonPath("$.data.status", is("FAILED")));

            assertThat(jdbc.queryForObject(
                """
                    select count(*)
                      from ai_execution_attempt a
                      join ai_video_task t on t.execution_id = a.execution_id
                     where t.id = ?
                    """,
                Integer.class,
                taskId
            )).isEqualTo(4);
            assertThat(jdbc.queryForObject(
                "select count(*) from ai_video_result where task_id = ?",
                Integer.class,
                taskId
            )).isZero();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void correlatesSuccessfulVideoWithExecutionCallAndSettlement() throws Exception {
        String token = registerUser("13800016014", "Settlement Owner");
        Long tenantId = createTenant(token, "视频结算团队");
        Long ownerId = userIdByMobile("13800016014");
        Long projectId = createProject(token, tenantId, ownerId, "视频结算项目", "AI_VIDEO_SETTLEMENT");
        Long modelId = createVideoService(token, tenantId);
        Long storyboardId = createStoryboard(tenantId, projectId, ownerId);
        grantTeamPoints(tenantId, 2);
        Long taskId = createVideoTask(token, tenantId, projectId, storyboardId, modelId, "视频结算关联");

        pollVideoTask(token, tenantId, projectId, taskId);

        Long executionId = jdbc.queryForObject(
            "select execution_id from ai_video_task where id = ?",
            Long.class,
            taskId
        );
        assertThat(executionId).isNotNull();
        assertThat(jdbc.queryForObject(
            "select count(*) from ai_call_log where execution_id = ?",
            Integer.class,
            executionId
        )).isGreaterThan(0);
        assertThat(jdbc.queryForObject(
            "select count(*) from ai_execution_attempt where execution_id = ?",
            Integer.class,
            executionId
        )).isEqualTo(2);
        assertThat(jdbc.queryForObject(
            "select count(*) from ai_call_log where execution_id = ? and attempt_id is not null",
            Integer.class,
            executionId
        )).isEqualTo(2);
        assertThat(jdbc.queryForList(
            "select metric from ai_usage_line where execution_id = ? order by metric",
            String.class,
            executionId
        )).containsExactly("CALL", "VIDEO_SECOND");
        assertThat(jdbc.queryForObject(
            "select count(*) from ai_usage_cost_line where execution_id = ?",
            Integer.class,
            executionId
        )).isEqualTo(2);
        var billing = jdbc.queryForMap("""
            select cost_price_version_id, point_price_version_id, usage_cost_status
              from ai_execution_task where id = ?
            """, executionId);
        assertThat(billing.get("cost_price_version_id")).isNotNull();
        assertThat(billing.get("point_price_version_id")).isNotNull();
        assertThat(billing.get("usage_cost_status")).isEqualTo("PRICED");
        assertThat(jdbc.queryForObject(
            "select count(*) from ai_usage_cost_line where execution_id = ? and pricing_status = 'PRICED'",
            Integer.class,
            executionId
        )).isEqualTo(2);
        assertThat(jdbc.queryForObject(
            "select status from ai_point_reservation where execution_id = ?",
            String.class,
            executionId
        )).isEqualTo("SETTLED");
    }

    private Long createStoryboard(Long tenantId, Long projectId, Long createdBy) {
        return createStoryboardWithShot(tenantId, projectId, createdBy, 1);
    }

    private org.springframework.test.web.servlet.ResultActions pollVideoTask(
        String token,
        Long tenantId,
        Long projectId,
        Long taskId
    ) throws Exception {
        return mockMvc.perform(post("/api/projects/%d/ai-video-tasks/%d/poll".formatted(projectId, taskId))
            .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
            .header("X-Tenant-Id", tenantId));
    }

    private void writeJson(HttpExchange exchange, int statusCode, String json) throws java.io.IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private Long createStoryboardWithShot(Long tenantId, Long projectId, Long createdBy, int shotNo) {
        jdbc.update("""
            insert into storyboard
              (tenant_id, project_id, episode_no, shot_no, shot_type, visual_description,
               characters, actions, scene, duration_seconds, image_prompt, video_prompt,
               first_frame_url, status, created_by, created_at, updated_at)
            values
              (?, ?, 1, ?, 'MEDIUM', '雨夜中女主站在豪宅门口', '女主',
               '缓慢推门', '豪宅门口', 5, '雨夜豪宅首帧', '镜头推近女主',
               'https://cdn.example.com/first-frame.jpg', 'READY', ?, now(), now())
            """, tenantId, projectId, shotNo, createdBy);
        return jdbc.queryForObject("select max(id) from storyboard where tenant_id = ? and project_id = ?", Long.class, tenantId, projectId);
    }

    private Long createStoryboardWithoutFirstFrame(Long tenantId, Long projectId, Long createdBy) {
        jdbc.update("""
            insert into storyboard
              (tenant_id, project_id, episode_no, shot_no, shot_type, visual_description,
               characters, actions, scene, duration_seconds, image_prompt, video_prompt,
               status, created_by, created_at, updated_at)
            values
              (?, ?, 1, 2, 'MEDIUM', '女主走入大厅', '女主',
               '走入大厅', '大厅', 5, '大厅首帧', '镜头跟随女主',
               'READY', ?, now(), now())
            """, tenantId, projectId, createdBy);
        return jdbc.queryForObject("select max(id) from storyboard where tenant_id = ? and project_id = ?", Long.class, tenantId, projectId);
    }

    private Long createVideoService(String token, Long tenantId) throws Exception {
        return createVideoService(token, tenantId, "https://example.com/v1");
    }

    private Long createVideoService(String token, Long tenantId, String baseUrl) throws Exception {
        Long providerId = jdbc.queryForObject(
            "select id from ai_provider where code = '火山'",
            Long.class
        );
        jdbc.update("update ai_provider set status = 'ENABLED' where id = ?", providerId);
        jdbc.update(
            "update ai_provider_config set api_key_cipher = ?, base_url = ?, status = 'ENABLED' where provider_id = ?",
            aiSecretCodec.encrypt("sk-test-1234"),
            baseUrl,
            providerId
        );
        Long modelId = jdbc.query(
            "select id from ai_model where code = 'TEST_VOLCENGINE_VIDEO'",
            rs -> rs.next() ? rs.getLong(1) : null
        );
        if (modelId == null) {
            jdbc.update(
                """
                    insert into ai_model
                      (provider_id, code, name, model_code, service_type, status, is_default, sort, config_json, created_at, updated_at)
                    values (?, 'TEST_VOLCENGINE_VIDEO', '测试视频模型', 'seedance-test', 'VIDEO', 'ENABLED', true, 100,
                      '{"submitEndpoint":"/video/generations","queryEndpoint":"/video/tasks"}', now(), now())
                    """,
                providerId
            );
            modelId = jdbc.queryForObject(
                "select id from ai_model where code = 'TEST_VOLCENGINE_VIDEO'",
                Long.class
            );
            jdbc.update(
                """
                    insert into ai_model_capability
                      (model_id, capability, status, created_at, updated_at)
                    values (?, 'VIDEO_GENERATION', 'ENABLED', now(), now())
                    """,
                modelId
            );
        } else {
            jdbc.update("update ai_model set status = 'ENABLED', is_default = true where id = ?", modelId);
        }
        com.antshorttv.support.ModelBillingTestSupport.publish(
            jdbc, modelId, "VIDEO_SECOND", java.math.BigDecimal.valueOf(5), java.math.BigDecimal.ONE
        );
        com.antshorttv.support.ModelBillingTestSupport.publish(
            jdbc, modelId, "CALL", java.math.BigDecimal.ONE, java.math.BigDecimal.ZERO
        );
        return modelId;
    }

    private Long createVideoTask(
        String token,
        Long tenantId,
        Long projectId,
        Long storyboardId,
        Long modelId,
        String prompt
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/%d/ai-video-tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(videoTaskPayload(storyboardId, modelId, prompt)))
            .andExpect(status().isOk())
            .andReturn();
        Long id = readLong(result, "$.data.id");
        if ("PENDING".equals(JsonPath.read(result.getResponse().getContentAsString(), "$.data.status"))) {
            pollVideoTask(token, tenantId, projectId, id);
        }
        return id;
    }

    private void grantTeamPoints(Long tenantId, int amount) {
        jdbc.update("""
            insert into team_point_account
              (tenant_id, balance, total_granted, total_consumed, created_at, updated_at)
            values (?, ?, ?, 0, now(), now())
            """, tenantId, amount, amount);
    }

    private String videoTaskPayload(Long storyboardId, Long modelId, String prompt) {
        return """
            {
              "storyboardId":%d,
              "modelId":%d,
              "prompt":"%s",
              "firstFrameUrl":"https://cdn.example.com/first-frame.jpg",
              "durationSeconds":5,
              "aspectRatio":"9:16",
              "resolution":"STANDARD",
              "cameraMovement":"PUSH_IN",
              "motionStrength":"MEDIUM"
            }
            """.formatted(storyboardId, modelId, prompt);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"400,true", "403,true", "408,false", "500,false"})
    void rejectedSeedanceSubmissionReleasesPointsWhileUnknownSubmissionStaysForReview(
        int providerStatus, boolean releaseExpected
    ) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v3/contents/generations/tasks", exchange -> writeJson(exchange,
            providerStatus, "{\"error\":{\"code\":\"InputImageSensitiveContentDetected.PrivacyInformation\","
                + "\"message\":\"input image 'content[1]' may contain real person\"}}"));
        server.start();
        try {
            String mobile = "13800017" + providerStatus;
            String token = registerUser(mobile, "Rejected Video Owner");
            Long tenantId = createTenant(token, "Video rejection team " + providerStatus);
            Long ownerId = userIdByMobile(mobile);
            Long projectId = createProject(token, tenantId, ownerId, "Rejected video", "REJECT_" + providerStatus);
            Long modelId = enableSeedanceMini("http://127.0.0.1:%d/api/v3".formatted(server.getAddress().getPort()));
            Long storyboardId = createStoryboard(tenantId, projectId, ownerId);
            seedStoryboardImageReference(tenantId, projectId, storyboardId, ownerId);
            grantTeamPoints(tenantId, 5);
            java.math.BigDecimal before = jdbc.queryForObject(
                "select balance from team_point_account where tenant_id=?", java.math.BigDecimal.class, tenantId);
            MvcResult created = mockMvc.perform(post("/api/projects/%d/ai-video-tasks".formatted(projectId))
                    .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                    .header("X-Tenant-Id", tenantId).contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"storyboardId":%d,"modelId":%d,"prompt":"Rejected reference image",
                         "durationSeconds":5,"aspectRatio":"9:16","resolution":"720p"}
                        """.formatted(storyboardId, modelId)))
                .andExpect(status().isOk()).andReturn();
            Long taskId = readLong(created, "$.data.id");
            pollVideoTask(token, tenantId, projectId, taskId)
                .andExpect(jsonPath("$.data.status", is("FAILED")))
                .andExpect(jsonPath("$.data.errorMessage", org.hamcrest.Matchers.containsString("图片1（林晚）")))
                .andExpect(jsonPath("$.data.referenceDiagnostics.failedImages[0].imageUrl",
                    is("https://cdn.example/seedance-frame.png")));
            mockMvc.perform(get("/api/projects/%d/ai-video-tasks".formatted(projectId))
                    .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                    .header("X-Tenant-Id", tenantId).param("storyboardId", storyboardId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data[0].errorMessage", org.hamcrest.Matchers.containsString("图片1（林晚）")))
                .andExpect(jsonPath("$.data.data[0].referenceDiagnostics.failedImages[0].imageUrl",
                    is("https://cdn.example/seedance-frame.png")));
            String settlement = releaseExpected ? "RELEASED" : "SETTLEMENT_REVIEW_REQUIRED";
            assertThat(jdbc.queryForObject("""
                select r.status from ai_point_reservation r join ai_video_task v on v.execution_id=r.execution_id
                 where v.id=?
                """, String.class, taskId)).isEqualTo(settlement);
            assertThat(jdbc.queryForObject("""
                select e.point_settlement_status from ai_execution_task e join ai_video_task v on v.execution_id=e.id
                 where v.id=?
                """, String.class, taskId)).isEqualTo(settlement);
            java.math.BigDecimal reserved = jdbc.queryForObject(
                "select reserved_balance from team_point_account where tenant_id=?", java.math.BigDecimal.class, tenantId);
            java.math.BigDecimal after = jdbc.queryForObject(
                "select balance from team_point_account where tenant_id=?", java.math.BigDecimal.class, tenantId);
            if (releaseExpected) {
                assertThat(reserved).isZero();
                assertThat(after).isEqualByComparingTo(before);
                aiVideoTaskService.pollDueTasks();
                assertThat(jdbc.queryForObject("""
                    select count(*) from point_ledger l join ai_video_task v on v.execution_id=l.execution_id
                     where v.id=? and l.entry_type='RELEASE'
                    """, Integer.class, taskId)).isEqualTo(1);
            } else {
                assertThat(reserved).isPositive();
                assertThat(after.add(reserved)).isEqualByComparingTo(before);
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void cancelsSeedanceTaskRemotelyAndKeepsRepeatedCancellationIdempotent() throws Exception {
        AtomicInteger deletes = new AtomicInteger();
        AtomicInteger queries = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v3/contents/generations/tasks", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                writeJson(exchange, 200, "{\"id\":\"ark-cancel-task\",\"status\":\"queued\"}");
            } else if ("DELETE".equals(exchange.getRequestMethod())) {
                deletes.incrementAndGet();
                writeJson(exchange, 200, "{\"Result\":{}}");
            } else {
                queries.incrementAndGet();
                writeJson(exchange, 200, "{\"id\":\"ark-cancel-task\",\"status\":\"running\"}");
            }
        });
        server.start();
        try {
            String token = registerUser("13800016022", "Seedance Cancel Owner");
            Long tenantId = createTenant(token, "Seedance 取消团队");
            Long ownerId = userIdByMobile("13800016022");
            Long projectId = createProject(token, tenantId, ownerId, "Seedance 取消项目", "SEEDANCE_CANCEL");
            Long modelId = enableSeedanceMini("http://127.0.0.1:%d/api/v3".formatted(server.getAddress().getPort()));
            Long storyboardId = createStoryboard(tenantId, projectId, ownerId);
            seedStoryboardImageReference(tenantId, projectId, storyboardId, ownerId);
            grantTeamPoints(tenantId, 5);
            MvcResult createResult = mockMvc.perform(post("/api/projects/%d/ai-video-tasks".formatted(projectId))
                    .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                    .header("X-Tenant-Id", tenantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"storyboardId":%d,"modelId":%d,"prompt":"Seedance 取消",
                         "durationSeconds":5,"aspectRatio":"9:16","resolution":"720p"}
                        """.formatted(storyboardId, modelId)))
                .andExpect(status().isOk())
                .andReturn();
            Long taskId = readLong(createResult, "$.data.id");
            pollVideoTask(token, tenantId, projectId, taskId)
                .andExpect(jsonPath("$.data.status", is("GENERATING")));

            for (int attempt = 0; attempt < 2; attempt++) {
                mockMvc.perform(post("/api/projects/%d/ai-video-tasks/%d/cancel".formatted(projectId, taskId))
                        .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                        .header("X-Tenant-Id", tenantId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status", is("CANCELED")));
            }
            aiVideoTaskService.pollDueTasks();

            assertThat(deletes.get()).isEqualTo(1);
            assertThat(queries.get()).isZero();
            assertThat(jdbc.queryForObject(
                "select status from ai_point_reservation where execution_id = (select execution_id from ai_video_task where id = ?)",
                String.class, taskId)).isEqualTo("SETTLEMENT_REVIEW_REQUIRED");
            assertThat(jdbc.queryForObject("select reserved_points - settled_points - released_points from ai_point_reservation where execution_id = (select execution_id from ai_video_task where id = ?)",
                java.math.BigDecimal.class, taskId)).isPositive();
        } finally {
            server.stop(0);
        }
    }

    private Long enableSeedanceMini() {
        return enableSeedanceMini("mock://seedance");
    }

    private Long enableSeedanceMini(String baseUrl) {
        Long providerId = jdbc.queryForObject(
            "select id from ai_provider where code = 'VOLCENGINE_ARK'", Long.class);
        jdbc.update("update ai_provider set status = 'ENABLED' where id = ?", providerId);
        jdbc.update("""
            update ai_provider_config
               set api_key_cipher = ?, base_url = ?, status = 'ENABLED'
             where provider_id = ?
            """, aiSecretCodec.encrypt("ark-test-key"), baseUrl, providerId);
        Long modelId = jdbc.queryForObject(
            "select id from ai_model where code = 'SEEDANCE_2_0_MINI'", Long.class);
        jdbc.update("""
            update ai_model
               set model_code = 'ep-test-mini', status = 'ENABLED', is_default = true
             where id = ?
            """, modelId);
        jdbc.update("""
            update ai_model_capability set status = 'ENABLED'
             where model_id = ? and capability = 'VIDEO_GENERATION'
            """, modelId);
        com.antshorttv.support.ModelBillingTestSupport.publish(
            jdbc, modelId, "OUTPUT_TOKEN", java.math.BigDecimal.valueOf(1000000), java.math.BigDecimal.ONE);
        com.antshorttv.support.ModelBillingTestSupport.publish(
            jdbc, modelId, "CALL", java.math.BigDecimal.ONE, java.math.BigDecimal.ZERO);
        return modelId;
    }

    private long seedStoryboardImageReference(
        Long tenantId,
        Long projectId,
        Long storyboardId,
        Long createdBy
    ) {
        jdbc.update("""
            insert into ai_image_result
              (tenant_id, project_id, task_id, target_type, target_id, image_url,
               storage_path, mime_type, width, height, file_size, is_selected, status,
               created_at, updated_at)
            values (?, ?, 1, 'VISUAL_VARIANT', 1, 'https://cdn.example/seedance-frame.png',
                    null, 'image/png', 720, 1280, 1000, true, 'ACTIVE', now(), now())
            """, tenantId, projectId);
        Long resultId = jdbc.queryForObject("select max(id) from ai_image_result", Long.class);
        jdbc.update("""
            insert into asset_visual_variant
              (tenant_id, project_id, asset_type, asset_id, name, source_type,
               generation_status, current_image_result_id, current_image_url, is_primary,
               created_by, created_at, updated_at)
            values (?, ?, 'CHARACTER', 1, '林晚', 'USER', 'SUCCEEDED', ?,
                    'https://cdn.example/seedance-frame.png', false, ?, now(), now())
            """, tenantId, projectId, resultId, createdBy);
        Long variantId = jdbc.queryForObject("select max(id) from asset_visual_variant", Long.class);
        jdbc.update("""
            update storyboard
               set prompt_document_json = ?, video_prompt = '首帧使用林晚完成广告'
             where id = ?
            """, """
            {"version":2,"nodes":[
              {"type":"text","text":"首帧使用"},
              {"type":"mention","mediaType":"IMAGE","sourceType":"ASSET_VISUAL_VARIANT",
               "sourceId":%d,"assetType":"CHARACTER","assetId":1,"variantId":%d,"displayName":"林晚"},
              {"type":"text","text":"完成广告"}
            ]}
            """.formatted(variantId, variantId), storyboardId);
        return variantId;
    }

    private String registerUser(String mobile, String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mobile":"%s","verificationCode":"%s","nickname":"%s","password":"Password123"}
                    """.formatted(mobile, registrationVerificationCode(mockMvc, mobile), nickname)))
            .andExpect(status().isOk())
            .andReturn();
        return com.antshorttv.support.SessionTestSupport.sessionCredential(result);
    }

    private Long createTenant(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tenants")
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"%s","type":"STUDIO","description":"视频生成测试"}
                    """.formatted(name)))
            .andExpect(status().isOk())
            .andReturn();
        return readLong(result, "$.data.id");
    }

    private Long createProject(String token, Long tenantId, Long ownerId, String name, String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"%s","code":"%s","description":"视频生成项目","ownerId":%d}
                    """.formatted(name, code, ownerId)))
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

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private void handleVideoServerRequest(HttpExchange exchange, AtomicInteger callCount) throws java.io.IOException {
        callCount.incrementAndGet();
        byte[] body = """
            {"externalTaskId":"server-task-1","status":"ACCEPTED"}
            """.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }
}
