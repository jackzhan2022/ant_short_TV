package com.antshorttv.ai;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class BuiltInAgentCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void catalogRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/platform/ai/agents"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode", is("UNAUTHORIZED")));
    }

    @Test
    void listsBuiltInAgentsAndPreviewsWithoutProviderCall() throws Exception {
        String token = registerUser("13800000999", "Platform Admin");

        mockMvc.perform(get("/api/platform/ai/agents")
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].code", hasItem("script-character-extract")))
            .andExpect(jsonPath("$.data[?(@.code == 'script-character-extract')].modelRouting", hasItem("PLATFORM_DEFAULT")));

        mockMvc.perform(post("/api/platform/ai/agents/script-character-extract/preview")
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"variables":{"scriptTitle":"真假千金","scriptContent":"林晚回到老宅。"}}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.agentCode", is("script-character-extract")))
            .andExpect(jsonPath("$.data.prompt", org.hamcrest.Matchers.containsString("真假千金")))
            .andExpect(jsonPath("$.data.prompt", org.hamcrest.Matchers.containsString("只返回合法 JSON")));
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

}
