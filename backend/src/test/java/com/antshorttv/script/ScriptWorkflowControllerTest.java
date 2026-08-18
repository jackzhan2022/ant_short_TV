package com.antshorttv.script;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.antshorttv.user.UserEntity;
import com.antshorttv.user.UserMapper;
import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicReference;
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
        ArrayDeque<String> responses = new ArrayDeque<>();
        responses.add("落魄千金重回豪门后发现当年的陷害另有隐情，模型生成正文。");
        HttpServer server = startQueuedTextServer(responses);
        Long ownerId = userIdByMobile("13800013002");
        Long projectId = createProject(token, tenantId, ownerId, "豪门逆袭", "SCRIPT_WORKFLOW_GENERATE");

        try {
            createTextServiceConfig(token, tenantId, "http://127.0.0.1:%d".formatted(server.getAddress().getPort()), "/chat/completions");

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
        } finally {
            server.stop(0);
        }
    }

    @Test
    void generatesScriptFromProviderResponseInsteadOfTemplate() throws Exception {
        String token = registerUser("13800013020", "Real Generate Owner");
        Long tenantId = createTenant(token, "真实文本生成团队");
        Long ownerId = userIdByMobile("13800013020");
        Long projectId = createProject(token, tenantId, ownerId, "真实生成项目", "SCRIPT_REAL_GENERATE");
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = """
                {"choices":[{"message":{"content":"模型返回的完整剧本正文"}}]}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            createTextServiceConfig(token, tenantId, "http://127.0.0.1:%d".formatted(server.getAddress().getPort()), "/chat/completions");

            mockMvc.perform(post("/api/projects/%d/scripts/ai-generate".formatted(projectId))
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .header("X-Tenant-Id", tenantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "title":"真实剧本",
                          "storyIdea":"主角回归后发现AI服务已经接通",
                          "genre":"逆袭"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.script.title", is("真实剧本")))
                .andExpect(jsonPath("$.data.script.content", is("模型返回的完整剧本正文")));

            assertThat(requestBody.get()).contains("主角回归后发现AI服务已经接通");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void extractsCharactersAndScenesFromCurrentScript() throws Exception {
        String token = registerUser("13800013003", "Extract Owner");
        Long tenantId = createTenant(token, "AI元素团队");
        ArrayDeque<String> responses = new ArrayDeque<>();
        responses.add("Script content for extraction.");
        responses.add("""
            {"characters":[{"name":"LINA","roleType":"LEAD","gender":"女","ageRange":"25-30","identity":"lead","personality":["calm"],"appearance":"black coat","prompt":"CHAR-LINA"}]}
            """);
        responses.add("""
            {"scenes":[{"name":"BALLROOM","sceneType":"INTERIOR","atmosphere":"tense","description":"mansion ballroom","visualStyle":"contrast","prompt":"SCENE-BALLROOM"}]}
            """);
        HttpServer server = startQueuedTextServer(responses);
        Long ownerId = userIdByMobile("13800013003");
        Long projectId = createProject(token, tenantId, ownerId, "豪门元素", "SCRIPT_WORKFLOW_EXTRACT");

        try {
            createTextServiceConfig(token, tenantId, "http://127.0.0.1:%d".formatted(server.getAddress().getPort()), "/chat/completions");

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
                .andExpect(jsonPath("$.data.characters", hasSize(1)))
                .andExpect(jsonPath("$.data.characters[0].name", is("LINA")));

            mockMvc.perform(post("/api/projects/%d/scripts/ai-extract-elements".formatted(projectId))
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .header("X-Tenant-Id", tenantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"elementType":"SCENE"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scenes", hasSize(1)))
                .andExpect(jsonPath("$.data.scenes[0].name", is("BALLROOM")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void completesTextWorkflowEditingStoryboardPromptsAndLogs() throws Exception {
        String token = registerUser("13800013004", "Workflow Owner");
        Long tenantId = createTenant(token, "AI文本全链路团队");
        Long ownerId = userIdByMobile("13800013004");
        ArrayDeque<String> responses = new ArrayDeque<>();
        responses.add("Full workflow generated script.");
        responses.add("Full workflow rewritten script.");
        responses.add("""
            {
              "characters":[
                {"name":"LEAD_A","roleType":"LEAD","gender":"女","ageRange":"25-30","identity":"lead","personality":["calm"],"appearance":"coat","prompt":"CHAR-A"},
                {"name":"SUPPORT_B","roleType":"SUPPORTING","gender":"未知","ageRange":"30-40","identity":"support","personality":["careful"],"appearance":"suit","prompt":"CHAR-B"}
              ],
              "scenes":[
                {"name":"SCENE_A","sceneType":"INTERIOR","atmosphere":"tense","description":"hall","visualStyle":"contrast","prompt":"SCENE-A"},
                {"name":"SCENE_B","sceneType":"EXTERIOR","atmosphere":"rain","description":"gate","visualStyle":"cold","prompt":"SCENE-B"}
              ],
              "props":[{"name":"PROP_A","propType":"DOCUMENT","appearance":"paper","plotFunction":"proof","relatedCharacter":"LEAD_A","prompt":"PROP-A"}]
            }
            """);
        responses.add("""
            {
              "storyboards":[
                {"episodeNo":1,"shotNo":1,"shotType":"WIDE","visualDescription":"Shot one","characters":"LEAD_A","actions":"enters","dialogue":"Line one","scene":"SCENE_A","props":"PROP_A","mood":"tense","durationSeconds":5,"imagePrompt":"FRAME-1","videoPrompt":"VIDEO-1"},
                {"episodeNo":1,"shotNo":2,"shotType":"MID","visualDescription":"Shot two","characters":"SUPPORT_B","actions":"steps back","dialogue":"Line two","scene":"SCENE_A","props":"PROP_A","mood":"shock","durationSeconds":6,"imagePrompt":"FRAME-2","videoPrompt":"VIDEO-2"},
                {"episodeNo":1,"shotNo":3,"shotType":"CLOSE","visualDescription":"Shot three","characters":"LEAD_A","actions":"shows proof","dialogue":"Line three","scene":"SCENE_B","props":"PROP_A","mood":"reveal","durationSeconds":4,"imagePrompt":"FRAME-3","videoPrompt":"VIDEO-3"}
              ]
            }
            """);
        HttpServer server = startQueuedTextServer(responses);
        Long projectId = createProject(token, tenantId, ownerId, "全链路短剧", "SCRIPT_WORKFLOW_FULL");
        try {
            createTextServiceConfig(token, tenantId, "http://127.0.0.1:%d".formatted(server.getAddress().getPort()), "/chat/completions");

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

            MvcResult breakdownResult = mockMvc.perform(post("/api/projects/%d/storyboards/ai-breakdown".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"scope":"FULL"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.storyboards", hasSize(3)))
            .andExpect(jsonPath("$.data.storyboards[0].imagePrompt", Matchers.containsString("FRAME")))
            .andReturn();
            Long firstStoryboardId = readLong(breakdownResult, "$.data.storyboards[0].id");

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

            responses.add("""
            {
              "characters":[{"id":%d,"prompt":"ROLE-PROMPT"}],
              "storyboards":[{"id":%d,"imagePrompt":"FRAME-PROMPT","videoPrompt":"VERTICAL-DRAMA-PROMPT"}]
            }
            """.formatted(characterId, firstStoryboardId));

            mockMvc.perform(post("/api/projects/%d/prompts/ai-generate".formatted(projectId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"targetType":"ALL"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.characters[0].prompt", Matchers.containsString("ROLE")))
            .andExpect(jsonPath("$.data.storyboards[0].videoPrompt", Matchers.containsString("VERTICAL")));

            Integer callCount = jdbcTemplate.queryForObject(
            "select count(*) from ai_call_log where tenant_id = ? and business_scene in ('script_generate','script_rewrite','element_extract_ALL','storyboard_breakdown','prompt_generate')",
            Integer.class,
            tenantId
        );
            org.assertj.core.api.Assertions.assertThat(callCount).isGreaterThanOrEqualTo(5);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void parsesStructuredJsonForElementsAndStoryboards() throws Exception {
        String token = registerUser("13800013021", "Structured Owner");
        Long tenantId = createTenant(token, "结构化文本生成团队");
        Long ownerId = userIdByMobile("13800013021");
        Long projectId = createProject(token, tenantId, ownerId, "结构化生成项目", "SCRIPT_STRUCTURED_AI");
        ArrayDeque<String> responses = new ArrayDeque<>();
        responses.add("Episode 1: LINA returns to the mansion.");
        responses.add("""
            {
              "characters":[{"name":"LINA","roleType":"LEAD","gender":"女","ageRange":"25-30","identity":"returning heiress","personality":["calm","decisive"],"appearance":"black trench coat","prompt":"CHAR-PROMPT-LINA"}],
              "scenes":[{"name":"BALLROOM","sceneType":"INTERIOR","atmosphere":"tense reveal","description":"the mansion ballroom full of guests","visualStyle":"high contrast","prompt":"SCENE-PROMPT-BALLROOM"}],
              "props":[{"name":"DEED","propType":"DOCUMENT","appearance":"yellowed paper","plotFunction":"prove reversal","relatedCharacter":"LINA","prompt":"PROP-PROMPT-DEED"}]
            }
            """);
        responses.add("""
            {
              "storyboards":[{"episodeNo":1,"shotNo":1,"sceneNo":"1-1","shotType":"CLOSE","visualDescription":"LINA opens the ballroom door","characters":"LINA","actions":"LINA looks up at the crowd","dialogue":"I am back.","scene":"BALLROOM","props":"DEED","mood":"reveal","durationSeconds":6,"imagePrompt":"SHOT-IMAGE-LINA","videoPrompt":"SHOT-VIDEO-LINA"}]
            }
            """);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            String next = responses.removeFirst();
            byte[] body = openAiResponse(next).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            createTextServiceConfig(token, tenantId, "http://127.0.0.1:%d".formatted(server.getAddress().getPort()), "/chat/completions");

            mockMvc.perform(post("/api/projects/%d/scripts/ai-generate".formatted(projectId))
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .header("X-Tenant-Id", tenantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"title":"STRUCTURED SCRIPT","storyIdea":"LINA returns to the mansion","genre":"REVENGE"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.script.content", is("Episode 1: LINA returns to the mansion.")));

            MvcResult extractResult = mockMvc.perform(post("/api/projects/%d/scripts/ai-extract-elements".formatted(projectId))
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .header("X-Tenant-Id", tenantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"elementType":"ALL"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.characters[0].name", is("LINA")))
                .andExpect(jsonPath("$.data.scenes[0].name", is("BALLROOM")))
                .andExpect(jsonPath("$.data.props[0].name", is("DEED")))
                .andReturn();
            Long characterId = readLong(extractResult, "$.data.characters[0].id");
            Long sceneId = readLong(extractResult, "$.data.scenes[0].id");
            Long propId = readLong(extractResult, "$.data.props[0].id");

            MvcResult storyboardResult = mockMvc.perform(post("/api/projects/%d/storyboards/ai-breakdown".formatted(projectId))
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .header("X-Tenant-Id", tenantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"scope":"FULL"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storyboards", hasSize(1)))
                .andExpect(jsonPath("$.data.storyboards[0].imagePrompt", is("SHOT-IMAGE-LINA")))
                .andReturn();
            Long storyboardId = readLong(storyboardResult, "$.data.storyboards[0].id");
            responses.add("""
                {
                  "characters":[{"id":%d,"prompt":"FINAL-CHAR-PROMPT"}],
                  "scenes":[{"id":%d,"prompt":"FINAL-SCENE-PROMPT"}],
                  "props":[{"id":%d,"prompt":"FINAL-PROP-PROMPT"}],
                  "storyboards":[{"id":%d,"imagePrompt":"FINAL-SHOT-IMAGE","videoPrompt":"FINAL-SHOT-VIDEO"}]
                }
                """.formatted(characterId, sceneId, propId, storyboardId));

            mockMvc.perform(post("/api/projects/%d/prompts/ai-generate".formatted(projectId))
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .header("X-Tenant-Id", tenantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"targetType":"ALL"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.characters[0].prompt", is("FINAL-CHAR-PROMPT")))
                .andExpect(jsonPath("$.data.scenes[0].prompt", is("FINAL-SCENE-PROMPT")))
                .andExpect(jsonPath("$.data.props[0].prompt", is("FINAL-PROP-PROMPT")))
                .andExpect(jsonPath("$.data.storyboards[0].imagePrompt", is("FINAL-SHOT-IMAGE")))
                .andExpect(jsonPath("$.data.storyboards[0].videoPrompt", is("FINAL-SHOT-VIDEO")));
        } finally {
            server.stop(0);
        }
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

    private void createTextServiceConfig(String token, Long tenantId, String baseUrl, String endpoint) throws Exception {
        mockMvc.perform(post("/api/tenants/%d/ai-service-configs".formatted(tenantId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name":"真实文本服务",
                      "serviceType":"TEXT",
                      "provider":"OpenAI",
                      "baseUrl":"%s",
                      "apiKey":"sk-test-1234",
                      "model":"gpt-test",
                      "endpoint":"%s",
                      "priority":100,
                      "isDefault":true,
                      "enabled":true
                    }
                    """.formatted(baseUrl, endpoint)))
            .andExpect(status().isOk());
    }

    private HttpServer startQueuedTextServer(ArrayDeque<String> responses) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            String next = responses.isEmpty() ? "{}" : responses.removeFirst();
            byte[] body = openAiResponse(next).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private String openAiResponse(String content) {
        String escaped = content
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n");
        return """
            {"choices":[{"message":{"content":"%s"}}]}
            """.formatted(escaped);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
