package com.antshorttv.ai;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AiCallLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void listsTenantScopedAiCallLogsWithFiltersAndPagination() throws Exception {
        String token = registerUser("13800015001", "Call Log Owner");
        Long tenantId = createTenant(token, "AI调用日志团队");
        Long configId = createConfig(token, tenantId);

        insertCallLog(tenantId, 1L, configId, "OpenAI", "TEXT", "test-model", "chatbot", "你好", "你好，我可以帮你。", "SUCCESS", null, 128);
        insertCallLog(tenantId, 1L, configId, "OpenAI", "IMAGE", "test-model", "image_generate", "生成海报", null, "FAILED", "模型不可用", 240);
        insertCallLog(tenantId + 999, 1L, configId, "OpenAI", "TEXT", "test-model", "chatbot", "其他团队", "不应返回", "SUCCESS", null, 99);

        mockMvc.perform(get("/api/tenants/%d/ai-call-logs".formatted(tenantId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .param("current", "1")
                .param("pageSize", "10")
                .param("serviceType", "TEXT")
                .param("status", "SUCCESS")
                .param("businessScene", "chatbot"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total", is(1)))
            .andExpect(jsonPath("$.data.current", is(1)))
            .andExpect(jsonPath("$.data.pageSize", is(10)))
            .andExpect(jsonPath("$.data.records", hasSize(1)))
            .andExpect(jsonPath("$.data.records[0].serviceConfigName", is("OpenAI 文本服务")))
            .andExpect(jsonPath("$.data.records[0].businessScene", is("chatbot")))
            .andExpect(jsonPath("$.data.records[0].requestSummary", is("你好")))
            .andExpect(jsonPath("$.data.records[0].responseSummary", is("你好，我可以帮你。")))
            .andExpect(jsonPath("$.data.records[0].status", is("SUCCESS")));
    }

    private Long createConfig(String token, Long tenantId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tenants/%d/ai-service-configs".formatted(tenantId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name":"OpenAI 文本服务",
                      "serviceType":"TEXT",
                      "provider":"OpenAI",
                      "baseUrl":"https://example.com/v1",
                      "apiKey":"sk-test-1234",
                      "model":"test-model",
                      "endpoint":"/chat/completions",
                      "priority":100,
                      "isDefault":true,
                      "enabled":true
                    }
                    """))
            .andExpect(status().isOk())
            .andReturn();
        return readLong(result, "$.data.id");
    }

    private void insertCallLog(
        Long tenantId,
        Long userId,
        Long configId,
        String provider,
        String serviceType,
        String model,
        String businessScene,
        String requestSummary,
        String responseSummary,
        String status,
        String errorMessage,
        long durationMs
    ) {
        jdbcTemplate.update("""
            insert into ai_call_log
              (tenant_id, user_id, service_config_id, provider, service_type, model, business_scene, request_summary, response_summary, status, error_message, duration_ms, created_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            """, tenantId, userId, configId, provider, serviceType, model, businessScene, requestSummary, responseSummary, status, errorMessage, durationMs);
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
                    {"name":"%s","type":"STUDIO","description":"AI调用日志测试"}
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
