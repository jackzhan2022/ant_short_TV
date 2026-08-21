package com.antshorttv.video;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.antshorttv.user.UserEntity;
import com.antshorttv.user.UserMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

    @Test
    void createsBatchAndKeepsUploadOrderAsEpisodeNumbers() throws Exception {
        String mobile = uniqueMobile();
        String token = registerUser(mobile, "Decomposition Owner");
        Long tenantId = createTenant(token, "拆剧团队");
        Long ownerId = userIdByMobile(mobile);
        Long projectId = createProject(token, tenantId, ownerId, "拆剧项目", "VIDEO_DECOMP_ORDER");

        MvcResult result = mockMvc.perform(post("/api/video-script-decomposition/batches")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "projectId":%d,
                      "name":"第一季拆剧",
                      "modelId":10,
                      "videos":[
                        {
                          "fileName":"episode-b.mp4",
                          "storagePath":"/materials/%d/%d/episode-b.mp4",
                          "mimeType":"video/mp4",
                          "fileSize":2048,
                          "durationSeconds":96.5
                        },
                        {
                          "fileName":"episode-a.mp4",
                          "storagePath":"/materials/%d/%d/episode-a.mp4",
                          "mimeType":"video/mp4",
                          "fileSize":4096,
                          "durationSeconds":88
                        }
                      ]
                    }
                    """.formatted(projectId, tenantId, projectId, tenantId, projectId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.projectId", is(projectId.intValue())))
            .andExpect(jsonPath("$.data.totalEpisodes", is(2)))
            .andExpect(jsonPath("$.data.episodes", hasSize(2)))
            .andExpect(jsonPath("$.data.episodes[0].episodeNo", is(1)))
            .andExpect(jsonPath("$.data.episodes[0].sourceFileName", is("episode-b.mp4")))
            .andExpect(jsonPath("$.data.episodes[1].episodeNo", is(2)))
            .andExpect(jsonPath("$.data.episodes[1].sourceFileName", is("episode-a.mp4")))
            .andReturn();

        Long batchId = readLong(result, "$.data.id");
        String orderedNames = jdbc.queryForObject("""
            select group_concat(source_file_name order by episode_no separator ',')
              from video_decomposition_episode
             where batch_id = ?
            """, String.class, batchId);

        org.assertj.core.api.Assertions.assertThat(orderedNames).isEqualTo("episode-b.mp4,episode-a.mp4");
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
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"draftContent":"审核后第 1 集剧本","expectedDraftVersion":0}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.draftStatus", is("PENDING_REVIEW")))
            .andExpect(jsonPath("$.data.draftVersion", is(1)));

        mockMvc.perform(post("/api/video-script-decomposition/episodes/%d/confirm".formatted(episodeId))
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"draftContent":"审核后第 1 集剧本","expectedDraftVersion":1,"expectedCurrentScriptVersionId":null}
                    """))
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
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"draftContent":"待确认草稿","expectedDraftVersion":3,"expectedCurrentScriptVersionId":%d}
                    """.formatted(oldVersionId)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorCode", is("SCRIPT_VERSION_CONFLICT")));

        var episode = jdbc.queryForMap("select * from video_decomposition_episode where id = ?", episodeId);
        org.assertj.core.api.Assertions.assertThat(episode.get("draft_content")).isEqualTo("待确认草稿");
        org.assertj.core.api.Assertions.assertThat(episode.get("status")).isEqualTo("PENDING_REVIEW");
        org.assertj.core.api.Assertions.assertThat(episode.get("confirmed_script_version_id")).isNull();
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
                    {"name":"%s","type":"STUDIO","description":"拆剧测试"}
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
