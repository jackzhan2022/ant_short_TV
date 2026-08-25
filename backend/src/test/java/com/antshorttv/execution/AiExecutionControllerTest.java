package com.antshorttv.execution;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AiExecutionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AiExecutionService executionService;

    @Autowired
    private AiExecutionTaskMapper taskMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void returnsCanonicalTaskDetailAndRequiresAuthentication() throws Exception {
        String token = registerUser("13800019901", "Execution Owner");
        Long userId = userId("13800019901");
        Long tenantId = createTenant(token, "执行任务团队");
        AiExecutionTaskEntity task = executionService.create(command(tenantId, userId, null, "detail-task"));

        mockMvc.perform(get(path(tenantId, task.id)))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get(path(tenantId, task.id))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token)))
            .andExpect(status().isOk())
            .andExpect(header().string("X-AI-Task-Contract-Version", "1"))
            .andExpect(jsonPath("$.data.id", is(task.id.intValue())))
            .andExpect(jsonPath("$.data.status", is("PENDING")))
            .andExpect(jsonPath("$.data.phase", is("SUBMIT")))
            .andExpect(jsonPath("$.data.progress", is(0)))
            .andExpect(jsonPath("$.data.usageCostStatus", is("PENDING")))
            .andExpect(jsonPath("$.data.pointSettlementStatus", is("PENDING")))
            .andExpect(jsonPath("$.data.reservedPoints", is(0.0)));
    }

    @Test
    void rejectsCrossTenantAndInaccessibleProjectTasks() throws Exception {
        String ownerToken = registerUser("13800019902", "Task Tenant Owner");
        Long ownerId = userId("13800019902");
        Long ownerTenantId = createTenant(ownerToken, "任务所属团队");
        AiExecutionTaskEntity task = executionService.create(command(ownerTenantId, ownerId, null, "tenant-isolation"));

        String otherToken = registerUser("13800019903", "Other Tenant Owner");
        Long otherTenantId = createTenant(otherToken, "其他团队");
        mockMvc.perform(get(path(otherTenantId, task.id))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(otherToken)))
            .andExpect(status().isForbidden());

        AiExecutionTaskEntity inaccessibleProject = executionService.create(command(
            ownerTenantId, ownerId, 999999L, "project-isolation"
        ));
        mockMvc.perform(get(path(ownerTenantId, inaccessibleProject.id))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(ownerToken)))
            .andExpect(status().isForbidden());

        jdbc.update("""
            insert into tenant_member (tenant_id, user_id, member_type, status, joined_at, created_at, updated_at)
            values (?, ?, 'MEMBER', 'ACTIVE', current_timestamp, current_timestamp, current_timestamp)
            """, ownerTenantId, userId("13800019903"));
        mockMvc.perform(get(path(ownerTenantId, task.id))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(otherToken)))
            .andExpect(status().isOk());
        mockMvc.perform(post(path(ownerTenantId, task.id) + "/cancel")
                .with(com.antshorttv.support.SessionTestSupport.authenticated(otherToken)))
            .andExpect(status().isForbidden());
    }

    @Test
    void cancelsRetriesAndRegeneratesThroughCanonicalControls() throws Exception {
        String token = registerUser("13800019904", "Task Controller");
        Long userId = userId("13800019904");
        Long tenantId = createTenant(token, "任务控制团队");

        AiExecutionTaskEntity pending = executionService.create(command(tenantId, userId, null, "cancel-task"));
        mockMvc.perform(post(path(tenantId, pending.id) + "/cancel")
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("CANCELED")));

        AiExecutionTaskEntity failed = executionService.create(command(tenantId, userId, null, "retry-task"));
        taskMapper.update(null, new UpdateWrapper<AiExecutionTaskEntity>()
            .set("status", "FAILED")
            .set("retryable", true)
            .eq("id", failed.id));
        mockMvc.perform(post(path(tenantId, failed.id) + "/retry")
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("PENDING")));

        AiExecutionTaskEntity succeeded = executionService.create(command(tenantId, userId, null, "regenerate-source"));
        taskMapper.update(null, new UpdateWrapper<AiExecutionTaskEntity>()
            .set("status", "SUCCEEDED")
            .set("result_type", "SCRIPT_VERSION")
            .set("result_id", 88L)
            .eq("id", succeeded.id));
        mockMvc.perform(post(path(tenantId, succeeded.id) + "/regenerate")
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientIdempotencyKey\":\"regenerated-task\",\"traceId\":\"trace-regenerated-task\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("PENDING")))
            .andExpect(jsonPath("$.data.executionVersion", is(2)));
    }

    private AiExecutionCreateCommand command(
        Long tenantId,
        Long userId,
        Long projectId,
        String key
    ) {
        return new AiExecutionCreateCommand(
            tenantId, userId, projectId, "script_generate", "TEXT", "SCRIPT_OPERATION", 1L,
            null, "SUBMIT", key, "trace-" + key, true, null
        );
    }

    private String path(Long tenantId, Long executionId) {
        return "/api/tenants/%d/ai-executions/%d".formatted(tenantId, executionId);
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
                .content("{\"name\":\"%s\",\"type\":\"STUDIO\",\"description\":\"执行测试\"}".formatted(name)))
            .andExpect(status().isOk())
            .andReturn();
        Number value = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return value.longValue();
    }

    private Long userId(String mobile) {
        return jdbc.queryForObject("select id from app_user where mobile = ?", Long.class, mobile);
    }
}
