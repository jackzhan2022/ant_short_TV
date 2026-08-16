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

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void currentUserReturnsAdminUserForFrontendBootstrap() throws Exception {
        mockMvc.perform(get("/api/currentUser"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.name", is("admin")))
            .andExpect(jsonPath("$.data.access", is("admin")));
    }

    @Test
    void loginAccountReturnsAntDesignProLoginShape() throws Exception {
        mockMvc.perform(post("/api/login/account")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"ant.design\",\"type\":\"account\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("ok")))
            .andExpect(jsonPath("$.type", is("account")))
            .andExpect(jsonPath("$.currentAuthority", is("admin")));
    }

    @Test
    void outLoginReturnsSuccessResponse() throws Exception {
        mockMvc.perform(post("/api/login/outLogin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)));
    }
}
