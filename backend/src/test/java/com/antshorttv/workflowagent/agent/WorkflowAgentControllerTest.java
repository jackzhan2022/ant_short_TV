package com.antshorttv.workflowagent.agent;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.antshorttv.support.SessionTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
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
class WorkflowAgentControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void catalogRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/platform/ai/workflow-agents"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/platform/ai/agent-tools"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void tenantUserCannotViewIndependentAgents() throws Exception {
        String credential = register("13800000882", "Tenant Agent User");
        mockMvc.perform(get("/api/platform/ai/workflow-agents")
                .with(SessionTestSupport.authenticated(credential)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode", is("FORBIDDEN")));
        mockMvc.perform(get("/api/platform/ai/agent-tools")
                .with(SessionTestSupport.authenticated(credential)))
            .andExpect(status().isForbidden());
    }

    @Test
    void platformAdministratorCanCreateUpdateCopyToggleListAndDeleteAgent() throws Exception {
        String credential = register("13800000999", "Platform Admin");
        long modelId = model();
        String code = "controller-agent-" + UUID.randomUUID();
        String copiedCode = code + "-copy";
        mockMvc.perform(get("/api/platform/ai/agent-tools")
                .with(SessionTestSupport.authenticated(credential)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()", is(9)));
        MvcResult created = mockMvc.perform(post("/api/platform/ai/workflow-agents")
                .with(SessionTestSupport.authenticated(credential))
                .contentType(MediaType.APPLICATION_JSON)
                .content(agentJson(code, "First", modelId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.code", is(code)))
            .andExpect(jsonPath("$.data.status", is("ENABLED")))
            .andExpect(jsonPath("$.data.skillCodes.length()", is(0)))
            .andReturn();
        Number revision = com.jayway.jsonpath.JsonPath.read(
            created.getResponse().getContentAsString(), "$.data.revision");

        mockMvc.perform(put("/api/platform/ai/workflow-agents/{code}", code)
                .with(SessionTestSupport.authenticated(credential))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson("Second", modelId, revision.longValue())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.code", is(code)))
            .andExpect(jsonPath("$.data.name", is("Second")));

        mockMvc.perform(post("/api/platform/ai/workflow-agents/{code}/copy", code)
                .with(SessionTestSupport.authenticated(credential))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("targetCode", copiedCode))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.code", is(copiedCode)));

        mockMvc.perform(post("/api/platform/ai/workflow-agents/{code}/disable", code)
                .with(SessionTestSupport.authenticated(credential)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("DISABLED")));
        mockMvc.perform(post("/api/platform/ai/workflow-agents/{code}/enable", code)
                .with(SessionTestSupport.authenticated(credential)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("ENABLED")));

        mockMvc.perform(get("/api/platform/ai/workflow-agents").queryParam("query", code)
                .with(SessionTestSupport.authenticated(credential)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()", is(2)));

        mockMvc.perform(delete("/api/platform/ai/workflow-agents/{code}", copiedCode)
                .with(SessionTestSupport.authenticated(credential)))
            .andExpect(status().isOk());
        mockMvc.perform(delete("/api/platform/ai/workflow-agents/{code}", code)
                .with(SessionTestSupport.authenticated(credential)))
            .andExpect(status().isOk());
    }

    private String register(String mobile, String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mobile":"%s","verificationCode":"123456","nickname":"%s","password":"Password123"}
                    """.formatted(mobile, nickname)))
            .andExpect(status().isOk())
            .andReturn();
        return SessionTestSupport.sessionCredential(result);
    }

    private String agentJson(String code, String name, long modelId) throws Exception {
        return objectMapper.writeValueAsString(Map.ofEntries(
            Map.entry("code", code), Map.entry("name", name), Map.entry("description", "test"),
            Map.entry("systemPrompt", "Complete the input"), Map.entry("modelId", modelId),
            Map.entry("temperature", 0.7), Map.entry("maxTokens", 4096),
            Map.entry("maxSteps", 10), Map.entry("status", "ENABLED"),
            Map.entry("skillCodes", java.util.List.of()), Map.entry("toolCodes", java.util.List.of())));
    }

    private String updateJson(String name, long modelId, long revision) throws Exception {
        return objectMapper.writeValueAsString(Map.ofEntries(
            Map.entry("name", name), Map.entry("description", "updated"),
            Map.entry("systemPrompt", "Complete the updated input"), Map.entry("modelId", modelId),
            Map.entry("temperature", 0.6), Map.entry("maxTokens", 2048),
            Map.entry("maxSteps", 8), Map.entry("status", "ENABLED"),
            Map.entry("skillCodes", java.util.List.of()), Map.entry("toolCodes", java.util.List.of()),
            Map.entry("expectedRevision", revision)));
    }

    private long model() {
        Long providerId = jdbc.queryForObject("select min(id) from ai_provider", Long.class);
        String code = "controller-model-" + UUID.randomUUID();
        jdbc.update("""
            insert into ai_model
              (provider_id, code, name, model_code, service_type, status, is_default, sort, created_at, updated_at)
            values (?, ?, ?, ?, 'TEXT', 'ENABLED', false, 999, now(), now())
            """, providerId, code, code, code);
        Long modelId = jdbc.queryForObject("select id from ai_model where code = ?", Long.class, code);
        jdbc.update("""
            insert into ai_model_capability
              (model_id, capability, status, created_at, updated_at)
            values (?, 'TOOL_CALLING', 'ENABLED', now(), now())
            """, modelId);
        return modelId;
    }
}
