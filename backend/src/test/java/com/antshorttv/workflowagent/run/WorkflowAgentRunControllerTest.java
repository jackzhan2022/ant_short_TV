package com.antshorttv.workflowagent.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.antshorttv.support.SessionTestSupport;
import com.antshorttv.security.RequestTenantContextResolver;
import com.antshorttv.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class WorkflowAgentRunControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper json;
    @MockBean
    private WorkflowAgentRunner runner;
    @MockBean
    private WorkflowAgentRunRepository runs;
    @MockBean
    private RequestTenantContextResolver tenantContextResolver;

    @Test
    void runAndAuditEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/platform/ai/workflow-agent-runs"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void platformAdministratorCanStartFormalAndTemporaryRunsAndInspectAudit() throws Exception {
        String credential = register("13800000999", "Workflow Run Admin");
        when(runner.runFormal(any())).thenReturn(new WorkflowAgentRunResult(101L, "正式结果"));
        when(runner.runTest(any(), any())).thenReturn(new WorkflowAgentRunResult(102L, "测试结果"));
        when(runs.list(7L, "screenplay-agent", 20)).thenReturn(List.of(new WorkflowAgentRunSummary(
            101L, "screenplay-agent", "FORMAL", "SUCCESS", 25L, 91L, "正式结果", null, null,
            LocalDateTime.now(), LocalDateTime.now()
        )));
        when(tenantContextResolver.require(any())).thenReturn(
            new TenantContext(9L, 7L, 11L, "OWNER"));

        mockMvc.perform(post("/api/platform/ai/workflow-agent-runs")
                .with(SessionTestSupport.authenticated(credential))
                .header(RequestTenantContextResolver.TENANT_HEADER, "7")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "agentCode", "screenplay-agent", "input", "改写",
                    "projectId", 25, "episodeId", 91
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.runId", is(101)))
            .andExpect(jsonPath("$.data.output", is("正式结果")));

        Map<String, Object> temporary = new java.util.LinkedHashMap<>();
        temporary.put("code", "screenplay-agent");
        temporary.put("name", "编剧");
        temporary.put("description", "测试");
        temporary.put("systemPrompt", "调用工具完成任务");
        temporary.put("modelId", 8);
        temporary.put("temperature", 0.2);
        temporary.put("maxTokens", 2048);
        temporary.put("maxSteps", 3);
        temporary.put("status", "ENABLED");
        temporary.put("skillCodes", List.of());
        temporary.put("toolCodes", List.of("read_episode_script"));
        temporary.put("input", "读取剧集");
        temporary.put("projectId", 25);
        temporary.put("episodeId", 91);
        mockMvc.perform(post("/api/platform/ai/workflow-agent-runs/test")
                .with(SessionTestSupport.authenticated(credential))
                .header(RequestTenantContextResolver.TENANT_HEADER, "7")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(temporary)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.runId", is(102)));

        mockMvc.perform(get("/api/platform/ai/workflow-agent-runs")
                .queryParam("agentCode", "screenplay-agent").queryParam("limit", "20")
                .with(SessionTestSupport.authenticated(credential)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id", is(101)));

        ArgumentCaptor<WorkflowAgentRunInput> input = ArgumentCaptor.forClass(WorkflowAgentRunInput.class);
        org.mockito.Mockito.verify(runner).runFormal(input.capture());
        assertThat(input.getValue().userId()).isNotNull();
        assertThat(input.getValue().projectId()).isEqualTo(25L);
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
}
