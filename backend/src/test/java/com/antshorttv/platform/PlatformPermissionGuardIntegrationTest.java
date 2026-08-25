package com.antshorttv.platform;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
class PlatformPermissionGuardIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlatformUserRoleMapper userRoleMapper;

    @Test
    void platformAdministratorWorksWithoutTenantAndRevocationAffectsNextRequest() throws Exception {
        Registration admin = register("13800000999", "Platform Admin");

        mockMvc.perform(get("/api/platform/ai/providers").cookie(admin.session()))
            .andExpect(status().isOk());

        userRoleMapper.deleteByUserId(admin.userId());

        mockMvc.perform(get("/api/platform/ai/providers").cookie(admin.session()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode", is("FORBIDDEN")));
    }

    @Test
    void tenantOwnerDoesNotInheritPlatformPermission() throws Exception {
        Registration owner = register("13800000888", "Tenant Owner");
        MvcResult safe = mockMvc.perform(get("/api/tenants/my").cookie(owner.session()))
            .andExpect(status().isOk())
            .andReturn();
        Cookie csrf = safe.getResponse().getCookie("XSRF-TOKEN");
        mockMvc.perform(post("/api/tenants")
                .cookie(owner.session(), csrf)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Owner Tenant","type":"STUDIO","description":"owner"}
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/platform/ai/providers")
                .cookie(owner.session())
                .header("X-Tenant-Id", "1"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode", is("FORBIDDEN")));
    }

    private Registration register(String mobile, String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mobile":"%s","verificationCode":"123456","nickname":"%s","password":"Password123"}
                    """.formatted(mobile, nickname)))
            .andExpect(status().isOk())
            .andReturn();
        Number userId = com.jayway.jsonpath.JsonPath.read(
            result.getResponse().getContentAsString(), "$.data.user.id");
        return new Registration(userId.longValue(), result.getResponse().getCookie("ANT_SHORT_SESSION"));
    }

    private record Registration(Long userId, Cookie session) {
    }
}
