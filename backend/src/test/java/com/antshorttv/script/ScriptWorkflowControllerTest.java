package com.antshorttv.script;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.antshorttv.user.UserEntity;
import com.antshorttv.user.UserMapper;
import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
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
class ScriptWorkflowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void returnsEmptyWorkspaceForProjectWithoutScript() throws Exception {
        String token = registerUser("13800013001", "Script Owner");
        Long tenantId = createTenant(token, "剧本工作流团队");
        Long ownerId = userIdByMobile("13800013001");
        Long projectId = createProject(token, tenantId, ownerId, "归来后我执掌豪门", "SCRIPT_WORKFLOW_EMPTY");

        mockMvc.perform(get("/api/projects/%d/script-workspace".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.projectId", is(projectId.intValue())))
            .andExpect(jsonPath("$.data.script", is((Object) null)))
            .andExpect(jsonPath("$.data.characters", hasSize(0)))
            .andExpect(jsonPath("$.data.storyboards", hasSize(0)));
    }

    @Test
    void generatesScriptDraftAndWorkspaceData() throws Exception {
        String token = registerUser("13800013002", "Generate Owner");
        Long tenantId = createTenant(token, "AI剧本团队");
        createDefaultTextService(tenantId, userIdByMobile("13800013002"));
        grantTeamPoints(tenantId, 5);
        Long ownerId = userIdByMobile("13800013002");
        Long projectId = createProject(token, tenantId, ownerId, "豪门逆袭", "SCRIPT_WORKFLOW_GENERATE");

        mockMvc.perform(post("/api/projects/%d/scripts/ai-generate".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title":"归来千金",
                      "storyIdea":"落魄千金重回豪门后发现当年的陷害另有隐情",
                      "genre":"逆袭",
                      "episodeCount":12,
                      "duration":90,
                      "styleRequirement":"强冲突"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.script.title", is("归来千金")))
            .andExpect(jsonPath("$.data.script.content").value(org.hamcrest.Matchers.containsString("落魄千金重回豪门后发现当年的陷害另有隐情")))
            .andExpect(jsonPath("$.data.versions", hasSize(1)));

        mockMvc.perform(get("/api/projects/%d/script-workspace".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.script.title", is("归来千金")))
            .andExpect(jsonPath("$.data.versions[0].sourceType", is("AI_GENERATE")));
    }

    @Test
    void generatesScriptWithGlobalTextServiceFromAnotherTenant() throws Exception {
        String configToken = registerUser("13800013012", "Global Config Owner");
        Long configTenantId = createTenant(configToken, "全局配置来源团队");
        createDefaultTextService(configTenantId, userIdByMobile("13800013012"));

        String projectToken = registerUser("13800013013", "Global Project Owner");
        Long projectTenantId = createTenant(projectToken, "全局配置使用团队");
        grantTeamPoints(projectTenantId, 5);
        Long ownerId = userIdByMobile("13800013013");
        Long projectId = createProject(projectToken, projectTenantId, ownerId, "跨团队剧本", "SCRIPT_GLOBAL_AI_CONFIG");

        mockMvc.perform(post("/api/projects/%d/scripts/ai-generate".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(projectToken))
                .header("X-Tenant-Id", projectTenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title":"全局服务剧本",
                      "storyIdea":"团队未单独配置 AI 服务时仍可生成剧本",
                      "genre":"逆袭"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.script.title", is("全局服务剧本")))
            .andExpect(jsonPath("$.data.versions", hasSize(1)));
    }

    @Test
    void aiScriptGenerationConsumesTeamPoint() throws Exception {
        String token = registerUser("13800013014", "Point Script Owner");
        Long tenantId = createTenant(token, "剧本积分团队");
        Long ownerId = userIdByMobile("13800013014");
        createDefaultTextService(tenantId, ownerId);
        grantTeamPoints(tenantId, 2);
        Long projectId = createProject(token, tenantId, ownerId, "积分剧本", "SCRIPT_POINTS_CONSUME");

        mockMvc.perform(post("/api/projects/%d/scripts/ai-generate".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title":"积分生成剧本",
                      "storyIdea":"每次AI生成剧本都要扣减团队积分",
                      "genre":"逆袭"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.script.title", is("积分生成剧本")));

        Integer balance = jdbcTemplate.queryForObject(
            "select balance from team_point_account where tenant_id = ?",
            Integer.class,
            tenantId
        );
        Integer consumeCount = jdbcTemplate.queryForObject(
            "select count(*) from team_point_transaction where tenant_id = ? and transaction_type = 'AI_CONSUME' and business_scene = 'script_generate'",
            Integer.class,
            tenantId
        );
        org.assertj.core.api.Assertions.assertThat(balance).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(consumeCount).isEqualTo(1);
    }

    @Test
    void aiScriptGenerationRequiresTeamPoint() throws Exception {
        String token = registerUser("13800013015", "No Point Owner");
        Long tenantId = createTenant(token, "无积分剧本团队");
        Long ownerId = userIdByMobile("13800013015");
        createDefaultTextService(tenantId, ownerId);
        Long projectId = createProject(token, tenantId, ownerId, "无积分剧本", "SCRIPT_POINTS_REQUIRED");

        mockMvc.perform(post("/api/projects/%d/scripts/ai-generate".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title":"不能生成",
                      "storyIdea":"团队没有积分时不能发起AI生成剧本",
                      "genre":"逆袭"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode", is("TEAM_POINTS_INSUFFICIENT")));
    }

    @Test
    void extractsCharactersAndScenesFromCurrentScript() throws Exception {
        String token = registerUser("13800013003", "Extract Owner");
        Long tenantId = createTenant(token, "AI元素团队");
        createDefaultTextService(tenantId, userIdByMobile("13800013003"));
        grantTeamPoints(tenantId, 5);
        Long ownerId = userIdByMobile("13800013003");
        Long projectId = createProject(token, tenantId, ownerId, "豪门元素", "SCRIPT_WORKFLOW_EXTRACT");

        mockMvc.perform(post("/api/projects/%d/scripts/ai-generate".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title":"归来千金",
                      "storyIdea":"落魄千金重回豪门，雨夜在林家老宅门口拿出股权协议",
                      "genre":"逆袭"
                    }
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/projects/%d/scripts/ai-extract-elements".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"elementType":"CHARACTER"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.characters", hasSize(2)))
            .andExpect(jsonPath("$.data.characters[0].name", is("主角")));

        mockMvc.perform(post("/api/projects/%d/scripts/ai-extract-elements".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"elementType":"SCENE"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.scenes", hasSize(2)))
            .andExpect(jsonPath("$.data.scenes[0].name", is("林家老宅门口")));
    }

    @Test
    void completesTextWorkflowEditingStoryboardPromptsAndLogs() throws Exception {
        String token = registerUser("13800013004", "Workflow Owner");
        Long tenantId = createTenant(token, "AI文本全链路团队");
        Long ownerId = userIdByMobile("13800013004");
        createDefaultTextService(tenantId, ownerId);
        grantTeamPoints(tenantId, 20);
        Long projectId = createProject(token, tenantId, ownerId, "全链路短剧", "SCRIPT_WORKFLOW_FULL");

        MvcResult generateResult = mockMvc.perform(post("/api/projects/%d/scripts/ai-generate".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"storyIdea":"落魄千金雨夜回归豪门","genre":"逆袭","episodeCount":12,"duration":90}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.versions", hasSize(1)))
            .andReturn();
        Long versionId = readLong(generateResult, "$.data.versions[0].id");

        mockMvc.perform(post("/api/projects/%d/scripts/ai-rewrite".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"rewriteType":"冲突增强","requirement":"强化前三秒钩子","outputLength":"KEEP"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.versions", hasSize(2)))
            .andExpect(jsonPath("$.data.versions[0].sourceType", is("AI_REWRITE")));

        mockMvc.perform(put("/api/projects/%d/scripts/current".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"手工整理版","content":"第一集：主角在雨夜回到林家老宅门口。","status":"CONFIRMED"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.script.title", is("手工整理版")))
            .andExpect(jsonPath("$.data.script.status", is("CONFIRMED")));

        mockMvc.perform(put("/api/projects/%d/scripts/versions/%d/apply".formatted(projectId, versionId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.script.currentVersionId", is(versionId.intValue())));

        MvcResult extractResult = mockMvc.perform(post("/api/projects/%d/scripts/ai-extract-elements".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"elementType":"ALL"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.characters", hasSize(2)))
            .andExpect(jsonPath("$.data.scenes", hasSize(2)))
            .andExpect(jsonPath("$.data.props", hasSize(1)))
            .andReturn();
        Long characterId = readLong(extractResult, "$.data.characters[0].id");

        mockMvc.perform(put("/api/projects/%d/script-elements/CHARACTER/%d".formatted(projectId, characterId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"林晚","roleType":"LEAD","gender":"女","ageRange":"25-30","identity":"回归千金","personality":["冷静","果断"],"appearance":"黑色风衣","prompt":"林晚角色定妆照","status":"CONFIRMED"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.characters[0].name", is("林晚")));

        mockMvc.perform(post("/api/projects/%d/storyboards/ai-breakdown".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"scope":"FULL"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.storyboards", hasSize(3)))
            .andExpect(jsonPath("$.data.storyboards[0].imagePrompt", Matchers.containsString("首帧")));

        MvcResult storyboardResult = mockMvc.perform(post("/api/projects/%d/storyboards".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"episodeNo":1,"shotNo":9,"shotType":"特写","visualDescription":"股权协议签名特写","characters":"林晚","scene":"宴会厅","dialogue":"这一次轮到我了。","durationSeconds":4,"imagePrompt":"协议特写首帧","videoPrompt":"镜头推进"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.storyboards", hasSize(4)))
            .andReturn();
        Long storyboardId = readLong(storyboardResult, "$.data.storyboards[3].id");

        mockMvc.perform(put("/api/projects/%d/storyboards/%d".formatted(projectId, storyboardId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"episodeNo":1,"shotNo":10,"shotType":"近景","visualDescription":"林晚抬眼看向众人","characters":"林晚","scene":"宴会厅","dialogue":"我回来了。","durationSeconds":5,"imagePrompt":"林晚近景首帧","videoPrompt":"慢慢推近","status":"CONFIRMED"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.storyboards[3].shotNo", is(10)));

        mockMvc.perform(post("/api/projects/%d/prompts/ai-generate".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"targetType":"ALL"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.characters[0].prompt", Matchers.containsString("角色")))
            .andExpect(jsonPath("$.data.storyboards[0].videoPrompt", Matchers.containsString("竖屏短剧")));

        Integer callCount = jdbcTemplate.queryForObject(
            "select count(*) from ai_call_log where tenant_id = ? and business_scene in ('script_generate','script_rewrite','character_extract','scene_extract','prop_extract','storyboard_breakdown','prompt_generate')",
            Integer.class,
            tenantId
        );
        org.assertj.core.api.Assertions.assertThat(callCount).isGreaterThanOrEqualTo(7);
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
