package com.antshorttv.video;

import static org.assertj.core.api.Assertions.assertThat;

import com.antshorttv.ai.AiSecretCodec;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class VideoDecompositionExecutionServiceTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AiSecretCodec aiSecretCodec;

    @Autowired
    private VideoDecompositionExecutionService executionService;

    @Test
    void generatesDraftAfterStructuredAnalysisSucceeds() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            int callNo = calls.incrementAndGet();
            String json = callNo == 1
                ? """
                    {
                      "id":"qwen-analysis-success",
                      "choices":[{"message":{"content":"{\\"characters\\":[],\\"scenes\\":[],\\"props\\":[],\\"timeline\\":[],\\"dialogue\\":[],\\"actions\\":[],\\"emotions\\":[]}"}}],
                      "usage":{"prompt_tokens":3,"completion_tokens":5,"total_tokens":8}
                    }
                    """
                : """
                    {
                      "id":"qwen-draft-success",
                      "choices":[{"message":{"content":"第 1 集\\n场景一：客厅。\\n人物对白：你好。"}}],
                      "usage":{"prompt_tokens":11,"completion_tokens":13,"total_tokens":24}
                    }
                    """;
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            String baseUrl = "http://127.0.0.1:%d/v1".formatted(server.getAddress().getPort());
            Long modelId = prepareModel(baseUrl);
            prepareTextModel(baseUrl);
            Long batchId = insertBatch(modelId);
            Long episodeId = insertEpisode(batchId);

            executionService.executeEpisode(episodeId);
            executionService.executeEpisode(episodeId);

            var episode = jdbc.queryForMap("select * from video_decomposition_episode where id = ?", episodeId);
            assertThat(episode.get("status")).as("episode row: %s", episode).isEqualTo("PENDING_REVIEW");
            assertThat(episode.get("draft_status")).isEqualTo("PENDING_REVIEW");
            assertThat((String) episode.get("draft_content")).contains("第 1 集");
            assertThat(episode.get("draft_version")).isEqualTo(1);
            assertThat(calls.get()).isEqualTo(2);
            assertThat(episode.get("execution_token")).isNull();
            assertThat(episode.get("retryable")).isEqualTo(false);
            Integer draftLogs = jdbc.queryForObject("""
                select count(*) from ai_call_log
                 where task_id = ? and business_scene = 'video_script_draft'
                """, Integer.class, episodeId);
            assertThat(draftLogs).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void marksTransportSuccessAsFailedWhenStructuredParsingFails() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] body = """
                {
                  "id":"qwen-parse-failure",
                  "choices":[{"message":{"content":"{\\"characters\\":[]}"}}],
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
            Long modelId = prepareModel("http://127.0.0.1:%d/v1".formatted(server.getAddress().getPort()));
            Long batchId = insertBatch(modelId);
            Long episodeId = insertEpisode(batchId);

            executionService.executeEpisode(episodeId);

            var episode = jdbc.queryForMap("select * from video_decomposition_episode where id = ?", episodeId);
            assertThat(episode.get("status")).isEqualTo("FAILED");
            assertThat(episode.get("error_code")).as("episode row: %s", episode).isEqualTo("AI_RESPONSE_INVALID");
            var analysis = jdbc.queryForMap("select * from video_decomposition_analysis where episode_id = ?", episodeId);
            assertThat(analysis.get("status")).isEqualTo("FAILED");
            assertThat((String) analysis.get("raw_response")).contains("\"characters\"");
            var log = jdbc.queryForMap("select * from ai_call_log where id = ?", analysis.get("ai_call_log_id"));
            assertThat(log.get("status")).isEqualTo("FAILED");
            assertThat((String) log.get("error_message")).contains("业务解析失败");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void linksAttemptToFailedAiCallLogWhenProviderFails() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] body = "{\"error\":{\"message\":\"too many requests\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(429, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            Long modelId = prepareModel("http://127.0.0.1:%d/v1".formatted(server.getAddress().getPort()));
            Long batchId = insertBatch(modelId);
            Long episodeId = insertEpisode(batchId);

            executionService.executeEpisode(episodeId);

            var episode = jdbc.queryForMap("select * from video_decomposition_episode where id = ?", episodeId);
            assertThat(episode.get("status")).isEqualTo("FAILED");
            assertThat(episode.get("error_code")).isEqualTo("AI_RATE_LIMIT");
            var attempt = jdbc.queryForMap("select * from video_decomposition_attempt where episode_id = ? and phase = 'VIDEO_ANALYSIS'", episodeId);
            assertThat(attempt.get("status")).isEqualTo("FAILED");
            assertThat(attempt.get("ai_call_log_id")).isNotNull();
            var log = jdbc.queryForMap("select * from ai_call_log where id = ?", attempt.get("ai_call_log_id"));
            assertThat(log.get("status")).isEqualTo("FAILED");
            assertThat(log.get("business_scene")).isEqualTo("video_understanding");
            assertThat((String) log.get("error_message")).contains("AI_RATE_LIMIT");
        } finally {
            server.stop(0);
        }
    }

    private Long prepareModel(String baseUrl) {
        java.util.List<Long> providerIds = jdbc.queryForList("select id from ai_provider where code = '阿里云百炼' limit 1", Long.class);
        Long providerId = providerIds.isEmpty() ? null : providerIds.get(0);
        if (providerId == null) {
            jdbc.update("""
                insert into ai_provider
                  (name, code, supported_types, default_base_url, status, created_at, updated_at)
                values ('阿里云百炼', '阿里云百炼', 'VIDEO_UNDERSTANDING', ?, 'ENABLED', now(), now())
                """, baseUrl);
            providerId = jdbc.queryForObject("select max(id) from ai_provider where code = '阿里云百炼'", Long.class);
        }
        jdbc.update("delete from ai_provider_config where provider_id = ?", providerId);
        jdbc.update("""
            insert into ai_provider_config
              (provider_id, api_key_cipher, base_url, status, last_test_status, created_at, updated_at)
            values (?, ?, ?, 'ENABLED', 'SUCCESS', now(), now())
            """, providerId, aiSecretCodec.encrypt("sk-real-qwen"), baseUrl);
        jdbc.update("delete from ai_model where code = 'qwen-video-understanding-execution'");
        jdbc.update("update ai_model set is_default = false where service_type = 'VIDEO_UNDERSTANDING'");
        jdbc.update("""
            insert into ai_model
              (provider_id, code, name, model_code, service_type, status, is_default, sort, created_at, updated_at)
            values (?, 'qwen-video-understanding-execution', 'Qwen3.7 Plus', 'qwen3.7-plus', 'VIDEO_UNDERSTANDING', 'ENABLED', true, 100, now(), now())
            """, providerId);
        return jdbc.queryForObject("select max(id) from ai_model where code = 'qwen-video-understanding-execution'", Long.class);
    }

    private Long prepareTextModel(String baseUrl) {
        java.util.List<Long> providerIds = jdbc.queryForList("select id from ai_provider where code = '阿里云百炼' limit 1", Long.class);
        Long providerId = providerIds.isEmpty() ? null : providerIds.get(0);
        if (providerId == null) {
            jdbc.update("""
                insert into ai_provider
                  (name, code, supported_types, default_base_url, status, created_at, updated_at)
                values ('阿里云百炼', '阿里云百炼', 'TEXT,VIDEO_UNDERSTANDING', ?, 'ENABLED', now(), now())
                """, baseUrl);
            providerId = jdbc.queryForObject("select max(id) from ai_provider where code = '阿里云百炼'", Long.class);
        } else {
            jdbc.update("update ai_provider set supported_types = 'TEXT,VIDEO_UNDERSTANDING' where id = ?", providerId);
        }
        jdbc.update("delete from ai_model where code = 'qwen-text-draft-execution'");
        jdbc.update("update ai_model set is_default = false where service_type = 'TEXT'");
        jdbc.update("""
            insert into ai_model
              (provider_id, code, name, model_code, service_type, status, is_default, sort, created_at, updated_at)
            values (?, 'qwen-text-draft-execution', 'Qwen Draft', 'qwen-plus', 'TEXT', 'ENABLED', true, 100, now(), now())
            """, providerId);
        return jdbc.queryForObject("select max(id) from ai_model where code = 'qwen-text-draft-execution'", Long.class);
    }

    private Long insertBatch(Long modelId) {
        jdbc.update("""
            insert into video_decomposition_batch
              (tenant_id, project_id, name, model_id, status, total_episodes, completed_episodes, failed_episodes, created_by, created_at, updated_at)
            values (501, 601, '执行拆剧', ?, 'PENDING_ANALYSIS', 1, 0, 0, 701, now(), now())
            """, modelId);
        return jdbc.queryForObject("select max(id) from video_decomposition_batch where tenant_id = 501", Long.class);
    }

    private Long insertEpisode(Long batchId) {
        jdbc.update("""
            insert into video_decomposition_episode
              (batch_id, tenant_id, project_id, episode_no, source_file_name, storage_path, mime_type, file_size,
               duration_seconds, status, analysis_version, draft_status, draft_version, created_by, created_at, updated_at)
            values (?, 501, 601, 1, 'episode.mp4', 'https://cdn.example.com/episode.mp4', 'video/mp4', 2048,
               90, 'PENDING_ANALYSIS', 0, 'NOT_STARTED', 0, 701, now(), now())
            """, batchId);
        Long episodeId = jdbc.queryForObject("select max(id) from video_decomposition_episode where batch_id = ?", Long.class, batchId);
        jdbc.update("""
            insert into video_decomposition_attempt
              (episode_id, attempt_no, phase, status, started_at)
            values (?, 1, 'VIDEO_ANALYSIS', 'PENDING', now())
            """, episodeId);
        return episodeId;
    }
}
