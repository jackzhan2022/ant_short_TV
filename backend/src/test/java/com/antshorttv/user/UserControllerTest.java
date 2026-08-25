package com.antshorttv.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
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
    void legacyAuthenticationPermissionAndTenantEndpointsAreNotExposed() throws Exception {
        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mobile":"13800001001","verificationCode":"123456","nickname":"Retired API User","password":"Password123"}
                    """))
            .andExpect(status().isOk())
            .andReturn();
        Cookie session = registration.getResponse().getCookie("ANT_SHORT_SESSION");
        MvcResult bootstrap = mockMvc.perform(get("/api/auth/bootstrap").cookie(session))
            .andExpect(status().isOk())
            .andReturn();
        Cookie csrf = bootstrap.getResponse().getCookie("XSRF-TOKEN");

        for (String path : new String[] {
            "/api/currentUser",
            "/api/user/me",
            "/api/auth/permissions",
            "/api/tenants/current"
        }) {
            mockMvc.perform(get(path).cookie(session, csrf))
                .andExpect(status().isNotFound());
        }

        mockMvc.perform(post("/api/login/account")
                .cookie(session, csrf)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/login/outLogin")
                .cookie(session, csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()))
            .andExpect(status().isNotFound());
    }
}
