package com.antshorttv.script;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.antshorttv.user.UserEntity;
import com.antshorttv.user.UserMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class ScriptElementExtractionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void savesElementsParsedFromAiExtractionOutput() throws Exception {
        String token = registerUser("13800013021", "Element Parser Owner");
        Long tenantId = createTenant(token, "AI元素解析团队");
        Long ownerId = userIdByMobile("13800013021");
        createDefaultTextService(tenantId, ownerId);
        grantTeamPoints(tenantId, 10);
        Long projectId = createProject(token, tenantId, ownerId, "AI解析短剧", "SCRIPT_ELEMENT_PARSE");

        mockMvc.perform(put("/api/projects/%d/scripts/current".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"AI解析短剧","content":"林晚在天台拿出录音笔，证明反派篡改遗嘱。","status":"DRAFT"}
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/projects/%d/scripts/ai-extract-elements".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"elementType":"ALL"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.characters", hasSize(1)))
            .andExpect(jsonPath("$.data.characters[0].name", is("林晚")))
            .andExpect(jsonPath("$.data.scenes", hasSize(2)))
            .andExpect(jsonPath("$.data.scenes[0].name", is("主场景")))
            .andExpect(jsonPath("$.data.props", hasSize(1)))
            .andExpect(jsonPath("$.data.props[0].name", is("股权协议")));

        Integer callLogs = jdbcTemplate.queryForObject("""
            select count(*) from ai_call_log
             where tenant_id = ?
               and business_scene in ('character_extract', 'scene_extract', 'prop_extract')
            """, Integer.class, tenantId);
        org.assertj.core.api.Assertions.assertThat(callLogs).isEqualTo(3);
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

    private Long createTenant(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tenants")
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"%s","type":"STUDIO","description":"剧本工作流测试"}
                    """.formatted(name)))
            .andExpect(status().isOk())
            .andReturn();
        return readLong(result, "$.data.id");
    }

    private Long createProject(String token, Long tenantId, Long ownerId, String name, String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"%s","code":"%s","description":"剧本工作流项目","ownerId":%d}
                    """.formatted(name, code, ownerId)))
            .andExpect(status().isOk())
            .andReturn();
        return readLong(result, "$.data.id");
    }

    private Long userIdByMobile(String mobile) {
        UserEntity user = userMapper.selectByMobile(mobile);
        return user.getId();
    }

    private Long readLong(MvcResult result, String path) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return value.longValue();
    }

    private void createDefaultTextService(Long tenantId, Long userId) {
        jdbcTemplate.update("""
            update ai_service_config
               set is_default = false,
                   updated_at = now()
             where service_type = 'TEXT'
               and is_default = true
               and deleted_at is null
            """);
        jdbcTemplate.update("""
            insert into ai_service_config
              (tenant_id, provider, service_type, name, base_url, api_key_cipher, model, endpoint, priority, is_default, enabled, last_test_status, created_by, created_at, updated_at)
            values (?, 'OpenAI', 'TEXT', '默认文本服务', 'https://example.com/v1', 'cipher', 'gpt-4.1-mini', '/chat/completions', 100, true, true, 'SUCCESS', ?, now(), now())
            """, tenantId, userId);
    }

    private void grantTeamPoints(Long tenantId, int amount) {
        jdbcTemplate.update("""
            insert into team_point_account
              (tenant_id, balance, total_granted, total_consumed, created_at, updated_at)
            values (?, ?, ?, 0, now(), now())
            """, tenantId, amount, amount);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
