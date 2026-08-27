package com.antshorttv.video;

import static org.assertj.core.api.Assertions.assertThat;

import com.antshorttv.ai.AiSecretCodec;
import com.antshorttv.accounting.AiUsageMetric;
import com.antshorttv.execution.AiExecutionCreateCommand;
import com.antshorttv.execution.AiExecutionService;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
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

    @Autowired
    private AiExecutionService aiExecutionService;

    @Test
    void releasesReservedPointsWhenClaimedEpisodeFailsBeforeProviderInvocation() {
        Long modelId = prepareModel("http://127.0.0.1:9/v1");
        prepareBilling(modelId);
        preparePointAccount();
        Long batchId = insertBatch(modelId);
        Long episodeId = insertEpisode(batchId);
        Long executionId = aiExecutionService.createWithReservation(new AiExecutionCreateCommand(
            501L,
            701L,
            601L,
            "video_decomposition",
            "VIDEO_UNDERSTANDING",
            "VIDEO_DECOMPOSITION_EPISODE",
            episodeId,
            modelId,
            "VIDEO_ANALYSIS",
            "recovery-" + UUID.randomUUID(),
            UUID.randomUUID().toString(),
            true,
            "{\"episodeId\":%d}".formatted(episodeId)
        ), Map.of(AiUsageMetric.CALL, BigDecimal.ONE), Map.of()).id;
        jdbc.update("""
            update video_decomposition_episode
               set status = 'ANALYZING', execution_id = ?, execution_token = 'claimed',
                   execution_phase = 'VIDEO_ANALYSIS', execution_version = 1
             where id = ?
            """, executionId, episodeId);

        executionService.recoverClaimedEpisode(episodeId, "pre-provider failure");

        var episode = jdbc.queryForMap("select * from video_decomposition_episode where id = ?", episodeId);
        assertThat(episode.get("status")).isEqualTo("FAILED");
        assertThat(episode.get("execution_token")).isNull();
        assertThat(episode.get("retryable")).isEqualTo(true);
        assertThat(jdbc.queryForObject("select status from video_decomposition_attempt where episode_id = ?", String.class, episodeId))
            .isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select status from ai_execution_attempt where execution_id = ?", String.class, executionId))
            .isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select provider_contacted from ai_execution_attempt where execution_id = ?", Boolean.class, executionId))
            .isFalse();
        assertThat(jdbc.queryForObject("select status from ai_execution_task where id = ?", String.class, executionId))
            .isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select status from ai_point_reservation where execution_id = ?", String.class, executionId))
            .isEqualTo("RELEASED");
    }

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
            Long textModelId = prepareTextModel(baseUrl);
            prepareBilling(modelId);
            prepareBilling(textModelId);
            preparePointAccount();
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
            Long draftExecutionId = jdbc.queryForObject(
                "select execution_id from video_decomposition_episode where id = ?",
                Long.class,
                episodeId
            );
            Long analysisExecutionId = jdbc.queryForObject(
                "select execution_id from video_decomposition_analysis where episode_id = ?",
                Long.class,
                episodeId
            );
            assertThat(draftExecutionId).isNotNull().isNotEqualTo(analysisExecutionId);
            assertThat(jdbc.queryForList(
                "select phase from ai_execution_attempt where execution_id = ? order by id",
                String.class,
                analysisExecutionId
            )).containsExactly("VIDEO_ANALYSIS");
            assertThat(jdbc.queryForList(
                "select phase from ai_execution_attempt where execution_id = ? order by id",
                String.class,
                draftExecutionId
            )).containsExactly("DRAFT_GENERATION");
            assertThat(jdbc.queryForObject(
                "select count(*) from ai_call_log where execution_id in (?, ?) and attempt_id is not null",
                Integer.class,
                analysisExecutionId,
                draftExecutionId
            )).isEqualTo(2);
            assertThat(jdbc.queryForObject(
                "select count(*) from video_decomposition_analysis where episode_id = ? and execution_id = ?",
                Integer.class,
                episodeId,
                analysisExecutionId
            )).isEqualTo(1);
            var analysisExecution = jdbc.queryForMap(
                "select * from ai_execution_task where id = ?", analysisExecutionId);
            var draftExecution = jdbc.queryForMap(
                "select * from ai_execution_task where id = ?", draftExecutionId);
            assertThat(analysisExecution.get("status")).isEqualTo("SUCCEEDED");
            assertThat(analysisExecution.get("requested_model_id")).isEqualTo(modelId);
            assertThat(analysisExecution.get("cost_price_version_id")).isNotNull();
            assertThat(analysisExecution.get("point_price_version_id")).isNotNull();
            assertThat(analysisExecution.get("usage_cost_status")).isEqualTo("PRICED");
            assertThat(analysisExecution.get("point_settlement_status")).isEqualTo("SETTLED");
            assertThat(draftExecution.get("status")).isEqualTo("SUCCEEDED");
            assertThat(draftExecution.get("requested_model_id")).isEqualTo(textModelId);
            assertThat(draftExecution.get("cost_price_version_id")).isNotNull();
            assertThat(draftExecution.get("point_price_version_id")).isNotNull();
            assertThat(draftExecution.get("usage_cost_status")).isEqualTo("PRICED");
            assertThat(draftExecution.get("point_settlement_status")).isEqualTo("SETTLED");
            assertThat(draftExecution.get("result_type")).isEqualTo("VIDEO_DECOMPOSITION_EPISODE");
            assertThat(draftExecution.get("result_id")).isEqualTo(episodeId);
            assertThat(jdbc.queryForObject(
                "select count(*) from ai_point_reservation where execution_id in (?, ?) and status = 'SETTLED'",
                Integer.class, analysisExecutionId, draftExecutionId)).isEqualTo(2);
            assertThat(jdbc.queryForObject(
                "select count(*) from ai_usage_cost_line where execution_id in (?, ?)",
                Integer.class, analysisExecutionId, draftExecutionId)).isEqualTo(2);
            assertThat(jdbc.queryForObject("""
                select count(*)
                  from ai_usage_cost_line cost
                  join ai_execution_task execution on execution.id = cost.execution_id
                 where execution.id in (?, ?)
                   and cost.price_version_id = execution.cost_price_version_id
                   and cost.pricing_status = 'PRICED'
                """, Integer.class, analysisExecutionId, draftExecutionId)).isEqualTo(2);
            assertThat(jdbc.queryForObject("""
                select count(*)
                  from ai_point_reservation reservation
                  join ai_execution_task execution on execution.id = reservation.execution_id
                 where execution.id in (?, ?)
                   and reservation.point_price_version_id = execution.point_price_version_id
                   and reservation.status = 'SETTLED'
                """, Integer.class, analysisExecutionId, draftExecutionId)).isEqualTo(2);
            String understandingSummary = jdbc.queryForObject("""
                select request_summary from ai_call_log
                 where task_id = ? and business_scene = 'video_understanding'
                order by id desc limit 1
                """, String.class, episodeId);
            String draftSummary = jdbc.queryForObject("""
                select request_summary from ai_call_log
                 where task_id = ? and business_scene = 'video_script_draft'
                order by id desc limit 1
                """, String.class, episodeId);
            assertThat(understandingSummary).startsWith("[Agent:video-understanding]");
            assertThat(draftSummary).startsWith("[Agent:video-script-draft]");
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
            prepareBilling(modelId);
            preparePointAccount();
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
            prepareBilling(modelId);
            preparePointAccount();
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
        Long modelId = jdbc.queryForObject("select max(id) from ai_model where code = 'qwen-video-understanding-execution'", Long.class);
        insertCapability(modelId, "VIDEO_UNDERSTANDING");
        return modelId;
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
        Long modelId = jdbc.queryForObject("select max(id) from ai_model where code = 'qwen-text-draft-execution'", Long.class);
        insertCapability(modelId, "TEXT_GENERATION");
        return modelId;
    }

    private void insertCapability(Long modelId, String capability) {
        jdbc.update("""
            insert into ai_model_capability
              (model_id, capability, status, created_at, updated_at)
            values (?, ?, 'ENABLED', now(), now())
            """, modelId, capability);
    }

    @Test
    void keepsReservationForSettlementReviewWhenProviderResetsConnection() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> exchange.close());
        server.start();

        try {
            Long modelId = prepareModel("http://127.0.0.1:%d/v1".formatted(server.getAddress().getPort()));
            prepareBilling(modelId);
            preparePointAccount();
            Long batchId = insertBatch(modelId);
            Long episodeId = insertEpisode(batchId);

            executionService.executeEpisode(episodeId);

            assertThat(jdbc.queryForObject("""
                select status from ai_point_reservation
                 where execution_id = (select execution_id from video_decomposition_episode where id = ?)
                """, String.class, episodeId)).isEqualTo("SETTLEMENT_REVIEW_REQUIRED");
        } finally {
            server.stop(0);
        }
    }

    private void prepareBilling(Long modelId) {
        com.antshorttv.support.ModelBillingTestSupport.publish(
            jdbc, modelId, "CALL", BigDecimal.ONE, BigDecimal.ONE);
    }

    private void preparePointAccount() {
        Integer count = jdbc.queryForObject(
            "select count(*) from team_point_account where tenant_id = 501", Integer.class);
        if (count == 0) {
            jdbc.update("""
                insert into team_point_account
                  (tenant_id, balance, reserved_balance, total_granted, total_consumed,
                   total_reserved, total_released, total_refunded, version, created_at, updated_at)
                values (501, 1000, 0, 1000, 0, 0, 0, 0, 0, now(), now())
                """);
        }
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
