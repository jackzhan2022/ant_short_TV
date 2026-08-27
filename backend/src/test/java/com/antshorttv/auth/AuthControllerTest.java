package com.antshorttv.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.antshorttv.user.UserEntity;
import com.antshorttv.user.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import jakarta.servlet.http.Cookie;

@SpringBootTest(properties = "app.public-base-url=https://antv.aixmax.cn")
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Test
    void registersUserWithHashedPasswordAndRejectsDuplicateMobile() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mobile":"13800000002","verificationCode":"123456","nickname":"李四","password":"Password123"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.user.mobile", is("13800000002")))
            .andExpect(jsonPath("$.data.accessToken").doesNotExist())
            .andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
            .andExpect(result -> assertThat(result.getResponse().getCookie("ANT_SHORT_SESSION"))
                .satisfies(cookie -> {
                    assertThat(cookie.isHttpOnly()).isTrue();
                    assertThat(cookie.getPath()).isEqualTo("/");
                }));

        UserEntity saved = userMapper.selectByMobile("13800000002");
        assertThat(saved.getPasswordHash()).isNotEqualTo("Password123");
        assertThat(saved.getPasswordHash()).startsWith("{bcrypt}");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mobile":"13800000002","verificationCode":"123456","nickname":"李四","password":"Password123"}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success", is(false)))
            .andExpect(jsonPath("$.errorCode", is("DUPLICATE_MOBILE")));
    }

    @Test
    void logsInByMobilePasswordAndReturnsHttpOnlySessionCookie() throws Exception {
        registerUser("13800000003", "Password123", "王五");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mobile":"13800000003","password":"Password123"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.accessToken").doesNotExist())
            .andExpect(jsonPath("$.data.user.mobile", is("13800000003")))
            .andExpect(result -> assertThat(result.getResponse().getCookie("ANT_SHORT_SESSION")).isNotNull());
    }

    @Test
    void loginReturnsJoinedTenantsAndNextAction() throws Exception {
        Cookie sessionCookie = registerUser("13800000005", "Password123", "钱七");
        mockMvc.perform(post("/api/tenants")
                .with(com.antshorttv.support.SessionTestSupport.authenticated(sessionCookie.getValue()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"登录后团队","type":"STUDIO","description":"登录后团队判断"}
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mobile":"13800000005","password":"Password123"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.tenants[0].name", is("登录后团队")))
            .andExpect(jsonPath("$.data.nextAction", is("ENTER_WORKSPACE")));
    }

    @Test
    void bootstrapReturnsCurrentUserForValidSession() throws Exception {
        Cookie sessionCookie = registerUser("13800000004", "Password123", "赵六");

        mockMvc.perform(get("/api/auth/bootstrap")
                .cookie(sessionCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.user.mobile", is("13800000004")))
            .andExpect(jsonPath("$.data.user.nickname", is("赵六")));
    }

    private Cookie registerUser(String mobile, String password, String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mobile":"%s","verificationCode":"123456","nickname":"%s","password":"%s"}
                    """.formatted(mobile, nickname, password)))
            .andExpect(status().isOk())
            .andReturn();

        return result.getResponse().getCookie("ANT_SHORT_SESSION");
    }

    @Test
    void loginAcceptsConfiguredPublicOrigin() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .header("Origin", "https://antv.aixmax.cn")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mobile":"13000000000","password":"invalid-test-only"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode", is("INVALID_CREDENTIALS")));
    }
}
