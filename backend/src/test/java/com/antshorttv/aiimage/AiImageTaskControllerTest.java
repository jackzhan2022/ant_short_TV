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
import com.jayway.jsonpath.JsonPath;
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

@SpringBootTest
@AutoConfigureMockMvc
class AiImageTaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void rejectsTaskWhenTenantHasNoImageService() throws Exception {
        String token = registerUser("13800014001", "Image Owner");
        Long tenantId = createTenant(token, "图片生成团队");
        Long ownerId = userIdByMobile("13800014001");
        Long projectId = createProject(token, tenantId, ownerId, "图片生成项目", "IMAGE_TASK_NO_SERVICE");

        mockMvc.perform(post("/api/projects/%d/ai-image-tasks".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"taskType":"CHARACTER","targetType":"CHARACTER","targetId":1,"prompt":"赛博短剧女主角色立绘","aspectRatio":"3:4","imageCount":1}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode", is("AI_IMAGE_SERVICE_UNAVAILABLE")));
    }

    @Test
    void createsImageTaskAndManagesGeneratedResult() throws Exception {
        String token = registerUser("13800014002", "Image Creator");
        Long tenantId = createTenant(token, "图片结果团队");
        Long ownerId = userIdByMobile("13800014002");
        Long projectId = createProject(token, tenantId, ownerId, "首帧项目", "IMAGE_TASK_RESULT");
        Long storyboardId = createStoryboard(tenantId, projectId, ownerId);
        createImageService(token, tenantId);

        MvcResult created = mockMvc.perform(post("/api/projects/%d/ai-image-tasks".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
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
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("PENDING")))
            .andExpect(jsonPath("$.data.results", hasSize(0)))
            .andReturn();

        Long taskId = readLong(created, "$.data.id");
        MvcResult completed = waitForTaskSuccess(token, tenantId, projectId, taskId);
        Long resultId = readLong(completed, "$.data.results[0].id");
        String imageUrl = JsonPath.read(completed.getResponse().getContentAsString(), "$.data.results[0].imageUrl");

        mockMvc.perform(get("/api/projects/%d/ai-image-tasks".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].id", is(taskId.intValue())));

        mockMvc.perform(get("/api/projects/%d/ai-image-results/%d/download".formatted(projectId, resultId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.IMAGE_PNG));

        mockMvc.perform(post("/api/projects/%d/ai-image-results/%d/save-material".formatted(projectId, resultId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.materialId", notNullValue()));

        mockMvc.perform(put("/api/projects/%d/ai-image-results/%d/selected".formatted(projectId, resultId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
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
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode", is("AI_IMAGE_RESULT_IN_USE")));

        mockMvc.perform(delete("/api/projects/%d/ai-image-results/%d?force=true".formatted(projectId, resultId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/api/projects/%d/ai-image-tasks/%d".formatted(projectId, taskId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk());
    }

    @Test
    void keepsRunningTaskCanceledWhenExecutorCompletesLater() throws Exception {
        String token = registerUser("13800014003", "Image Canceler");
        Long tenantId = createTenant(token, "图片取消团队");
        Long ownerId = userIdByMobile("13800014003");
        Long projectId = createProject(token, tenantId, ownerId, "取消项目", "IMAGE_TASK_CANCEL");
        Long storyboardId = createStoryboard(tenantId, projectId, ownerId);
        createImageService(token, tenantId);

        MvcResult created = mockMvc.perform(post("/api/projects/%d/ai-image-tasks".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
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
            .andExpect(status().isOk())
            .andReturn();
        Long taskId = readLong(created, "$.data.id");

        waitForTaskStatus(token, tenantId, projectId, taskId, "RUNNING");
        mockMvc.perform(put("/api/projects/%d/ai-image-tasks/%d/cancel".formatted(projectId, taskId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("CANCELED")));
        Thread.sleep(400);

        mockMvc.perform(get("/api/projects/%d/ai-image-tasks/%d".formatted(projectId, taskId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
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
            last = mockMvc.perform(get("/api/projects/%d/ai-image-tasks/%d".formatted(projectId, taskId))
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
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

    private void createImageService(String token, Long tenantId) throws Exception {
        mockMvc.perform(post("/api/tenants/%d/ai-service-configs".formatted(tenantId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name":"默认图片服务",
                      "serviceType":"IMAGE",
                      "provider":"OpenAI",
                      "baseUrl":"https://api.openai.com/v1",
                      "apiKey":"test-key",
                      "model":"local-image-model",
                      "endpoint":"/images/generations",
                      "priority":100,
                      "isDefault":true,
                      "enabled":true
                    }
                    """))
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
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private Long createTenant(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tenants")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
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
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"%s","code":"%s","description":"图片任务项目","ownerId":%d}
                    """.formatted(name, code, ownerId)))
            .andExpect(status().isOk())
            .andReturn();
        return readLong(result, "$.data.id");
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
