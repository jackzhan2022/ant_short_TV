package com.antshorttv.script;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.antshorttv.ai.AiContext;
import com.antshorttv.ai.AiGateway;
import com.antshorttv.ai.AiTextRequest;
import com.antshorttv.ai.AiTextResponse;
import com.antshorttv.user.UserEntity;
import com.antshorttv.user.UserMapper;
import com.jayway.jsonpath.JsonPath;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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

    @MockBean
    private AiGateway aiGateway;

    @Test
    void savesElementsParsedFromAiExtractionOutput() throws Exception {
        when(aiGateway.text(any(AiContext.class), any(AiTextRequest.class))).thenAnswer(invocation -> {
            AiContext context = invocation.getArgument(0);
            AiTextRequest request = invocation.getArgument(1);
            assertTrue(request.userPrompt().contains("林晚在天台拿出录音笔"));
            assertTrue(request.userPrompt().contains("只返回合法 JSON"));
            String content = switch (context.businessType()) {
                case "character_extract" -> """
                    {"characters":[{"name":"林晚","roleType":"LEAD","gender":"女","ageRange":"25-30","identity":"回归千金","personality":["冷静","果断"],"appearance":"黑色风衣","prompt":"林晚角色定妆照"}]}
                    """;
                case "scene_extract" -> """
                    {"scenes":[{"name":"天台","sceneType":"EXTERIOR","atmosphere":"深夜冷风","description":"城市高楼天台，霓虹远景压迫感强","visualStyle":"冷色电影感","prompt":"深夜城市天台，冷色电影光"}]}
                    """;
                case "prop_extract" -> """
                    {"props":[{"name":"录音笔","propType":"KEY_PROP","appearance":"银色小型录音笔","plotFunction":"证明反派篡改遗嘱","prompt":"银色录音笔关键线索特写"}]}
                    """;
                default -> "ok";
            };
            return new AiTextResponse(content, null, null, null, null, 1L, null);
        });

        String token = registerUser("13800013021", "Element Parser Owner");
        Long tenantId = createTenant(token, "AI元素解析团队");
        Long ownerId = userIdByMobile("13800013021");
        createDefaultTextService(tenantId, ownerId);
        grantTeamPoints(tenantId, 10);
        Long projectId = createProject(token, tenantId, ownerId, "AI解析短剧", "SCRIPT_ELEMENT_PARSE");

        mockMvc.perform(put("/api/projects/%d/scripts/current".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"AI解析短剧","content":"林晚在天台拿出录音笔，证明反派篡改遗嘱。","status":"DRAFT"}
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/projects/%d/scripts/ai-extract-elements".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"elementType":"ALL"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.characters", hasSize(1)))
            .andExpect(jsonPath("$.data.characters[0].name", is("林晚")))
            .andExpect(jsonPath("$.data.scenes", hasSize(1)))
            .andExpect(jsonPath("$.data.scenes[0].name", is("天台")))
            .andExpect(jsonPath("$.data.props", hasSize(1)))
            .andExpect(jsonPath("$.data.props[0].name", is("录音笔")));
    }

    private String registerUser(String mobile, String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mobile":"%s","verificationCode":"123456","nickname":"%s","password":"Password123"}
                    """.formatted(mobile, nickname)))
            .andExpect(status().isOk())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private Long createTenant(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tenants")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
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
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
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
