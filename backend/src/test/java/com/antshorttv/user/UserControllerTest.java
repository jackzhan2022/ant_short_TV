package com.antshorttv.user;

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
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void currentUserRequiresTokenAndReturnsRealUserForFrontendBootstrap() throws Exception {
        mockMvc.perform(get("/api/currentUser"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode", is("UNAUTHORIZED")));

        String token = registerUser("13800001001", "兼容用户");

        mockMvc.perform(get("/api/currentUser")
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.name", is("兼容用户")))
            .andExpect(jsonPath("$.data.phone", is("13800001001")))
            .andExpect(jsonPath("$.data.access", is("user")));
    }

    @Test
    void loginAccountValidatesRealMobilePasswordAndReturnsUserAuthority() throws Exception {
        registerUser("13800001002", "兼容登录");

        mockMvc.perform(post("/api/login/account")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"13800001002\",\"password\":\"Password123\",\"type\":\"account\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("ok")))
            .andExpect(jsonPath("$.type", is("account")))
            .andExpect(jsonPath("$.currentAuthority", is("user")));

        mockMvc.perform(post("/api/login/account")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"13800001002\",\"password\":\"wrong-password\",\"type\":\"account\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode", is("INVALID_CREDENTIALS")));
    }

    @Test
    void outLoginReturnsSuccessResponse() throws Exception {
        mockMvc.perform(post("/api/login/outLogin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)));
    }

    private String registerUser(String mobile, String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mobile":"%s","verificationCode":"123456","nickname":"%s","password":"Password123"}
                    """.formatted(mobile, nickname)))
            .andExpect(status().isOk())
            .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
