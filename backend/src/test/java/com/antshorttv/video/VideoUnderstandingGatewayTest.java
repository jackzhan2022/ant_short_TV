package com.antshorttv.video;

import static org.assertj.core.api.Assertions.assertThat;

import com.antshorttv.ai.AiContext;
import com.antshorttv.ai.AiSecretCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class VideoUnderstandingGatewayTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AiSecretCodec aiSecretCodec;

    @Autowired
    private VideoUnderstandingGateway gateway;

    @Test
    void recordsRealQwenVideoUnderstandingCallLog() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] body = """
                {
                  "id":"qwen-log-test",
                  "choices":[{"message":{"content":"{\\"characters\\":[],\\"scenes\\":[],\\"props\\":[],\\"timeline\\":[],\\"dialogue\\":[],\\"actions\\":[],\\"emotions\\":[]}"}}],
                  "usage":{"prompt_tokens":3,"completion_tokens":5,"total_tokens":8}
                }
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            Long providerId = insertProvider();
            insertProviderConfig(providerId, "http://127.0.0.1:%d/v1".formatted(server.getAddress().getPort()));
            Long modelId = insertModel(providerId);

            VideoUnderstandingCallResult result = gateway.call(
                new AiContext(91L, 92L, 93L, 94L, modelId, "video_understanding", "trace-video"),
                new VideoUnderstandingRequest("https://cdn.example.com/episode.mp4", "只返回合法 JSON")
            );

            assertThat(result.response().providerRequestId()).isEqualTo("qwen-log-test");
            var log = jdbc.queryForMap("select * from ai_call_log where id = ?", result.aiCallLogId());
            assertThat(log.get("service_type")).isEqualTo("VIDEO_UNDERSTANDING");
            assertThat(log.get("business_scene")).isEqualTo("video_understanding");
            assertThat(log.get("provider_request_id")).isEqualTo("qwen-log-test");
            assertThat(log.get("total_tokens")).isEqualTo(8);
            assertThat((String) log.get("request_summary")).doesNotContain("sk-real-qwen");
        } finally {
            server.stop(0);
        }
    }

    private Long insertProvider() {
        java.util.List<Long> existing = jdbc.queryForList("select id from ai_provider where code = '阿里云百炼' limit 1", Long.class);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        jdbc.update("""
            insert into ai_provider
              (name, code, supported_types, default_base_url, status, created_at, updated_at)
            values ('阿里云百炼', '阿里云百炼', 'VIDEO_UNDERSTANDING', 'https://dashscope.aliyuncs.com/compatible-mode/v1', 'ENABLED', now(), now())
            """);
        return jdbc.queryForObject("select max(id) from ai_provider where code = '阿里云百炼'", Long.class);
    }

    private void insertProviderConfig(Long providerId, String baseUrl) {
        jdbc.update("delete from ai_provider_config where provider_id = ?", providerId);
        jdbc.update("""
            insert into ai_provider_config
              (provider_id, api_key_cipher, base_url, status, last_test_status, created_at, updated_at)
            values (?, ?, ?, 'ENABLED', 'SUCCESS', now(), now())
            """, providerId, aiSecretCodec.encrypt("sk-real-qwen"), baseUrl);
    }

    private Long insertModel(Long providerId) {
        jdbc.update("update ai_model set is_default = false where service_type = 'VIDEO_UNDERSTANDING'");
        jdbc.update("delete from ai_model where code = 'qwen-video-understanding'");
        jdbc.update("""
            insert into ai_model
              (provider_id, code, name, model_code, service_type, status, is_default, sort, created_at, updated_at)
            values (?, 'qwen-video-understanding', 'Qwen3.7 Plus', 'qwen3.7-plus', 'VIDEO_UNDERSTANDING', 'ENABLED', true, 100, now(), now())
            """, providerId);
        return jdbc.queryForObject("select max(id) from ai_model where code = 'qwen-video-understanding'", Long.class);
    }
}
