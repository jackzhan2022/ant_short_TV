package com.antshorttv.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
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
            .andExpect(jsonPath("$.data.accessToken", not("")));

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
    void logsInByMobilePasswordAndReturnsAccessToken() throws Exception {
        registerUser("13800000003", "Password123", "王五");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mobile":"13800000003","password":"Password123"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.accessToken", not("")))
            .andExpect(jsonPath("$.data.user.mobile", is("13800000003")));
    }

    @Test
    void loginReturnsJoinedTenantsAndNextAction() throws Exception {
        String token = registerUser("13800000005", "Password123", "钱七");
        mockMvc.perform(post("/api/tenants")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
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
    void currentUserRequiresValidAccessToken() throws Exception {
        String token = registerUser("13800000004", "Password123", "赵六");

        mockMvc.perform(get("/api/user/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.mobile", is("13800000004")))
            .andExpect(jsonPath("$.data.nickname", is("赵六")));
    }

    private String registerUser(String mobile, String password, String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mobile":"%s","verificationCode":"123456","nickname":"%s","password":"%s"}
                    """.formatted(mobile, nickname, password)))
            .andExpect(status().isOk())
            .andReturn();

        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }
}
