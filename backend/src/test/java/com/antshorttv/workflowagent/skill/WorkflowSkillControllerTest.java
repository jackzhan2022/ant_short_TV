package com.antshorttv.workflowagent.skill;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.antshorttv.support.SessionTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "ai.workflow-agent.skill-root=target/workflow-skill-controller-test")
@AutoConfigureMockMvc
class WorkflowSkillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void catalogRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/platform/ai/workflow-skills"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void tenantUserCannotViewIndependentSkills() throws Exception {
        String credential = register("13800000881", "Tenant User");

        mockMvc.perform(get("/api/platform/ai/workflow-skills")
                .with(SessionTestSupport.authenticated(credential)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode", is("FORBIDDEN")));
    }

    @Test
    void platformAdministratorCanCreateUpdateCopyListAndDeleteSkill() throws Exception {
        String credential = register("13800000999", "Platform Admin");
        String code = "controller-" + UUID.randomUUID();
        String copiedCode = code + "-copy";
        String content = skill(code, "first");
        MvcResult created = mockMvc.perform(post("/api/platform/ai/workflow-skills")
                .with(SessionTestSupport.authenticated(credential))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(code, content)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.code", is(code)))
            .andReturn();
        String revision = com.jayway.jsonpath.JsonPath.read(
            created.getResponse().getContentAsString(), "$.data.revision");

        mockMvc.perform(put("/api/platform/ai/workflow-skills/{code}", code)
                .with(SessionTestSupport.authenticated(credential))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson(skill(code, "second"), revision)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content", org.hamcrest.Matchers.containsString("second")));

        mockMvc.perform(post("/api/platform/ai/workflow-skills/{code}/copy", code)
                .with(SessionTestSupport.authenticated(credential))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetCode\":\"" + copiedCode + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.code", is(copiedCode)));

        mockMvc.perform(get("/api/platform/ai/workflow-skills").queryParam("query", code)
                .with(SessionTestSupport.authenticated(credential)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()", is(2)));

        mockMvc.perform(delete("/api/platform/ai/workflow-skills/{code}", copiedCode)
                .with(SessionTestSupport.authenticated(credential)))
            .andExpect(status().isOk());
        mockMvc.perform(delete("/api/platform/ai/workflow-skills/{code}", code)
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

    private String skill(String name, String body) {
        return "---\nname: " + name + "\ndescription: controller test\n---\n\n# " + body + "\n";
    }

    private String json(String code, String content) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
            java.util.Map.of("code", code, "content", content));
    }

    private String updateJson(String content, String revision) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
            java.util.Map.of("content", content, "expectedRevision", revision));
    }
}
