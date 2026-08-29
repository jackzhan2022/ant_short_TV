package com.antshorttv.script;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.antshorttv.user.UserEntity;
import com.antshorttv.user.UserMapper;
import com.jayway.jsonpath.JsonPath;
import com.antshorttv.execution.AiExecutionWorker;
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

    @Autowired
    private AiExecutionWorker aiExecutionWorker;

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

        MvcResult extraction = mockMvc.perform(post("/api/projects/%d/scripts/ai-extract-elements".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"elementType":"ALL"}
                    """))
            .andExpect(status().isAccepted())
            .andReturn();
        Long executionId = ((Number) JsonPath.read(extraction.getResponse().getContentAsString(), "$.data.id")).longValue();
        aiExecutionWorker.run(executionId);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/api/projects/%d/asset-candidates?reviewStatus=PENDING_REVIEW".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items", hasSize(4)))
            .andExpect(jsonPath("$.data.items[*].name", hasItems("林晚", "主场景", "室内场景", "录音笔")));

        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject("""
            select (select count(*) from character_asset where tenant_id = ? and project_id = ?)
                 + (select count(*) from scene_asset where tenant_id = ? and project_id = ?)
                 + (select count(*) from prop_asset where tenant_id = ? and project_id = ?)
            """, Integer.class, tenantId, projectId, tenantId, projectId, tenantId, projectId)).isZero();

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
        Long providerId = jdbcTemplate.queryForObject("select id from ai_provider where code = 'OpenAI' limit 1", Long.class);
        jdbcTemplate.update("update ai_provider set status = 'ENABLED' where id = ?", providerId);
        jdbcTemplate.update("update ai_provider_config set api_key_cipher = 'test-key', base_url = 'https://example.com/v1', status = 'ENABLED' where provider_id = ?", providerId);
        String modelCode = "test-extract-text-" + tenantId;
        jdbcTemplate.update("update ai_model set is_default = false where service_type = 'TEXT'");
        jdbcTemplate.update("delete from ai_model_capability where model_id in (select id from ai_model where code = ?)", modelCode);
        jdbcTemplate.update("delete from ai_model where code = ?", modelCode);
        jdbcTemplate.update("""
            insert into ai_model
              (provider_id, code, name, model_code, service_type, status, is_default, sort, created_at, updated_at)
            values (?, ?, 'Test Extract Text', 'gpt-4.1-mini', 'TEXT', 'ENABLED', true, 100, now(), now())
            """, providerId, modelCode);
        Long modelId = jdbcTemplate.queryForObject("select id from ai_model where code = ?", Long.class, modelCode);
        jdbcTemplate.update("insert into ai_model_capability (model_id, capability, status, created_at, updated_at) values (?, 'TEXT_GENERATION', 'ENABLED', now(), now())", modelId);
        jdbcTemplate.update("""
            insert into ai_model_price_version
              (model_id, version_no, status, effective_from, published_at, created_at)
            values (?, 1, 'PUBLISHED', dateadd('hour', -1, now()), now(), now())
            """, modelId);
        Long costVersionId = jdbcTemplate.queryForObject(
            "select max(id) from ai_model_price_version where model_id = ?", Long.class, modelId
        );
        jdbcTemplate.update("""
            insert into ai_model_price_component
              (price_version_id, metric, unit_size, unit_price, currency,
               dimensions_json, dimensions_key, created_at)
            values (?, 'CALL', 1, 0.1, 'USD', '{}', '', now())
            """, costVersionId);
        jdbcTemplate.update("""
            insert into ai_model_point_price_version
              (model_id, version_no, status, effective_from, published_at, created_at)
            values (?, 1, 'PUBLISHED', dateadd('hour', -1, now()), now(), now())
            """, modelId);
        Long pointVersionId = jdbcTemplate.queryForObject(
            "select max(id) from ai_model_point_price_version where model_id = ?", Long.class, modelId
        );
        jdbcTemplate.update("""
            insert into ai_model_point_price_component
              (price_version_id, metric, unit_size, point_rate,
               dimensions_json, dimensions_key, created_at)
            values (?, 'CALL', 1, 1, '{}', '', now())
            """, pointVersionId);
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
