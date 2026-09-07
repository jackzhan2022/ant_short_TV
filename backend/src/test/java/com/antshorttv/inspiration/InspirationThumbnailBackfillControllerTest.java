package com.antshorttv.inspiration;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class InspirationThumbnailBackfillControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void platformAdminCanStartThumbnailBackfill() throws Exception {
        Registration admin = register("13800000888");
        grantPlatformAdmin(admin.userId());
        Cookie csrf = csrf(admin.session());

        mockMvc.perform(post("/api/platform/inspiration/thumbnail-backfill?limit=1")
                .cookie(admin.session(), csrf)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.processed").isNumber())
            .andExpect(jsonPath("$.data.failed").isNumber());
    }

    @Test
    void ordinaryUserCannotStartThumbnailBackfill() throws Exception {
        Registration user = register("13800000889");
        Cookie csrf = csrf(user.session());

        mockMvc.perform(post("/api/platform/inspiration/thumbnail-backfill")
                .cookie(user.session(), csrf)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode", is("FORBIDDEN")));
    }

    private Registration register(String mobile) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mobile\":\"%s\",\"verificationCode\":\"123456\",\"nickname\":\"Backfill Admin\",\"password\":\"Password123\"}".formatted(mobile)))
            .andExpect(status().isOk())
            .andReturn();
        Number userId = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.user.id");
        return new Registration(userId.longValue(), result.getResponse().getCookie("ANT_SHORT_SESSION"));
    }

    private Cookie csrf(Cookie session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/tenants/my").cookie(session))
            .andExpect(status().isOk())
            .andReturn();
        return result.getResponse().getCookie("XSRF-TOKEN");
    }

    private void grantPlatformAdmin(Long userId) {
        Long roleId = jdbc.queryForObject("select id from platform_role where code='PLATFORM_ADMIN'", Long.class);
        jdbc.update("insert into platform_user_role (user_id,role_id,created_at) values (?,?,now())", userId, roleId);
    }

    private record Registration(Long userId, Cookie session) {}
}
