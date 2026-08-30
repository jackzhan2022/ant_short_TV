package com.antshorttv.video;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.antshorttv.execution.AiExecutionCreateCommand;
import com.antshorttv.execution.AiExecutionService;
import com.antshorttv.accounting.AiUsageMetric;
import com.antshorttv.user.UserEntity;
import com.antshorttv.user.UserMapper;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "ai.video.scheduler.enabled=false")
@AutoConfigureMockMvc
class VideoDecompositionControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AiExecutionService aiExecutionService;

    @Test
    void uploadsVideoWithoutProjectId() throws Exception {
        String mobile = uniqueMobile();
        String token = registerUser(mobile, "Decomposition Uploader");
        Long tenantId = createTenant(token, "独立拆剧团队");

        mockMvc.perform(multipart("/api/video-script-decomposition/uploads")
                .file(new MockMultipartFile(
                    "file",
                    "episode-1.mp4",
                    "video/mp4",
                    "video bytes".getBytes()
                ))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.storagePath", org.hamcrest.Matchers.startsWith(
                "/materials/%d/video-decomposition/".formatted(tenantId)
            )));
    }

    @Test
    void createsUnboundBatchAndKeepsUploadOrderAsEpisodeNumbers() throws Exception {
        String mobile = uniqueMobile();
        String token = registerUser(mobile, "Decomposition Owner");
        Long tenantId = createTenant(token, "拆剧团队");
        Long ownerId = userIdByMobile(mobile);
        com.antshorttv.support.ModelBillingTestSupport.publish(
            jdbc, 10L, "CALL", BigDecimal.ONE, BigDecimal.ONE);
        fundPointAccount(tenantId);

        MvcResult result = mockMvc.perform(post("/api/video-script-decomposition/batches")
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name":"第一季拆剧",
                      "modelId":10,
                      "videos":[
                        {
                          "fileName":"episode-b.mp4",
                          "storagePath":"/materials/%d/video-decomposition/20260823/episode-b.mp4",
                          "mimeType":"video/mp4",
                          "fileSize":2048,
                          "durationSeconds":96.5
                        },
                        {
                          "fileName":"episode-a.mp4",
                          "storagePath":"/materials/%d/video-decomposition/20260823/episode-a.mp4",
                          "mimeType":"video/mp4",
                          "fileSize":4096,
                          "durationSeconds":88
                        }
                      ]
                    }
                    """.formatted(tenantId, tenantId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.projectId").doesNotExist())
            .andExpect(jsonPath("$.data.totalEpisodes", is(2)))
            .andExpect(jsonPath("$.data.episodes", hasSize(2)))
            .andExpect(jsonPath("$.data.episodes[0].episodeNo", is(1)))
            .andExpect(jsonPath("$.data.episodes[0].executionId").doesNotExist())
            .andExpect(jsonPath("$.data.episodes[0].sourceFileName", is("episode-b.mp4")))
            .andExpect(jsonPath("$.data.episodes[1].episodeNo", is(2)))
            .andExpect(jsonPath("$.data.episodes[1].executionId").doesNotExist())
            .andExpect(jsonPath("$.data.episodes[1].sourceFileName", is("episode-a.mp4")))
            .andReturn();

        Long batchId = readLong(result, "$.data.id");
        Long firstEpisodeId = readLong(result, "$.data.episodes[0].id");
        Long secondEpisodeId = readLong(result, "$.data.episodes[1].id");
        String orderedNames = jdbc.queryForObject("""
            select group_concat(source_file_name order by episode_no separator ',')
              from video_decomposition_episode
             where batch_id = ?
            """, String.class, batchId);

        org.assertj.core.api.Assertions.assertThat(orderedNames).isEqualTo("episode-b.mp4,episode-a.mp4");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("""
            select count(*)
              from video_decomposition_attempt
             where episode_id in (?, ?)
               and phase = 'VIDEO_ANALYSIS'
               and status = 'PENDING'
               and execution_id is null
            """, Integer.class, firstEpisodeId, secondEpisodeId)).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("""
            select count(*)
              from ai_execution_task
             where business_type = 'VIDEO_DECOMPOSITION_EPISODE'
               and business_id in (?, ?)
            """, Integer.class, firstEpisodeId, secondEpisodeId)).isZero();
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("""
            select count(*)
              from ai_point_reservation reservation
              join ai_execution_task execution on execution.id = reservation.execution_id
             where execution.business_type = 'VIDEO_DECOMPOSITION_EPISODE'
               and execution.business_id in (?, ?)
            """, Integer.class, firstEpisodeId, secondEpisodeId)).isZero();
    }

    @Test
    void updatesDraftThenConfirmsVideoImportVersion() throws Exception {
        String mobile = uniqueMobile();
        String token = registerUser(mobile, "Decomposition Reviewer");
        Long tenantId = createTenant(token, "拆剧审核团队");
        Long ownerId = userIdByMobile(mobile);
        Long projectId = createProject(token, tenantId, ownerId, "拆剧确认项目", "VIDEO_DECOMP_CONFIRM");
        Long episodeId = insertReviewableEpisode(tenantId, projectId, ownerId, "原始拆剧草稿", 0);

        mockMvc.perform(put("/api/video-script-decomposition/episodes/%d/draft".formatted(episodeId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"draftContent":"审核后第 1 集剧本","expectedDraftVersion":0}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.draftStatus", is("PENDING_REVIEW")))
            .andExpect(jsonPath("$.data.draftVersion", is(1)));

        mockMvc.perform(post("/api/video-script-decomposition/episodes/%d/confirm".formatted(episodeId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"draftContent":"审核后第 1 集剧本","expectedDraftVersion":1,"projectId":%d,"expectedCurrentScriptVersionId":null}
                    """.formatted(projectId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("CONFIRMED")))
            .andExpect(jsonPath("$.data.draftStatus", is("CONFIRMED")));

        var version = jdbc.queryForMap("""
            select sv.*
              from video_decomposition_episode e
              join script_version sv on sv.id = e.confirmed_script_version_id
             where e.id = ?
            """, episodeId);
        org.assertj.core.api.Assertions.assertThat(version.get("source_type")).isEqualTo("VIDEO_IMPORT");
        org.assertj.core.api.Assertions.assertThat((String) version.get("input_summary"))
            .contains("第1集")
            .contains("拆剧批次");
        org.assertj.core.api.Assertions.assertThat(version.get("content")).isEqualTo("审核后第 1 集剧本");
    }

    @Test
    void rejectsConfirmWhenCurrentScriptVersionChangedAndKeepsDraft() throws Exception {
        String mobile = uniqueMobile();
        String token = registerUser(mobile, "Decomposition Conflict Reviewer");
        Long tenantId = createTenant(token, "拆剧冲突团队");
        Long ownerId = userIdByMobile(mobile);
        Long projectId = createProject(token, tenantId, ownerId, "拆剧冲突项目", "VIDEO_DECOMP_CONFLICT");
        Long scriptId = insertScript(tenantId, projectId, ownerId, "当前剧本");
        Long oldVersionId = insertScriptVersion(tenantId, projectId, scriptId, ownerId, 1, "MANUAL_EDIT", "旧版本");
        Long newVersionId = insertScriptVersion(tenantId, projectId, scriptId, ownerId, 2, "MANUAL_EDIT", "新版本");
        jdbc.update("update script set current_version_id = ?, updated_at = now() where id = ?", newVersionId, scriptId);
        Long episodeId = insertReviewableEpisode(tenantId, projectId, ownerId, "待确认草稿", 3);

        mockMvc.perform(post("/api/video-script-decomposition/episodes/%d/confirm".formatted(episodeId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"draftContent":"待确认草稿","expectedDraftVersion":3,"projectId":%d,"expectedCurrentScriptVersionId":%d}
                    """.formatted(projectId, oldVersionId)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode", is("SCRIPT_VERSION_CONFLICT")));

        var episode = jdbc.queryForMap("select * from video_decomposition_episode where id = ?", episodeId);
        org.assertj.core.api.Assertions.assertThat(episode.get("draft_content")).isEqualTo("待确认草稿");
        org.assertj.core.api.Assertions.assertThat(episode.get("status")).isEqualTo("PENDING_REVIEW");
        org.assertj.core.api.Assertions.assertThat(episode.get("confirmed_script_version_id")).isNull();
    }

    @Test
    void rejectsConfirmWithoutTargetProjectId() throws Exception {
        String mobile = uniqueMobile();
        String token = registerUser(mobile, "Decomposition Missing Project");
        Long tenantId = createTenant(token, "拆剧项目校验团队");
        Long ownerId = userIdByMobile(mobile);
        Long projectId = createProject(token, tenantId, ownerId, "拆剧目标项目", "VIDEO_DECOMP_TARGET");
        Long episodeId = insertReviewableEpisode(tenantId, null, ownerId, "待确认草稿", 1);

        mockMvc.perform(post("/api/video-script-decomposition/episodes/%d/confirm".formatted(episodeId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"draftContent":"待确认草稿","expectedDraftVersion":1,"expectedCurrentScriptVersionId":null}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")));

        var episode = jdbc.queryForMap("select * from video_decomposition_episode where id = ?", episodeId);
        org.assertj.core.api.Assertions.assertThat(episode.get("project_id")).isNull();
    }

    @Test
    void rejectsRetryForConfirmedEpisode() throws Exception {
        String mobile = uniqueMobile();
        String token = registerUser(mobile, "Decomposition Retry Reviewer");
        Long tenantId = createTenant(token, "拆剧重试团队");
        Long ownerId = userIdByMobile(mobile);
        Long projectId = createProject(token, tenantId, ownerId, "拆剧重试项目", "VIDEO_DECOMP_RETRY");
        Long episodeId = insertReviewableEpisode(tenantId, projectId, ownerId, "已确认草稿", 1);
        jdbc.update("""
            update video_decomposition_episode
               set status = 'CONFIRMED',
                   draft_status = 'CONFIRMED',
                   retryable = false,
                   updated_at = now()
             where id = ?
            """, episodeId);

        mockMvc.perform(post("/api/video-script-decomposition/episodes/%d/retry".formatted(episodeId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phase\":\"VIDEO_ANALYSIS\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode", is("VALIDATION_ERROR")));

        var episode = jdbc.queryForMap("select * from video_decomposition_episode where id = ?", episodeId);
        org.assertj.core.api.Assertions.assertThat(episode.get("status")).isEqualTo("CONFIRMED");
    }

    @Test
    void technicalRetryKeepsTheFrozenExecutionAndPricingSnapshot() throws Exception {
        String mobile = uniqueMobile();
        String token = registerUser(mobile, "Technical Retry Owner");
        Long tenantId = createTenant(token, "技术重试团队");
        com.antshorttv.support.ModelBillingTestSupport.publish(
            jdbc, 10L, "CALL", BigDecimal.ONE, BigDecimal.ONE);
        fundPointAccount(tenantId);
        Long ownerId = userIdByMobile(mobile);
        MvcResult created = mockMvc.perform(post("/api/video-script-decomposition/batches")
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"技术重试","modelId":10,"videos":[{
                      "fileName":"retry.mp4",
                      "storagePath":"/materials/%d/video-decomposition/retry.mp4",
                      "mimeType":"video/mp4","fileSize":2048
                    }]}
                    """.formatted(tenantId)))
            .andExpect(status().isOk())
            .andReturn();
        Long episodeId = readLong(created, "$.data.episodes[0].id");
        Long executionId = aiExecutionService.createWithReservation(new AiExecutionCreateCommand(
            tenantId,
            ownerId,
            null,
            "video_decomposition_video_analysis",
            "VIDEO_UNDERSTANDING",
            "VIDEO_DECOMPOSITION_EPISODE",
            episodeId,
            10L,
            "VIDEO_ANALYSIS",
            "video-decomposition:%d".formatted(episodeId),
            UUID.randomUUID().toString(),
            true,
            "{\"screenplayPrompt\":\"# 第1集：标题 只输出完整合法的 JSON 对象\"}"
        ), Map.of(AiUsageMetric.CALL, BigDecimal.ONE), Map.of()).id;
        jdbc.update(
            "update video_decomposition_episode set execution_id = ?, updated_at = now() where id = ?",
            executionId,
            episodeId
        );
        var frozen = jdbc.queryForMap("""
            select requested_model_id, cost_price_version_id, point_price_version_id, execution_version
              from ai_execution_task where id = ?
            """, executionId);
        String executionSnapshot = jdbc.queryForObject(
            "select redacted_input_json from ai_execution_task where id = ?", String.class, executionId);
        org.assertj.core.api.Assertions.assertThat(executionSnapshot)
            .contains("screenplayPrompt", "# 第1集：标题", "只输出完整合法的 JSON 对象");
        int reservations = jdbc.queryForObject(
            "select count(*) from ai_point_reservation where execution_id = ?",
            Integer.class, executionId);
        jdbc.update("""
            update video_decomposition_episode
               set status = 'FAILED', retryable = true, error_code = 'AI_RATE_LIMIT'
             where id = ?
            """, episodeId);
        jdbc.update("""
            update ai_execution_task
               set status = 'FAILED', progress = 17, retryable = true,
                   error_code = 'AI_RATE_LIMIT', error_message = 'rate limited', completed_at = now()
             where id = ?
            """, executionId);

        mockMvc.perform(post("/api/video-script-decomposition/episodes/%d/retry".formatted(episodeId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("PENDING_ANALYSIS")));

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForMap("""
            select requested_model_id, cost_price_version_id, point_price_version_id, execution_version
              from ai_execution_task where id = ?
            """, executionId)).isEqualTo(frozen);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
            "select count(*) from ai_point_reservation where execution_id = ?",
            Integer.class, executionId)).isEqualTo(reservations);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
            "select count(*) from video_decomposition_attempt where episode_id = ? and phase = 'VIDEO_ANALYSIS'",
            Integer.class, episodeId)).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForMap("""
            select status, progress, error_code, error_message, completed_at
              from ai_execution_task where id = ?
            """, executionId)).satisfies(execution -> {
                org.assertj.core.api.Assertions.assertThat(execution.get("status")).isEqualTo("PENDING");
                org.assertj.core.api.Assertions.assertThat(execution.get("progress")).isEqualTo(0);
                org.assertj.core.api.Assertions.assertThat(execution.get("error_code")).isNull();
                org.assertj.core.api.Assertions.assertThat(execution.get("error_message")).isNull();
                org.assertj.core.api.Assertions.assertThat(execution.get("completed_at")).isNull();
            });
    }

    @Test
    void exposesRetryabilityInEpisodeDetail() throws Exception {
        String mobile = uniqueMobile();
        String token = registerUser(mobile, "Decomposition Detail Reviewer");
        Long tenantId = createTenant(token, "拆剧详情团队");
        Long ownerId = userIdByMobile(mobile);
        Long projectId = createProject(token, tenantId, ownerId, "拆剧详情项目", "VIDEO_DECOMP_DETAIL");
        Long episodeId = insertReviewableEpisode(tenantId, projectId, ownerId, "失败草稿", 1);
        jdbc.update("""
            update video_decomposition_episode
               set status = 'FAILED',
                   error_code = 'AI_RESPONSE_INVALID',
                   error_message = '业务解析失败',
                   retryable = true,
                   updated_at = now()
             where id = ?
            """, episodeId);

        mockMvc.perform(get("/api/video-script-decomposition/episodes/%d".formatted(episodeId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.episode.status", is("FAILED")))
            .andExpect(jsonPath("$.data.episode.retryable", is(true)));
    }

    @Test
    void returnsOrderedImmutableScreenplaysWithBatchProgressAndTenantIsolation() throws Exception {
        String mobile = uniqueMobile();
        String token = registerUser(mobile, "Screenplay Reader");
        Long tenantId = createTenant(token, "剧本结果团队");
        Long ownerId = userIdByMobile(mobile);
        Long firstEpisodeId = insertReviewableEpisode(tenantId, null, ownerId, "历史草稿", 0);
        Long batchId = jdbc.queryForObject(
            "select batch_id from video_decomposition_episode where id = ?", Long.class, firstEpisodeId);
        jdbc.update("""
            update video_decomposition_episode
               set status = 'SUCCEEDED', draft_content = null, draft_status = 'NOT_STARTED', retryable = false
             where id = ?
            """, firstEpisodeId);
        insertImmutableResult(tenantId, batchId, firstEpisodeId, "# 第1集：真相\n\n## 1-1 夜 内 客厅\n\n出场人物：林晚\n\n林晚抬头。\n\n——本集完");
        jdbc.update("""
            insert into video_decomposition_episode
              (batch_id, tenant_id, episode_no, source_file_name, storage_path, mime_type, file_size,
               status, analysis_version, draft_status, draft_version, error_code, error_message,
               retryable, created_by, created_at, updated_at)
            values (?, ?, 2, 'episode-2.mp4', ?, 'video/mp4', 2048,
                    'FAILED', 0, 'NOT_STARTED', 0, 'AI_RATE_LIMIT', '请求频率过高',
                    true, ?, now(), now())
            """, batchId, tenantId, "/materials/%d/video-decomposition/episode-2.mp4".formatted(tenantId), ownerId);

        mockMvc.perform(get("/api/video-script-decomposition/batches/%d/screenplays".formatted(batchId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("PARTIAL_FAILED")))
            .andExpect(jsonPath("$.data.percentage", is(50)))
            .andExpect(jsonPath("$.data.succeededEpisodes", is(1)))
            .andExpect(jsonPath("$.data.failedEpisodes", is(1)))
            .andExpect(jsonPath("$.data.episodes", hasSize(2)))
            .andExpect(jsonPath("$.data.episodes[0].episode.episodeNo", is(1)))
            .andExpect(jsonPath("$.data.episodes[0].screenplayContent", org.hamcrest.Matchers.startsWith("# 第1集")))
            .andExpect(jsonPath("$.data.episodes[0].formatVersion", is("markdown-screenplay-v1")))
            .andExpect(jsonPath("$.data.episodes[1].episode.episodeNo", is(2)))
            .andExpect(jsonPath("$.data.episodes[1].episode.retryable", is(true)))
            .andExpect(jsonPath("$.data.episodes[1].screenplayContent").doesNotExist());

        String otherToken = registerUser(uniqueMobile(), "Other Tenant Reader");
        Long otherTenantId = createTenant(otherToken, "其他剧本团队");
        mockMvc.perform(get("/api/video-script-decomposition/batches/%d/screenplays".formatted(batchId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(otherToken))
                .header("X-Tenant-Id", otherTenantId))
            .andExpect(status().isNotFound());
    }

    @Test
    void immutableResultCannotBeEditedOrRetried() throws Exception {
        String mobile = uniqueMobile();
        String token = registerUser(mobile, "Immutable Reader");
        Long tenantId = createTenant(token, "不可变剧本团队");
        Long ownerId = userIdByMobile(mobile);
        Long episodeId = insertReviewableEpisode(tenantId, null, ownerId, "历史草稿", 0);
        Long batchId = jdbc.queryForObject(
            "select batch_id from video_decomposition_episode where id = ?", Long.class, episodeId);
        jdbc.update("update video_decomposition_episode set status = 'SUCCEEDED', retryable = false where id = ?", episodeId);
        insertImmutableResult(tenantId, batchId, episodeId, "# 第1集：结果\n\n## 1-1 夜 内 房间\n\n出场人物：林晚\n\n林晚转身。\n\n——本集完");

        mockMvc.perform(put("/api/video-script-decomposition/episodes/%d/draft".formatted(episodeId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"draftContent\":\"试图修改\",\"expectedDraftVersion\":0}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/video-script-decomposition/episodes/%d/retry".formatted(episodeId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phase\":\"VIDEO_ANALYSIS\"}"))
            .andExpect(status().isBadRequest());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
            "select content from video_decomposition_script_result where episode_id = ?",
            String.class, episodeId)).startsWith("# 第1集：结果");
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
                    {"name":"%s","type":"STUDIO","description":"拆剧测试"}
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
                    {"name":"%s","code":"%s","description":"拆剧项目","ownerId":%d}
                    """.formatted(name, code, ownerId)))
            .andExpect(status().isOk())
            .andReturn();
        return readLong(result, "$.data.id");
    }

    private Long userIdByMobile(String mobile) {
        UserEntity user = userMapper.selectByMobile(mobile);
        return user.getId();
    }

    private void fundPointAccount(Long tenantId) {
        int updated = jdbc.update(
            "update team_point_account set balance = 100, reserved_balance = 0, updated_at = now() where tenant_id = ?",
            tenantId
        );
        if (updated == 0) {
            jdbc.update("""
                insert into team_point_account
                  (tenant_id, balance, reserved_balance, total_granted, total_consumed,
                   total_reserved, total_released, total_refunded, version, created_at, updated_at)
                values (?, 100, 0, 100, 0, 0, 0, 0, 0, now(), now())
                """, tenantId);
        }
    }

    private Long readLong(MvcResult result, String path) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return value.longValue();
    }

    private Long insertReviewableEpisode(Long tenantId, Long projectId, Long ownerId, String draftContent, int draftVersion) {
        jdbc.update("""
            insert into video_decomposition_batch
              (tenant_id, project_id, name, model_id, status, total_episodes, completed_episodes, failed_episodes, created_by, created_at, updated_at)
            values (?, ?, '确认导入批次', 10, 'PENDING_REVIEW', 1, 1, 0, ?, now(), now())
            """, tenantId, projectId, ownerId);
        Long batchId = jdbc.queryForObject("select max(id) from video_decomposition_batch where tenant_id = ?", Long.class, tenantId);
        jdbc.update("""
            insert into video_decomposition_episode
              (batch_id, tenant_id, project_id, episode_no, source_file_name, storage_path, mime_type, file_size,
               duration_seconds, status, analysis_version, draft_content, draft_status, draft_version, created_by, created_at, updated_at)
            values (?, ?, ?, 1, 'episode.mp4', ?, 'video/mp4', 2048, 90, 'PENDING_REVIEW', 1, ?, 'PENDING_REVIEW', ?, ?, now(), now())
            """, batchId, tenantId, projectId, "/materials/%d/%d/episode.mp4".formatted(tenantId, projectId), draftContent, draftVersion, ownerId);
        return jdbc.queryForObject("select max(id) from video_decomposition_episode where batch_id = ?", Long.class, batchId);
    }

    private void insertImmutableResult(Long tenantId, Long batchId, Long episodeId, String content) {
        jdbc.update("""
            insert into video_decomposition_analysis
              (episode_id, schema_version, status, raw_response, normalized_json, created_at)
            values (?, 'v1', 'SUCCEEDED', ?, ?, now())
            """, episodeId, "{\"script\":\"result\"}", "{\"script\":\"result\"}");
        Long analysisId = jdbc.queryForObject(
            "select max(id) from video_decomposition_analysis where episode_id = ?", Long.class, episodeId);
        jdbc.update("""
            insert into video_decomposition_script_result
              (tenant_id, batch_id, episode_id, analysis_id, content, format_version, created_at)
            values (?, ?, ?, ?, ?, 'markdown-screenplay-v1', now())
            """, tenantId, batchId, episodeId, analysisId, content);
    }

    private Long insertScript(Long tenantId, Long projectId, Long ownerId, String content) {
        jdbc.update("""
            insert into script
              (tenant_id, project_id, title, source_type, content, status, created_by, created_at, updated_at)
            values (?, ?, '项目剧本', 'MANUAL_EDIT', ?, 'DRAFT', ?, now(), now())
            """, tenantId, projectId, content, ownerId);
        return jdbc.queryForObject("select max(id) from script where tenant_id = ?", Long.class, tenantId);
    }

    private Long insertScriptVersion(
        Long tenantId,
        Long projectId,
        Long scriptId,
        Long ownerId,
        int versionNo,
        String sourceType,
        String content
    ) {
        jdbc.update("""
            insert into script_version
              (tenant_id, project_id, script_id, version_no, source_type, input_summary, content, status, created_by, created_at)
            values (?, ?, ?, ?, ?, '测试版本', ?, 'DRAFT', ?, now())
            """, tenantId, projectId, scriptId, versionNo, sourceType, content, ownerId);
        return jdbc.queryForObject("select max(id) from script_version where script_id = ?", Long.class, scriptId);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String uniqueMobile() {
        int suffix = java.util.concurrent.ThreadLocalRandom.current().nextInt(20_000_000, 99_999_999);
        return "139%08d".formatted(suffix);
    }
}
