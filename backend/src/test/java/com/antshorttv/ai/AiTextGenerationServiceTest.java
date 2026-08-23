package com.antshorttv.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.antshorttv.common.BusinessException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class AiTextGenerationServiceTest {

    @Autowired
    private AiTextGenerationService aiTextGenerationService;

    @Autowired
    private AiSecretCodec aiSecretCodec;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void generatesTextThroughOpenAiCompatibleEndpoint() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = """
                {"choices":[{"message":{"content":"模型生成的短剧正文"}}]}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            Long tenantId = 91001L;
            Long userId = 92001L;
            insertTextService(tenantId, userId, "OpenAI", "http://127.0.0.1:%d".formatted(server.getAddress().getPort()), null);

            AiTextGenerationResponse response = aiTextGenerationService.generateText(new AiTextGenerationRequest(
                tenantId,
                userId,
                "script_generate",
                "落魄千金回归",
                "你是短剧编剧。",
                "生成一集短剧正文。"
            ));

            assertThat(response.content()).isEqualTo("模型生成的短剧正文");
            assertThat(response.callLogId()).isNotNull();
            assertThat(authorization.get()).isEqualTo("Bearer sk-test-1234");
            assertThat(requestBody.get()).contains("\"model\":\"gpt-test\"");
            assertThat(requestBody.get()).contains("生成一集短剧正文。");
            Integer successCount = jdbcTemplate.queryForObject(
                "select count(*) from ai_call_log where id = ? and tenant_id = ? and status = 'SUCCESS'",
                Integer.class,
                response.callLogId(),
                tenantId
            );
            assertThat(successCount).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void generatesTextThroughGeminiEndpoint() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta/models/gemini-test:generateContent", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = """
                {"candidates":[{"content":{"parts":[{"text":"Gemini 生成的短剧正文"}]}}]}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            Long tenantId = 91002L;
            Long userId = 92002L;
            insertTextService(tenantId, userId, "gemini", "http://127.0.0.1:%d".formatted(server.getAddress().getPort()), null, "gemini-test");

            AiTextGenerationResponse response = aiTextGenerationService.generateText(new AiTextGenerationRequest(
                tenantId,
                userId,
                "script_generate",
                "Gemini 生成",
                "你是短剧编剧。",
                "生成一集短剧正文。"
            ));

            assertThat(response.content()).isEqualTo("Gemini 生成的短剧正文");
            assertThat(authorization.get()).isEqualTo("Bearer sk-test-1234");
            assertThat(requestBody.get()).contains("\"contents\"");
            assertThat(requestBody.get()).contains("生成一集短剧正文。");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void recordsFailedCallWhenProviderReturnsHttpError() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            byte[] body = """
                {"error":"rate limited"}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(429, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            Long tenantId = 91003L;
            Long userId = 92003L;
            insertTextService(tenantId, userId, "OpenAI", "http://127.0.0.1:%d".formatted(server.getAddress().getPort()), null);

            assertThatThrownBy(() -> aiTextGenerationService.generateText(new AiTextGenerationRequest(
                tenantId,
                userId,
                "script_generate",
                "失败日志",
                "你是短剧编剧。",
                "生成一集短剧正文。"
            )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("HTTP 429");

            Integer failedCount = jdbcTemplate.queryForObject(
                "select count(*) from ai_call_log where tenant_id = ? and business_scene = 'script_generate' and status = 'FAILED' and error_message like '%HTTP 429%'",
                Integer.class,
                tenantId
            );
            assertThat(failedCount).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    private void insertTextService(Long tenantId, Long userId, String provider, String baseUrl, String endpoint) {
        insertTextService(tenantId, userId, provider, baseUrl, endpoint, "gpt-test");
    }

    private void insertTextService(Long tenantId, Long userId, String provider, String baseUrl, String endpoint, String model) {
        jdbcTemplate.update("""
            insert into ai_service_config
              (tenant_id, provider, service_type, name, base_url, api_key_cipher, model, endpoint, priority, is_default, enabled, last_test_status, created_by, created_at, updated_at)
            values (?, ?, 'TEXT', '测试文本服务', ?, ?, ?, ?, 100, true, true, 'SUCCESS', ?, now(), now())
            """, tenantId, provider, baseUrl, aiSecretCodec.encrypt("sk-test-1234"), model, endpoint, userId);
    }
}
