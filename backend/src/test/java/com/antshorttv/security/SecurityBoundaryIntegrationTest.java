package com.antshorttv.security;

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
import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityBoundaryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void explicitlyPublicRegistrationRemainsAvailableWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mobile":"13800000101","verificationCode":"123456","nickname":"Public User","password":"Password123"}
                    """))
            .andExpect(status().isOk());
    }

    @Test
    void anonymousTenantApiIsRejectedBySecurityChain() throws Exception {
        assertUnauthorized("/api/tenants/my");
    }

    @Test
    void anonymousProjectApiIsRejectedBySecurityChain() throws Exception {
        assertUnauthorized("/api/projects");
    }

    @Test
    void anonymousPlatformApiIsRejectedBySecurityChain() throws Exception {
        assertUnauthorized("/api/platform/ai/providers");
    }

    @Test
    void unknownProtectedApiFailsClosedBeforeControllerResolution() throws Exception {
        assertUnauthorized("/api/not-a-public-endpoint");
    }

    @Test
    void unsafeCookieAuthenticatedRequestsRequireMatchingCsrfHeader() throws Exception {
        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mobile":"13800000102","verificationCode":"123456","nickname":"CSRF User","password":"Password123"}
                    """))
            .andExpect(status().isOk())
            .andReturn();
        Cookie session = registration.getResponse().getCookie("ANT_SHORT_SESSION");

        mockMvc.perform(post("/api/tenants")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"CSRF Tenant","type":"STUDIO","description":"csrf"}
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode", is("FORBIDDEN")));

        MvcResult safeRequest = mockMvc.perform(get("/api/tenants/my").cookie(session))
            .andExpect(status().isOk())
            .andReturn();
        Cookie csrf = safeRequest.getResponse().getCookie("XSRF-TOKEN");

        mockMvc.perform(post("/api/tenants")
                .cookie(session, csrf)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"CSRF Tenant","type":"STUDIO","description":"csrf"}
                    """))
            .andExpect(status().isOk());
    }

    private void assertUnauthorized(String path) throws Exception {
        mockMvc.perform(get(path))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success", is(false)))
            .andExpect(jsonPath("$.errorCode", is("UNAUTHORIZED")));
    }
}
