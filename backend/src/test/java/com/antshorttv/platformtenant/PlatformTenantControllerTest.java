package com.antshorttv.platformtenant;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
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
class PlatformTenantControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void platformAdminCanQueryAndUpdateTenantWithoutMembership() throws Exception {
        Registration admin = register("13800000999", "Platform Admin");
        Long tenantId = insertTenant("CTRL-ADMIN", "Controller Admin Tenant", "DISABLED");

        mockMvc.perform(get("/api/platform/tenants").cookie(admin.session()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").isNumber());
        mockMvc.perform(get("/api/platform/tenants/{id}", tenantId).cookie(admin.session()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("DISABLED")));

        Cookie csrf = csrf(admin.session());
        mockMvc.perform(put("/api/platform/tenants/{id}/status", tenantId)
                .cookie(admin.session(), csrf)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACTIVE\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("ACTIVE")));
    }

    @Test
    void viewOnlyOperatorCanQueryButCannotUpdate() throws Exception {
        Registration viewer = register("13800000777", "Tenant Viewer");
        grantViewOnly(viewer.userId());
        Long tenantId = insertTenant("CTRL-VIEW", "Controller View Tenant", "ACTIVE");

        mockMvc.perform(get("/api/platform/tenants").cookie(viewer.session()))
            .andExpect(status().isOk());

        Cookie csrf = csrf(viewer.session());
        mockMvc.perform(put("/api/platform/tenants/{id}/status", tenantId)
                .cookie(viewer.session(), csrf)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DISABLED\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode", is("FORBIDDEN")));
    }

    @Test
    void userWithoutPlatformPermissionCannotReadTenantOperationsData() throws Exception {
        Registration user = register("13800000666", "No Permission");

        mockMvc.perform(get("/api/platform/tenants").cookie(user.session()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode", is("FORBIDDEN")));
    }

    @Test
    void invalidFiltersAndStatusReturnValidationError() throws Exception {
        Registration admin = register("13800000555", "Validation Admin");
        grantPlatformAdmin(admin.userId());
        Long tenantId = insertTenant("CTRL-VALIDATE", "Controller Validate Tenant", "ACTIVE");

        mockMvc.perform(get("/api/platform/tenants?status=UNKNOWN&pageSize=101").cookie(admin.session()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")));

        Cookie csrf = csrf(admin.session());
        mockMvc.perform(put("/api/platform/tenants/{id}/status", tenantId)
                .cookie(admin.session(), csrf)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ARCHIVED\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")));
    }

    private Cookie csrf(Cookie session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/tenants/my").cookie(session))
            .andExpect(status().isOk()).andReturn();
        return result.getResponse().getCookie("XSRF-TOKEN");
    }

    private Registration register(String mobile, String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mobile":"%s","verificationCode":"123456","nickname":"%s","password":"Password123"}
                    """.formatted(mobile, nickname)))
            .andExpect(status().isOk())
            .andReturn();
        Number userId = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.user.id");
        return new Registration(userId.longValue(), result.getResponse().getCookie("ANT_SHORT_SESSION"));
    }

    private Long insertTenant(String code, String name, String status) {
        jdbc.update("insert into tenant (code,name,type,status,created_at,updated_at) values (?,?, 'STUDIO', ?, now(), now())",
            code, name, status);
        return jdbc.queryForObject("select id from tenant where code=?", Long.class, code);
    }

    private void grantViewOnly(Long userId) {
        String code = "PLATFORM_TENANT_VIEWER_" + userId;
        jdbc.update("insert into platform_role (code,name,status,created_at,updated_at) values (?,?,'ACTIVE',now(),now())", code, code);
        Long roleId = jdbc.queryForObject("select id from platform_role where code=?", Long.class, code);
        Long permissionId = jdbc.queryForObject("select id from platform_permission where code='PLATFORM_TENANT_VIEW'", Long.class);
        jdbc.update("insert into platform_role_permission (role_id,permission_id,created_at) values (?,?,now())", roleId, permissionId);
        jdbc.update("insert into platform_user_role (user_id,role_id,created_at) values (?,?,now())", userId, roleId);
    }

    private void grantPlatformAdmin(Long userId) {
        Long roleId = jdbc.queryForObject(
            "select id from platform_role where code='PLATFORM_ADMIN'", Long.class);
        jdbc.update("insert into platform_user_role (user_id,role_id,created_at) values (?,?,now())", userId, roleId);
    }

    private record Registration(Long userId, Cookie session) {}
}
