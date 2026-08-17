package com.antshorttv.ai;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
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
class AiServiceConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void providersRequireLogin() throws Exception {
        mockMvc.perform(get("/api/ai-providers"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode", is("UNAUTHORIZED")));
    }

    @Test
    void createsListsAndMasksAiServiceConfig() throws Exception {
        String token = registerUser("13800012001", "AI Owner");
        Long tenantId = createTenant(token, "AI服务团队");

        MvcResult result = createConfig(token, tenantId, "OpenAI 文本服务", "TEXT", "OpenAI", true);
        Long configId = readLong(result, "$.data.id");

        mockMvc.perform(get("/api/tenants/%d/ai-service-configs".formatted(tenantId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].id", is(configId.intValue())))
            .andExpect(jsonPath("$.data[0].apiKey", is("sk-****1234")))
            .andExpect(jsonPath("$.data[0].apiKey", not("sk-test-1234")))
            .andExpect(jsonPath("$.data[0].isDefault", is(true)))
            .andExpect(jsonPath("$.data[0].lastTestStatus", is("UNTESTED")));
    }

    @Test
    void keepsOneDefaultPerTenantAndServiceType() throws Exception {
        String token = registerUser("13800012002", "Default Owner");
        Long tenantId = createTenant(token, "默认服务团队");

        MvcResult first = createConfig(token, tenantId, "Gemini 文本服务", "TEXT", "Gemini", true);
        Long firstId = readLong(first, "$.data.id");
        MvcResult second = createConfig(token, tenantId, "火山文本服务", "TEXT", "火山", true);
        Long secondId = readLong(second, "$.data.id");

        mockMvc.perform(get("/api/tenants/%d/ai-service-configs".formatted(tenantId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.id == %d)].isDefault".formatted(firstId), hasSize(1)))
            .andExpect(jsonPath("$.data[?(@.id == %d)].isDefault".formatted(firstId), hasItem(false)))
            .andExpect(jsonPath("$.data[?(@.id == %d)].isDefault".formatted(secondId), hasItem(true)));
    }

    @Test
    void updatesStatusSetsDefaultTestsAndDeletesConfig() throws Exception {
        String token = registerUser("13800012003", "Operate Owner");
        Long tenantId = createTenant(token, "操作服务团队");
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        Long configId;
        try {
            String baseUrl = "http://127.0.0.1:%d".formatted(server.getAddress().getPort());
            configId = readLong(
                createConfig(token, tenantId, "MiniMax 语音服务", "VOICE", "MiniMax", false, baseUrl, "/health"),
                "$.data.id"
            );

            mockMvc.perform(put("/api/tenants/%d/ai-service-configs/%d/status".formatted(tenantId, configId))
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled", is(false)));

            mockMvc.perform(put("/api/tenants/%d/ai-service-configs/%d/default".formatted(tenantId, configId))
                    .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault", is(true)));

            mockMvc.perform(post("/api/tenants/%d/ai-service-configs/%d/test".formatted(tenantId, configId))
                    .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("SUCCESS")));
            assertThat(authorization.get()).isEqualTo("Bearer sk-test-1234");

            mockMvc.perform(delete("/api/tenants/%d/ai-service-configs/%d".formatted(tenantId, configId))
                    .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", nullValue()));

            mockMvc.perform(get("/api/tenants/%d/ai-service-configs".formatted(tenantId))
                    .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void blocksCrossTenantConfigAccess() throws Exception {
        String firstToken = registerUser("13800012004", "First Owner");
        Long firstTenantId = createTenant(firstToken, "第一AI团队");
        String secondToken = registerUser("13800012005", "Second Owner");
        Long secondTenantId = createTenant(secondToken, "第二AI团队");
        Long configId = readLong(
            createConfig(firstToken, firstTenantId, "OpenAI 图片服务", "IMAGE", "OpenAI", false),
            "$.data.id"
        );

        mockMvc.perform(put("/api/tenants/%d/ai-service-configs/%d/default".formatted(secondTenantId, configId))
                .header(HttpHeaders.AUTHORIZATION, bearer(secondToken)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode", is("AI_SERVICE_CONFIG_NOT_FOUND")));
    }

    @Test
    void validationErrorsDoNotEchoApiKey() throws Exception {
        String token = registerUser("13800012006", "Validation Owner");
        Long tenantId = createTenant(token, "校验服务团队");
        String oversizedApiKey = "sk-" + "x".repeat(600);

        MvcResult result = mockMvc.perform(post("/api/tenants/%d/ai-service-configs".formatted(tenantId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name":"OpenAI 文本服务",
                      "serviceType":"TEXT",
                      "provider":"OpenAI",
                      "baseUrl":"https://example.com/v1",
                      "apiKey":"%s",
                      "model":"test-model",
                      "priority":100,
                      "isDefault":false,
                      "enabled":true
                    }
                    """.formatted(oversizedApiKey)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")))
            .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(oversizedApiKey);
    }

    private MvcResult createConfig(
        String token,
        Long tenantId,
        String name,
        String serviceType,
        String provider,
        boolean isDefault
    ) throws Exception {
        return createConfig(token, tenantId, name, serviceType, provider, isDefault, "https://example.com/v1", "/chat/completions");
    }

    private MvcResult createConfig(
        String token,
        Long tenantId,
        String name,
        String serviceType,
        String provider,
        boolean isDefault,
        String baseUrl,
        String endpoint
    ) throws Exception {
        return mockMvc.perform(post("/api/tenants/%d/ai-service-configs".formatted(tenantId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name":"%s",
                      "serviceType":"%s",
                      "provider":"%s",
                      "baseUrl":"%s",
                      "apiKey":"sk-test-1234",
                      "model":"test-model",
                      "endpoint":"%s",
                      "priority":100,
                      "isDefault":%s,
                      "enabled":true,
                      "remark":"测试配置"
                    }
                    """.formatted(name, serviceType, provider, baseUrl, endpoint, isDefault)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name", is(name)))
            .andReturn();
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
                    {"name":"%s","type":"STUDIO","description":"AI服务测试"}
                    """.formatted(name)))
            .andExpect(status().isOk())
            .andReturn();
        return readLong(result, "$.data.id");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private Long readLong(MvcResult result, String path) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return value.longValue();
    }
}
