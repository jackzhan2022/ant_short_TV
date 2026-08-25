package com.antshorttv.aiimage;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.antshorttv.user.UserEntity;
import com.antshorttv.user.UserMapper;
import com.antshorttv.execution.AiExecutionDispatcher;
import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "ai.execution.dispatcher.enabled=false")
@AutoConfigureMockMvc
class AiImageTaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AiExecutionDispatcher executionDispatcher;

    @Test
    void createsOneDurableExecutionForDuplicateImageRequests() throws Exception {
        String token = registerUser("13800014006", "Idempotent Image Creator");
        Long tenantId = createTenant(token, "幂等图片团队");
        Long ownerId = userIdByMobile("13800014006");
        Long projectId = createProject(token, tenantId, ownerId, "幂等图片项目", "IMAGE_TASK_IDEMPOTENT");
        createImageService(token, tenantId);
        grantTeamPoints(tenantId, 5);
        String body = """
            {"taskType":"CHARACTER","targetType":"CHARACTER","targetId":1,"prompt":"角色立绘","aspectRatio":"3:4","imageCount":1}
            """;

        MvcResult first = createImageTask(token, tenantId, projectId, "image-create-duplicate", body);
        MvcResult duplicate = createImageTask(token, tenantId, projectId, "image-create-duplicate", body);

        Long taskId = readLong(first, "$.data.id");
        Long executionId = readLong(first, "$.data.executionId");
        org.assertj.core.api.Assertions.assertThat(readLong(duplicate, "$.data.id")).isEqualTo(taskId);
        org.assertj.core.api.Assertions.assertThat(readLong(duplicate, "$.data.executionId")).isEqualTo(executionId);
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
            "select count(*) from ai_image_task where tenant_id = ? and client_idempotency_key = ?",
            Integer.class,
            tenantId,
            "image-create-duplicate"
        )).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(executionDispatcher.eligibleExecutionIds(
            java.time.LocalDateTime.now().plusSeconds(1),
            20
        )).contains(executionId);
    }

    @Test
    void releasesReservationWhenImageTaskIsCanceledBeforeProviderCall() throws Exception {
        String token = registerUser("13800014007", "Pre-call Image Canceler");
        Long tenantId = createTenant(token, "调用前取消团队");
        Long ownerId = userIdByMobile("13800014007");
        Long projectId = createProject(token, tenantId, ownerId, "调用前取消项目", "IMAGE_TASK_PRECALL_CANCEL");
        createImageService(token, tenantId);
        grantTeamPoints(tenantId, 5);
        MvcResult created = createImageTask(token, tenantId, projectId, "image-precall-cancel", """
            {"taskType":"CHARACTER","targetType":"CHARACTER","targetId":1,"prompt":"取消角色立绘","aspectRatio":"3:4","imageCount":1}
            """);
        Long taskId = readLong(created, "$.data.id");
        Long executionId = readLong(created, "$.data.executionId");

        mockMvc.perform(put("/api/projects/%d/ai-image-tasks/%d/cancel".formatted(projectId, taskId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.execution.status", is("CANCELED")));

        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
            "select status from ai_point_reservation where execution_id = ?",
            String.class,
            executionId
        )).isEqualTo("RELEASED");
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
            "select count(*) from ai_call_log where execution_id = ?",
            Integer.class,
            executionId
        )).isZero();
    }

    @Test
    void regeneratesIntoNewDomainTaskAndNextExecutionVersion() throws Exception {
        String token = registerUser("13800014008", "Image Regenerator");
        Long tenantId = createTenant(token, "图片再生成团队");
        Long ownerId = userIdByMobile("13800014008");
        Long projectId = createProject(token, tenantId, ownerId, "图片再生成项目", "IMAGE_TASK_REGENERATE");
        createImageService(token, tenantId);
        grantTeamPoints(tenantId, 5);
        MvcResult created = createImageTask(token, tenantId, projectId, "image-regenerate-source", """
            {"taskType":"CHARACTER","targetType":"CHARACTER","targetId":1,"prompt":"角色立绘","aspectRatio":"3:4","imageCount":1}
            """);
        Long sourceTaskId = readLong(created, "$.data.id");
        Long sourceExecutionId = readLong(created, "$.data.executionId");
        waitForTaskSuccess(token, tenantId, projectId, sourceTaskId);

        MvcResult regenerated = regenerateImageTask(
            token, tenantId, projectId, sourceTaskId, "image-regenerate-v2"
        );
        MvcResult duplicate = regenerateImageTask(
            token, tenantId, projectId, sourceTaskId, "image-regenerate-v2"
        );
        Long regeneratedTaskId = readLong(regenerated, "$.data.id");
        Long regeneratedExecutionId = readLong(regenerated, "$.data.executionId");

        org.assertj.core.api.Assertions.assertThat(regeneratedTaskId).isNotEqualTo(sourceTaskId);
        org.assertj.core.api.Assertions.assertThat(readLong(duplicate, "$.data.id")).isEqualTo(regeneratedTaskId);
        org.assertj.core.api.Assertions.assertThat(readLong(duplicate, "$.data.executionId")).isEqualTo(regeneratedExecutionId);
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForMap("""
            select execution_version, source_execution_id, root_execution_id
              from ai_execution_task
             where id = ?
            """, regeneratedExecutionId))
            .containsEntry("EXECUTION_VERSION", 2)
            .containsEntry("SOURCE_EXECUTION_ID", sourceExecutionId)
            .containsEntry("ROOT_EXECUTION_ID", sourceExecutionId);
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
            "select count(*) from ai_point_reservation where execution_id in (?, ?)",
            Integer.class,
            sourceExecutionId,
            regeneratedExecutionId
        )).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
            "select count(*) from ai_image_result where task_id = ? and status = 'ACTIVE'",
            Integer.class,
            sourceTaskId
        )).isEqualTo(1);
    }

    @Test
    void rejectsTaskWhenTenantHasNoImageService() throws Exception {
        String token = registerUser("13800014001", "Image Owner");
        Long tenantId = createTenant(token, "图片生成团队");
        Long ownerId = userIdByMobile("13800014001");
        Long projectId = createProject(token, tenantId, ownerId, "图片生成项目", "IMAGE_TASK_NO_SERVICE");
        jdbcTemplate.update("delete from ai_service_config where service_type = 'IMAGE'");

        mockMvc.perform(post("/api/projects/%d/ai-image-tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"taskType":"CHARACTER","targetType":"CHARACTER","targetId":1,"prompt":"赛博短剧女主角色立绘","aspectRatio":"3:4","imageCount":1}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode", is("AI_IMAGE_SERVICE_UNAVAILABLE")));
    }

    @Test
    void rejectsImageTaskWhenTeamPointsAreInsufficient() throws Exception {
        String token = registerUser("13800014004", "Image Point Owner");
        Long tenantId = createTenant(token, "图片积分团队");
        Long ownerId = userIdByMobile("13800014004");
        Long projectId = createProject(token, tenantId, ownerId, "图片积分项目", "IMAGE_TASK_NO_POINTS");
        createImageService(token, tenantId);

        mockMvc.perform(post("/api/projects/%d/ai-image-tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"taskType":"CHARACTER","targetType":"CHARACTER","targetId":1,"prompt":"赛博短剧女主角色立绘","aspectRatio":"3:4","imageCount":1}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode", is("TEAM_POINTS_INSUFFICIENT")));
    }

    @Test
    void createsImageTaskAndManagesGeneratedResult() throws Exception {
        String token = registerUser("13800014002", "Image Creator");
        Long tenantId = createTenant(token, "图片结果团队");
        Long ownerId = userIdByMobile("13800014002");
        Long projectId = createProject(token, tenantId, ownerId, "首帧项目", "IMAGE_TASK_RESULT");
        Long storyboardId = createStoryboard(tenantId, projectId, ownerId);
        createImageService(token, tenantId);
        grantTeamPoints(tenantId, 5);

        MvcResult created = mockMvc.perform(post("/api/projects/%d/ai-image-tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "taskType":"STORYBOARD_FIRST_FRAME",
                      "targetType":"STORYBOARD",
                      "targetId":%d,
                      "prompt":"竖屏短剧首帧，雨夜豪门门口，女主拖着行李箱回归",
                      "negativePrompt":"低清晰度，畸形手",
                      "aspectRatio":"9:16",
                      "imageCount":2,
                      "style":"电影感",
                      "quality":"HD"
                    }
                    """.formatted(storyboardId)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.status", is("PENDING")))
            .andExpect(jsonPath("$.data.results", hasSize(0)))
            .andReturn();

        Long taskId = readLong(created, "$.data.id");
        MvcResult completed = waitForTaskSuccess(token, tenantId, projectId, taskId);
        Long resultId = readLong(completed, "$.data.results[0].id");
        String imageUrl = JsonPath.read(completed.getResponse().getContentAsString(), "$.data.results[0].imageUrl");

        mockMvc.perform(get("/api/projects/%d/ai-image-tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].id", is(taskId.intValue())));

        mockMvc.perform(get("/api/projects/%d/ai-image-results/%d/download".formatted(projectId, resultId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.IMAGE_PNG));

        mockMvc.perform(post("/api/projects/%d/ai-image-results/%d/save-material".formatted(projectId, resultId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.materialId", notNullValue()));

        mockMvc.perform(put("/api/projects/%d/ai-image-results/%d/selected".formatted(projectId, resultId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.selected", is(true)));

        String selectedImage = jdbcTemplate.queryForObject(
            "select first_frame_image_url from storyboard where id = ?",
            String.class,
            storyboardId
        );
        Integer callLogCount = jdbcTemplate.queryForObject(
            "select count(*) from ai_call_log where tenant_id = ? and business_scene = ? and status = ?",
            Integer.class,
            tenantId,
            "STORYBOARD_FIRST_FRAME",
            "SUCCESS"
        );

        org.assertj.core.api.Assertions.assertThat(selectedImage).isEqualTo(imageUrl);
        org.assertj.core.api.Assertions.assertThat(callLogCount).isGreaterThanOrEqualTo(1);

        mockMvc.perform(delete("/api/projects/%d/ai-image-results/%d".formatted(projectId, resultId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode", is("AI_IMAGE_RESULT_IN_USE")));

        mockMvc.perform(delete("/api/projects/%d/ai-image-results/%d?force=true".formatted(projectId, resultId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/api/projects/%d/ai-image-tasks/%d".formatted(projectId, taskId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk());
    }

    @Test
    void storesProviderImageUrlWhenGatewayReturnsRealImages() throws Exception {
        String token = registerUser("13800014005", "Real Image Creator");
        Long tenantId = createTenant(token, "真实图片团队");
        Long ownerId = userIdByMobile("13800014005");
        Long projectId = createProject(token, tenantId, ownerId, "真实图片项目", "IMAGE_TASK_REAL_PROVIDER");
        Long storyboardId = createStoryboard(tenantId, projectId, ownerId);
        grantTeamPoints(tenantId, 5);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/images/generations", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = """
                {"data":[{"url":"https://cdn.example.com/generated-first-frame.png"}]}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            createImageService(token, tenantId, "http://127.0.0.1:%d/v1".formatted(server.getAddress().getPort()), "sk-real-image");
            MvcResult created = mockMvc.perform(post("/api/projects/%d/ai-image-tasks".formatted(projectId))
                    .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                    .header("X-Tenant-Id", tenantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "taskType":"STORYBOARD_FIRST_FRAME",
                          "targetType":"STORYBOARD",
                          "targetId":%d,
                          "prompt":"真实 Provider 首帧",
                          "aspectRatio":"9:16",
                          "imageCount":1
                        }
                        """.formatted(storyboardId)))
                .andExpect(status().isAccepted())
                .andReturn();

            Long taskId = readLong(created, "$.data.id");
            MvcResult completed = waitForTaskSuccess(token, tenantId, projectId, taskId);

            String imageUrl = JsonPath.read(completed.getResponse().getContentAsString(), "$.data.results[0].imageUrl");
            org.assertj.core.api.Assertions.assertThat(imageUrl).isEqualTo("https://cdn.example.com/generated-first-frame.png");
            Long executionId = readLong(completed, "$.data.executionId");
            Long callLogExecutionId = jdbcTemplate.queryForObject("""
                select log.execution_id
                  from ai_image_task task
                  join ai_call_log log on log.id = task.ai_call_log_id
                 where task.id = ?
                """, Long.class, taskId);
            org.assertj.core.api.Assertions.assertThat(callLogExecutionId).isEqualTo(executionId);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void keepsRunningTaskCanceledWhenExecutorCompletesLater() throws Exception {
        String token = registerUser("13800014003", "Image Canceler");
        Long tenantId = createTenant(token, "图片取消团队");
        Long ownerId = userIdByMobile("13800014003");
        Long projectId = createProject(token, tenantId, ownerId, "取消项目", "IMAGE_TASK_CANCEL");
        Long storyboardId = createStoryboard(tenantId, projectId, ownerId);
        createImageService(token, tenantId);
        grantTeamPoints(tenantId, 5);

        MvcResult created = mockMvc.perform(post("/api/projects/%d/ai-image-tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "taskType":"STORYBOARD_FIRST_FRAME",
                      "targetType":"STORYBOARD",
                      "targetId":%d,
                      "prompt":"取消中的首帧任务",
                      "aspectRatio":"9:16",
                      "imageCount":1
                    }
                    """.formatted(storyboardId)))
            .andExpect(status().isAccepted())
            .andReturn();
        Long taskId = readLong(created, "$.data.id");

        waitForTaskStatus(token, tenantId, projectId, taskId, "RUNNING");
        mockMvc.perform(put("/api/projects/%d/ai-image-tasks/%d/cancel".formatted(projectId, taskId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("CANCELED")));
        Thread.sleep(400);

        mockMvc.perform(get("/api/projects/%d/ai-image-tasks/%d".formatted(projectId, taskId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("CANCELED")))
            .andExpect(jsonPath("$.data.results", hasSize(0)));
    }

    private MvcResult waitForTaskSuccess(String token, Long tenantId, Long projectId, Long taskId) throws Exception {
        return waitForTaskStatus(token, tenantId, projectId, taskId, "SUCCESS");
    }

    private MvcResult waitForTaskStatus(String token, Long tenantId, Long projectId, Long taskId, String expectedStatus) throws Exception {
        MvcResult last = null;
        for (int i = 0; i < 20; i++) {
            executionDispatcher.dispatchOnce();
            last = mockMvc.perform(get("/api/projects/%d/ai-image-tasks/%d".formatted(projectId, taskId))
                    .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                    .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andReturn();
            String status = JsonPath.read(last.getResponse().getContentAsString(), "$.data.status");
            if (expectedStatus.equals(status)) {
                return last;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("图片任务未在预期时间内进入 %s：%s".formatted(expectedStatus, last.getResponse().getContentAsString()));
    }

    private MvcResult createImageTask(
        String token,
        Long tenantId,
        Long projectId,
        String idempotencyKey,
        String body
    ) throws Exception {
        return mockMvc.perform(post("/api/projects/%d/ai-image-tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.execution.status", is("PENDING")))
            .andReturn();
    }

    private MvcResult regenerateImageTask(
        String token,
        Long tenantId,
        Long projectId,
        Long taskId,
        String idempotencyKey
    ) throws Exception {
        return mockMvc.perform(post("/api/projects/%d/ai-image-tasks/%d/regenerate".formatted(projectId, taskId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .header("Idempotency-Key", idempotencyKey))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.execution.status", is("PENDING")))
            .andReturn();
    }

    private void createImageService(String token, Long tenantId) throws Exception {
        createImageService(token, tenantId, "https://api.openai.com/v1", "test-key");
    }

    private void createImageService(String token, Long tenantId, String baseUrl, String apiKey) throws Exception {
        mockMvc.perform(post("/api/tenants/%d/ai-service-configs".formatted(tenantId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name":"默认图片服务",
                      "serviceType":"IMAGE",
                      "provider":"OpenAI",
                      "baseUrl":"%s",
                      "apiKey":"%s",
                      "model":"local-image-model",
                      "endpoint":"/images/generations",
                      "priority":100,
                      "isDefault":true,
                      "enabled":true
                    }
                    """.formatted(baseUrl, apiKey)))
            .andExpect(status().isOk());
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
                    {"name":"%s","type":"STUDIO","description":"图片任务测试"}
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
                    {"name":"%s","code":"%s","description":"图片任务项目","ownerId":%d}
                    """.formatted(name, code, ownerId)))
            .andExpect(status().isOk())
            .andReturn();
        return readLong(result, "$.data.id");
    }

    private void grantTeamPoints(Long tenantId, int amount) {
        jdbcTemplate.update("""
            insert into team_point_account
              (tenant_id, balance, total_granted, total_consumed, created_at, updated_at)
            values (?, ?, ?, 0, now(), now())
            """, tenantId, amount, amount);
    }

    private Long createStoryboard(Long tenantId, Long projectId, Long userId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                insert into storyboard
                  (tenant_id, project_id, episode_no, shot_no, visual_description, status, created_by, created_at, updated_at)
                values (?, ?, 1, 1, '雨夜豪门门口，女主拖着行李箱回归', 'CONFIRMED', ?, now(), now())
                """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, tenantId);
            statement.setLong(2, projectId);
            statement.setLong(3, userId);
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
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
