package com.antshorttv.video;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.antshorttv.user.UserEntity;
import com.antshorttv.user.UserMapper;
import com.jayway.jsonpath.JsonPath;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
    "ai.video.max-concurrent-per-tenant=1",
    "ai.video.scheduler.enabled=false",
    "ai.video.storage-root=target/test-video-storage",
    "ai.video.task-timeout-minutes=20"
})
@AutoConfigureMockMvc
class AiVideoTaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AiVideoTaskService aiVideoTaskService;

    @Test
    void createsCompletesSavesBindsAndProtectsStoryboardVideoResult() throws Exception {
        String token = registerUser("13800016001", "Video Owner");
        Long tenantId = createTenant(token, "视频生成团队");
        Long ownerId = userIdByMobile("13800016001");
        Long projectId = createProject(token, tenantId, ownerId, "分镜视频项目", "AI_VIDEO_TASK_FLOW");
        Long serviceConfigId = createVideoService(token, tenantId);
        Long storyboardId = createStoryboard(tenantId, projectId, ownerId);

        MvcResult createResult = mockMvc.perform(post("/api/projects/%d/ai-video-tasks".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "storyboardId":%d,
                      "serviceConfigId":%d,
                      "prompt":"雨夜中女主缓慢推门进入，镜头轻微推近，情绪紧张",
                      "negativePrompt":"画面扭曲，人物畸形",
                      "firstFrameUrl":"https://cdn.example.com/first-frame.jpg",
                      "durationSeconds":5,
                      "aspectRatio":"9:16",
                      "resolution":"STANDARD",
                      "cameraMovement":"PUSH_IN",
                      "motionStrength":"MEDIUM"
                    }
                    """.formatted(storyboardId, serviceConfigId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id", notNullValue()))
            .andExpect(jsonPath("$.data.status", is("GENERATING")))
            .andExpect(jsonPath("$.data.externalTaskId", containsString("mock-video-")))
            .andReturn();

        Long taskId = readLong(createResult, "$.data.id");

        mockMvc.perform(post("/api/projects/%d/ai-video-tasks/%d/poll".formatted(projectId, taskId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("SUCCEEDED")))
            .andExpect(jsonPath("$.data.results", hasSize(1)))
            .andExpect(jsonPath("$.data.results[0].videoUrl", containsString(".mp4")));

        MvcResult detailResult = mockMvc.perform(get("/api/projects/%d/ai-video-tasks/%d".formatted(projectId, taskId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("SUCCEEDED")))
            .andExpect(jsonPath("$.data.results", hasSize(1)))
            .andReturn();
        Long resultId = readLong(detailResult, "$.data.results[0].id");

        mockMvc.perform(post("/api/projects/%d/ai-video-results/%d/save-material".formatted(projectId, resultId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.materialId", notNullValue()));

        mockMvc.perform(post("/api/projects/%d/ai-video-results/%d/bind-storyboard".formatted(projectId, resultId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.isSelected", is(true)));

        mockMvc.perform(delete("/api/projects/%d/ai-video-results/%d".formatted(projectId, resultId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode", is("AI_VIDEO_RESULT_IN_USE")));

        mockMvc.perform(get("/api/projects/%d/ai-video-tasks".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId)
                .param("status", "SUCCEEDED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].id", is(taskId.intValue())));

        String storagePath = jdbc.queryForObject(
            "select storage_path from ai_video_result where task_id = ?",
            String.class,
            taskId
        );
        assert Files.exists(Path.of("target/test-video-storage", storagePath.substring(1)));
    }

    @Test
    void returnsExistingActiveTaskForDuplicateCreateRequest() throws Exception {
        String token = registerUser("13800016004", "Idempotent Owner");
        Long tenantId = createTenant(token, "幂等视频团队");
        Long ownerId = userIdByMobile("13800016004");
        Long projectId = createProject(token, tenantId, ownerId, "幂等视频项目", "AI_VIDEO_IDEMPOTENT");
        Long serviceConfigId = createVideoService(token, tenantId);
        Long storyboardId = createStoryboard(tenantId, projectId, ownerId);

        Long firstTaskId = createVideoTask(token, tenantId, projectId, storyboardId, serviceConfigId, "同一条视频提示词");
        Long secondTaskId = createVideoTask(token, tenantId, projectId, storyboardId, serviceConfigId, "同一条视频提示词");

        assert firstTaskId.equals(secondTaskId);
        Integer taskCount = jdbc.queryForObject(
            "select count(*) from ai_video_task where tenant_id = ? and project_id = ?",
            Integer.class,
            tenantId,
            projectId
        );
        assert taskCount == 1;
    }

    @Test
    void rejectsNewTaskWhenTenantConcurrencyLimitIsReached() throws Exception {
        String token = registerUser("13800016005", "Concurrency Owner");
        Long tenantId = createTenant(token, "并发视频团队");
        Long ownerId = userIdByMobile("13800016005");
        Long projectId = createProject(token, tenantId, ownerId, "并发视频项目", "AI_VIDEO_CONCURRENCY");
        Long serviceConfigId = createVideoService(token, tenantId);
        Long firstStoryboardId = createStoryboard(tenantId, projectId, ownerId);
        Long secondStoryboardId = createStoryboardWithShot(tenantId, projectId, ownerId, 2);
        createVideoTask(token, tenantId, projectId, firstStoryboardId, serviceConfigId, "第一条视频提示词");

        mockMvc.perform(post("/api/projects/%d/ai-video-tasks".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(videoTaskPayload(secondStoryboardId, serviceConfigId, "第二条视频提示词")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode", is("AI_VIDEO_CONCURRENCY_LIMIT_EXCEEDED")));
    }

    @Test
    void backgroundPollingCompletesDueGeneratingTasks() throws Exception {
        String token = registerUser("13800016006", "Polling Owner");
        Long tenantId = createTenant(token, "轮询视频团队");
        Long ownerId = userIdByMobile("13800016006");
        Long projectId = createProject(token, tenantId, ownerId, "轮询视频项目", "AI_VIDEO_POLLING");
        Long serviceConfigId = createVideoService(token, tenantId);
        Long storyboardId = createStoryboard(tenantId, projectId, ownerId);
        Long taskId = createVideoTask(token, tenantId, projectId, storyboardId, serviceConfigId, "后台轮询视频提示词");

        aiVideoTaskService.pollDueTasks();

        mockMvc.perform(get("/api/projects/%d/ai-video-tasks/%d".formatted(projectId, taskId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("SUCCEEDED")))
            .andExpect(jsonPath("$.data.results", hasSize(1)));
    }

    @Test
    void backgroundPollingFailsTimedOutTasks() throws Exception {
        String token = registerUser("13800016007", "Timeout Owner");
        Long tenantId = createTenant(token, "超时视频团队");
        Long ownerId = userIdByMobile("13800016007");
        Long projectId = createProject(token, tenantId, ownerId, "超时视频项目", "AI_VIDEO_TIMEOUT");
        Long serviceConfigId = createVideoService(token, tenantId);
        Long storyboardId = createStoryboard(tenantId, projectId, ownerId);
        Long taskId = createVideoTask(token, tenantId, projectId, storyboardId, serviceConfigId, "超时视频提示词");
        jdbc.update("""
            update ai_video_task
            set submitted_at = dateadd('minute', -25, now()),
                started_at = dateadd('minute', -25, now())
            where id = ?
            """, taskId);

        aiVideoTaskService.pollDueTasks();

        mockMvc.perform(get("/api/projects/%d/ai-video-tasks/%d".formatted(projectId, taskId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("FAILED")))
            .andExpect(jsonPath("$.data.errorMessage", containsString("超时")));
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
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
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
    }

    @Test
    void rejectsCreationWhenTenantHasNoVideoService() throws Exception {
        String token = registerUser("13800016003", "No Service Owner");
        Long tenantId = createTenant(token, "无视频服务团队");
        Long ownerId = userIdByMobile("13800016003");
        Long projectId = createProject(token, tenantId, ownerId, "无服务项目", "AI_VIDEO_NO_SERVICE");
        Long storyboardId = createStoryboard(tenantId, projectId, ownerId);

        mockMvc.perform(post("/api/projects/%d/ai-video-tasks".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
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
            .andExpect(jsonPath("$.errorCode", is("AI_VIDEO_SERVICE_UNAVAILABLE")));
    }

    private Long createStoryboard(Long tenantId, Long projectId, Long createdBy) {
        return createStoryboardWithShot(tenantId, projectId, createdBy, 1);
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
        MvcResult result = mockMvc.perform(post("/api/tenants/%d/ai-service-configs".formatted(tenantId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name":"默认视频服务",
                      "serviceType":"VIDEO",
                      "provider":"火山",
                      "baseUrl":"https://example.com/v1",
                      "apiKey":"sk-test-1234",
                      "model":"seedance-test",
                      "endpoint":"/video/generations",
                      "queryEndpoint":"/video/tasks",
                      "priority":100,
                      "isDefault":true,
                      "enabled":true,
                      "remark":"测试视频服务"
                    }
                    """))
            .andExpect(status().isOk())
            .andReturn();
        return readLong(result, "$.data.id");
    }

    private Long createVideoTask(
        String token,
        Long tenantId,
        Long projectId,
        Long storyboardId,
        Long serviceConfigId,
        String prompt
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects/%d/ai-video-tasks".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(videoTaskPayload(storyboardId, serviceConfigId, prompt)))
            .andExpect(status().isOk())
            .andReturn();
        return readLong(result, "$.data.id");
    }

    private String videoTaskPayload(Long storyboardId, Long serviceConfigId, String prompt) {
        return """
            {
              "storyboardId":%d,
              "serviceConfigId":%d,
              "prompt":"%s",
              "firstFrameUrl":"https://cdn.example.com/first-frame.jpg",
              "durationSeconds":5,
              "aspectRatio":"9:16",
              "resolution":"STANDARD",
              "cameraMovement":"PUSH_IN",
              "motionStrength":"MEDIUM"
            }
            """.formatted(storyboardId, serviceConfigId, prompt);
    }

    private String registerUser(String mobile, String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mobile":"%s","verificationCode":"123456","nickname":"%s","password":"Password123"}
                    """.formatted(mobile, nickname)))
            .andExpect(status().isOk())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private Long createTenant(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tenants")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
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
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
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
}
