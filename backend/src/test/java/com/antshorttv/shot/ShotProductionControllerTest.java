package com.antshorttv.shot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.antshorttv.user.UserEntity;
import com.antshorttv.user.UserMapper;
import com.jayway.jsonpath.JsonPath;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = {
    "ai.video.storage-root=target/test-shot-storage"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ShotProductionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserMapper userMapper;

    @Test
    void createsVoiceSubtitleComposeSavesBindsAndProtectsSelectedShotResult() throws Exception {
        String token = registerUser("13800017001", "Shot Owner");
        Long tenantId = createTenant(token, "五期制作团队");
        Long ownerId = userIdByMobile("13800017001");
        Long projectId = createProject(token, tenantId, ownerId, "五期项目", "SHOT_PHASE_5");
        Long serviceConfigId = createVoiceService(token, tenantId);
        Long storyboardId = createStoryboard(tenantId, projectId, ownerId);
        grantTeamPoints(tenantId, 1);

        MvcResult voiceCreate = mockMvc.perform(post("/api/projects/%d/ai-voice-tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "storyboardId":%d,
                      "serviceConfigId":%d,
                      "voiceType":"DIALOGUE",
                      "speakerName":"女主",
                      "voiceId":"female-cn-01",
                      "textContent":"你终于来了。",
                      "speed":1.0,
                      "pitch":1.0,
                      "volume":1.0
                    }
                    """.formatted(storyboardId, serviceConfigId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("SUCCEEDED")))
            .andExpect(jsonPath("$.data.results", hasSize(1)))
            .andExpect(jsonPath("$.data.results[0].audioUrl", containsString(".mp3")))
            .andReturn();
        Long voiceResultId = readLong(voiceCreate, "$.data.results[0].id");

        mockMvc.perform(post("/api/projects/%d/ai-voice-results/%d/bind-storyboard".formatted(projectId, voiceResultId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.selected", is(true)));

        MvcResult subtitleCreate = mockMvc.perform(post("/api/projects/%d/storyboard-subtitles".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "storyboardId":%d,
                      "voiceResultId":%d,
                      "subtitleType":"DIALOGUE",
                      "textContent":"你终于来了。",
                      "styleConfig":{"fontSize":"MEDIUM","position":"BOTTOM"}
                    }
                    """.formatted(storyboardId, voiceResultId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.srtUrl", containsString(".srt")))
            .andExpect(jsonPath("$.data.segments", hasSize(1)))
            .andReturn();
        Long subtitleId = readLong(subtitleCreate, "$.data.id");

        mockMvc.perform(put("/api/projects/%d/storyboard-subtitles/%d/selected".formatted(projectId, subtitleId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.selected", is(true)));

        MvcResult composeCreate = mockMvc.perform(post("/api/projects/%d/shot-compose-tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "storyboardId":%d,
                      "voiceResultId":%d,
                      "subtitleId":%d,
                      "includeSubtitle":true,
                      "audioVolume":1.0,
                      "outputFormat":"mp4"
                    }
                    """.formatted(storyboardId, voiceResultId, subtitleId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("SUCCEEDED")))
            .andExpect(jsonPath("$.data.results", hasSize(1)))
            .andExpect(jsonPath("$.data.results[0].videoUrl", containsString(".mp4")))
            .andReturn();
        Long composeResultId = readLong(composeCreate, "$.data.results[0].id");

        mockMvc.perform(post("/api/projects/%d/shot-compose-results/%d/save-material".formatted(projectId, composeResultId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.materialId", notNullValue()));

        mockMvc.perform(post("/api/projects/%d/shot-compose-results/%d/bind-storyboard".formatted(projectId, composeResultId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.selected", is(true)));

        mockMvc.perform(delete("/api/projects/%d/shot-compose-results/%d".formatted(projectId, composeResultId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isConflict());

        String storagePath = jdbc.queryForObject(
            "select storage_path from shot_compose_result where id = ?",
            String.class,
            composeResultId
        );
        assert Files.exists(Path.of("target/test-shot-storage", storagePath.substring(1)));
    }

    @Test
    void localVoicePlaceholderDoesNotConsumeAiPoints() throws Exception {
        String token = registerUser("13800017009", "Voice Point Owner");
        Long tenantId = createTenant(token, "语音积分团队");
        Long ownerId = userIdByMobile("13800017009");
        Long projectId = createProject(token, tenantId, ownerId, "语音积分项目", "VOICE_NO_POINTS");
        Long serviceConfigId = createVoiceService(token, tenantId);
        Long storyboardId = createStoryboard(tenantId, projectId, ownerId);

        mockMvc.perform(post("/api/projects/%d/ai-voice-tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "storyboardId":%d,
                      "serviceConfigId":%d,
                      "voiceType":"NARRATION",
                      "voiceId":"default-cn-voice",
                      "textContent":"积分不足的旁白。",
                      "speed":1.0,
                      "pitch":1.0,
                      "volume":1.0
                    }
                    """.formatted(storyboardId, serviceConfigId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("SUCCEEDED")));

        Integer pointTransactions = jdbc.queryForObject(
            "select count(*) from team_point_transaction where tenant_id = ?",
            Integer.class,
            tenantId
        );
        assertEquals(0, pointTransactions);
    }

    @Test
    void rejectsShotComposeWhenStoryboardHasNoVideo() throws Exception {
        String token = registerUser("13800017002", "No Video Owner");
        Long tenantId = createTenant(token, "无视频团队");
        Long ownerId = userIdByMobile("13800017002");
        Long projectId = createProject(token, tenantId, ownerId, "无视频项目", "SHOT_NO_VIDEO");
        Long storyboardId = createStoryboardWithoutVideo(tenantId, projectId, ownerId);

        mockMvc.perform(post("/api/projects/%d/shot-compose-tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"storyboardId":%d,"includeSubtitle":false,"audioVolume":1.0,"outputFormat":"mp4"}
                    """.formatted(storyboardId)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorMessage", containsString("请先生成或上传分镜视频")));
    }

    @Test
    void updatesSubtitleTimelineAndExportsSrt() throws Exception {
        String token = registerUser("13800017004", "Subtitle Editor");
        Long tenantId = createTenant(token, "字幕编辑团队");
        Long ownerId = userIdByMobile("13800017004");
        Long projectId = createProject(token, tenantId, ownerId, "字幕编辑项目", "SHOT_SUBTITLE");
        Long serviceConfigId = createVoiceService(token, tenantId);
        Long storyboardId = createStoryboard(tenantId, projectId, ownerId);
        grantTeamPoints(tenantId, 1);

        MvcResult voiceResult = mockMvc.perform(post("/api/projects/%d/ai-voice-tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "storyboardId":%d,
                      "serviceConfigId":%d,
                      "voiceType":"NARRATION",
                      "voiceId":"default-cn-voice",
                      "textContent":"字幕编辑示例。",
                      "speed":1.0,
                      "pitch":1.0,
                      "volume":1.0
                    }
                    """.formatted(storyboardId, serviceConfigId)))
            .andExpect(status().isOk())
            .andReturn();
        Long voiceResultId = readLong(voiceResult, "$.data.results[0].id");

        MvcResult subtitleCreate = mockMvc.perform(post("/api/projects/%d/storyboard-subtitles".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "storyboardId":%d,
                      "voiceResultId":%d,
                      "subtitleType":"NARRATION",
                      "textContent":"字幕编辑示例。",
                      "styleConfig":{"fontSize":"MEDIUM","position":"BOTTOM"}
                    }
                    """.formatted(storyboardId, voiceResultId)))
            .andExpect(status().isOk())
            .andReturn();
        Long subtitleId = readLong(subtitleCreate, "$.data.id");

        MvcResult updated = mockMvc.perform(put("/api/projects/%d/storyboard-subtitles/%d".formatted(projectId, subtitleId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "textContent":"字幕编辑后的示例。",
                      "startTime":1.5,
                      "endTime":3.25,
                      "styleConfig":{"fontSize":"LARGE","position":"TOP"}
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.segments[0].startTime", is(1.5)))
            .andExpect(jsonPath("$.data.segments[0].endTime", is(3.25)))
            .andReturn();

        String srtUrl = JsonPath.read(updated.getResponse().getContentAsString(), "$.data.srtUrl");
        String srtContent = Files.readString(Path.of("target/test-shot-storage", stripQuery(srtUrl).substring(1)));
        assert srtContent.contains("00:00:01,500 --> 00:00:03,250");
        assert srtContent.contains("字幕编辑后的示例。");
    }

    @Test
    void supportsVoiceSubtitleAndComposeTaskManagement() throws Exception {
        String token = registerUser("13800017003", "Phase Five Manager");
        Long tenantId = createTenant(token, "五期管理团队");
        Long ownerId = userIdByMobile("13800017003");
        Long projectId = createProject(token, tenantId, ownerId, "五期管理项目", "SHOT_MANAGE");
        Long serviceConfigId = createVoiceService(token, tenantId);
        Long storyboardId = createStoryboard(tenantId, projectId, ownerId);
        grantTeamPoints(tenantId, 3);

        MvcResult voiceTask = mockMvc.perform(post("/api/projects/%d/ai-voice-tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "storyboardId":%d,
                      "serviceConfigId":%d,
                      "voiceType":"NARRATION",
                      "voiceId":"default-cn-voice",
                      "textContent":"第一句旁白。",
                      "speed":1.0,
                      "pitch":1.0,
                      "volume":1.0
                    }
                    """.formatted(storyboardId, serviceConfigId)))
            .andExpect(status().isOk())
            .andReturn();
        Long voiceTaskId = readLong(voiceTask, "$.data.id");
        Long voiceResultId = readLong(voiceTask, "$.data.results[0].id");

        Long pendingVoiceTaskId = insertPendingVoiceTask(tenantId, projectId, storyboardId, serviceConfigId, ownerId);
        mockMvc.perform(post("/api/projects/%d/ai-voice-tasks/%d/cancel".formatted(projectId, pendingVoiceTaskId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("CANCELED")));

        mockMvc.perform(post("/api/projects/%d/ai-voice-tasks/%d/regenerate".formatted(projectId, voiceTaskId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("SUCCEEDED")))
            .andExpect(jsonPath("$.data.results", hasSize(1)));

        mockMvc.perform(delete("/api/projects/%d/ai-voice-tasks/%d".formatted(projectId, pendingVoiceTaskId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk());

        MvcResult subtitleCreate = mockMvc.perform(post("/api/projects/%d/storyboard-subtitles".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "storyboardId":%d,
                      "voiceResultId":%d,
                      "subtitleType":"NARRATION",
                      "textContent":"第一句旁白。",
                      "styleConfig":{"fontSize":"MEDIUM","position":"BOTTOM"}
                    }
                    """.formatted(storyboardId, voiceResultId)))
            .andExpect(status().isOk())
            .andReturn();
        Long subtitleId = readLong(subtitleCreate, "$.data.id");

        mockMvc.perform(get("/api/projects/%d/storyboard-subtitles".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .param("storyboardId", String.valueOf(storyboardId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)));

        mockMvc.perform(get("/api/projects/%d/storyboard-subtitles/%d".formatted(projectId, subtitleId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id", is(subtitleId.intValue())));

        Long composeTaskId = insertPendingComposeTask(tenantId, projectId, storyboardId, voiceResultId, subtitleId, ownerId);
        mockMvc.perform(post("/api/projects/%d/shot-compose-tasks/%d/cancel".formatted(projectId, composeTaskId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("CANCELED")));

        MvcResult composeCreate = mockMvc.perform(post("/api/projects/%d/shot-compose-tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "storyboardId":%d,
                      "voiceResultId":%d,
                      "subtitleId":%d,
                      "includeSubtitle":true,
                      "audioVolume":1.0,
                      "outputFormat":"mp4"
                    }
            """.formatted(storyboardId, voiceResultId, subtitleId)))
            .andExpect(status().isOk())
            .andReturn();
        Long composeResultId = readLong(composeCreate, "$.data.results[0].id");

        mockMvc.perform(post("/api/projects/%d/shot-compose-tasks/%d/regenerate".formatted(projectId, composeTaskId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("SUCCEEDED")));

        mockMvc.perform(delete("/api/projects/%d/storyboard-subtitles/%d".formatted(projectId, subtitleId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/api/projects/%d/shot-compose-tasks/%d".formatted(projectId, composeTaskId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/projects/%d/ai-voice-results/%d/download".formatted(projectId, voiceResultId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id", is(voiceResultId.intValue())));

        mockMvc.perform(get("/api/projects/%d/shot-compose-results/%d/download".formatted(projectId, composeResultId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id", is(composeResultId.intValue())));
    }

    @Test
    void composesEpisodeVersionsExportsAndKeepsSingleCurrentVersion() throws Exception {
        String token = registerUser("13800017005", "Episode Composer");
        Long tenantId = createTenant(token, "六期成片团队");
        Long ownerId = userIdByMobile("13800017005");
        Long projectId = createProject(token, tenantId, ownerId, "六期成片项目", "EPISODE_COMPOSE");
        createStoryboard(tenantId, projectId, ownerId);
        createStoryboardWithSelectedShot(tenantId, projectId, ownerId, 1, 2, 9102L, "/materials/1/1/shots/mock-2.mp4", 6, 720, 1280);

        MvcResult compose = mockMvc.perform(post("/api/projects/%d/episode-compose-tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "episodeNo":1,
                      "taskName":"第1集成片合成",
                      "versionName":"第1集 成片 v1",
                      "outputFormat":"mp4",
                      "quality":"STANDARD",
                      "generateCover":true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("SUCCEEDED")))
            .andExpect(jsonPath("$.data.storyboardCount", is(2)))
            .andExpect(jsonPath("$.data.items", hasSize(2)))
            .andExpect(jsonPath("$.data.videoVersion.versionNo", is(1)))
            .andExpect(jsonPath("$.data.videoVersion.current", is(true)))
            .andExpect(jsonPath("$.data.videoVersion.videoUrl", containsString(".mp4")))
            .andReturn();
        Long taskId = readLong(compose, "$.data.id");
        Long versionId = readLong(compose, "$.data.videoVersion.id");

        mockMvc.perform(get("/api/projects/%d/episode-compose-tasks/%d".formatted(projectId, taskId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id", is(taskId.intValue())))
            .andExpect(jsonPath("$.data.items", hasSize(2)));

        mockMvc.perform(get("/api/projects/%d/episode-video-versions".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .param("episodeNo", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].current", is(true)));

        mockMvc.perform(get("/api/projects/%d/episode-video-versions/%d/download".formatted(projectId, versionId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("video/mp4")))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("episode_1_v1.mp4")));

        mockMvc.perform(get("/api/projects/%d/episode-video-versions/%d/cover".formatted(projectId, versionId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG));

        mockMvc.perform(post("/api/projects/%d/episode-video-versions/%d/save-material".formatted(projectId, versionId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.materialId", notNullValue()));

        MvcResult recompose = mockMvc.perform(post("/api/projects/%d/episode-compose-tasks/%d/regenerate".formatted(projectId, taskId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.videoVersion.versionNo", is(2)))
            .andExpect(jsonPath("$.data.videoVersion.current", is(true)))
            .andReturn();
        Long secondVersionId = readLong(recompose, "$.data.videoVersion.id");

        mockMvc.perform(post("/api/projects/%d/episode-video-versions/%d/current".formatted(projectId, versionId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.current", is(true)));

        mockMvc.perform(delete("/api/projects/%d/episode-video-versions/%d".formatted(projectId, versionId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errorMessage", containsString("当前成片版本已被单集引用")));

        Integer currentCount = jdbc.queryForObject(
            "select count(*) from episode_video_version where tenant_id = ? and project_id = ? and episode_no = 1 and is_current = true and status = 'ACTIVE'",
            Integer.class,
            tenantId,
            projectId
        );
        org.assertj.core.api.Assertions.assertThat(currentCount).isEqualTo(1);

        mockMvc.perform(delete("/api/projects/%d/episode-video-versions/%d".formatted(projectId, secondVersionId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/projects/%d/episode-export-records".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .param("episodeNo", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(2)));

        String storagePath = jdbc.queryForObject(
            "select storage_path from episode_video_version where id = ?",
            String.class,
            versionId
        );
        assert Files.exists(Path.of("target/test-shot-storage", stripQuery(storagePath).substring(1)));

        mockMvc.perform(get(storagePath))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void marksEpisodeComposeValidationFailedWhenShotVideoMissing() throws Exception {
        String token = registerUser("13800017006", "Episode Validator");
        Long tenantId = createTenant(token, "六期校验团队");
        Long ownerId = userIdByMobile("13800017006");
        Long projectId = createProject(token, tenantId, ownerId, "六期校验项目", "EPISODE_VALIDATE");
        createStoryboardWithoutVideo(tenantId, projectId, ownerId);

        mockMvc.perform(post("/api/projects/%d/episode-compose-tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"episodeNo":1,"outputFormat":"mp4","quality":"STANDARD","generateCover":true}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("VALIDATION_FAILED")))
            .andExpect(jsonPath("$.data.errorMessage", containsString("存在分镜缺少单镜头视频")))
            .andExpect(jsonPath("$.data.items[0].status", is("FAILED")));
    }

    @Test
    void marksEpisodeComposeValidationFailedWhenShotAspectRatioMismatch() throws Exception {
        String token = registerUser("13800017007", "Episode Ratio Validator");
        Long tenantId = createTenant(token, "六期比例校验团队");
        Long ownerId = userIdByMobile("13800017007");
        Long projectId = createProject(token, tenantId, ownerId, "六期比例校验项目", "EPISODE_RATIO");
        createStoryboardWithSelectedShot(tenantId, projectId, ownerId, 1, 1, 9201L, "/materials/1/1/shots/ratio-1.mp4", 5, 720, 1280);
        createStoryboardWithSelectedShot(tenantId, projectId, ownerId, 1, 2, 9202L, "/materials/1/1/shots/ratio-2.mp4", 6, 1920, 1080);

        mockMvc.perform(post("/api/projects/%d/episode-compose-tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"episodeNo":1,"outputFormat":"mp4","quality":"STANDARD","generateCover":true}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("VALIDATION_FAILED")))
            .andExpect(jsonPath("$.data.items[1].status", is("FAILED")))
            .andExpect(jsonPath("$.data.items[1].errorMessage", containsString("分镜视频比例不一致")));
    }

    @Test
    void composesEpisodeWhenShotResolutionDiffersButAspectRatioMatches() throws Exception {
        String token = registerUser("13800017008", "Episode Resolution Validator");
        Long tenantId = createTenant(token, "六期分辨率校验团队");
        Long ownerId = userIdByMobile("13800017008");
        Long projectId = createProject(token, tenantId, ownerId, "六期分辨率校验项目", "EPISODE_RESOLUTION");
        createStoryboardWithSelectedShot(tenantId, projectId, ownerId, 1, 1, 9301L, "/materials/1/1/shots/resolution-1.mp4", 5, 720, 1280);
        createStoryboardWithSelectedShot(tenantId, projectId, ownerId, 1, 2, 9302L, "/materials/1/1/shots/resolution-2.mp4", 6, 1080, 1920);

        mockMvc.perform(post("/api/projects/%d/episode-compose-tasks".formatted(projectId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .header("X-Tenant-Id", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"episodeNo":1,"outputFormat":"mp4","quality":"STANDARD","generateCover":true}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", is("SUCCEEDED")))
            .andExpect(jsonPath("$.data.items", hasSize(2)))
            .andExpect(jsonPath("$.data.videoVersion.current", is(true)));
    }

    private Long createStoryboard(Long tenantId, Long projectId, Long createdBy) {
        jdbc.update("""
            insert into storyboard
              (tenant_id, project_id, episode_no, shot_no, shot_type, visual_description,
               characters, actions, dialogue, scene, duration_seconds, image_prompt, video_prompt,
               first_frame_url, current_video_result_id, current_video_url, status, created_by, created_at, updated_at)
            values
              (?, ?, 1, 1, 'MEDIUM', '雨夜中女主站在豪宅门口', '女主',
               '缓慢推门', '你终于来了。', '豪宅门口', 5, '雨夜豪宅首帧', '镜头推近女主',
               'https://cdn.example.com/first-frame.jpg', 9001, '/materials/1/1/videos/mock.mp4', 'READY', ?, now(), now())
            """, tenantId, projectId, createdBy);
        return jdbc.queryForObject("select max(id) from storyboard where tenant_id = ? and project_id = ?", Long.class, tenantId, projectId);
    }

    private Long createStoryboardWithoutVideo(Long tenantId, Long projectId, Long createdBy) {
        jdbc.update("""
            insert into storyboard
              (tenant_id, project_id, episode_no, shot_no, shot_type, visual_description,
               characters, actions, dialogue, scene, duration_seconds, image_prompt, video_prompt,
               status, created_by, created_at, updated_at)
            values
              (?, ?, 1, 1, 'MEDIUM', '空镜', '女主',
               '等待', '旁白文本', '房间', 5, '首帧', '视频提示词',
               'READY', ?, now(), now())
            """, tenantId, projectId, createdBy);
        return jdbc.queryForObject("select max(id) from storyboard where tenant_id = ? and project_id = ?", Long.class, tenantId, projectId);
    }

    private Long createStoryboardWithSelectedShot(
        Long tenantId,
        Long projectId,
        Long createdBy,
        int episodeNo,
        int shotNo,
        Long shotResultId,
        String videoUrl,
        int durationSeconds,
        int width,
        int height
    ) {
        jdbc.update("""
            insert into storyboard
              (tenant_id, project_id, episode_no, shot_no, shot_type, visual_description,
               characters, actions, dialogue, scene, duration_seconds, image_prompt, video_prompt,
               first_frame_url, current_video_result_id, current_video_url, current_shot_result_id,
               current_shot_video_url, status, created_by, created_at, updated_at)
            values
              (?, ?, ?, ?, 'MEDIUM', '单镜头画面', '女主',
               '继续推进', '下一句对白', '宴会厅', ?, '首帧', '视频提示词',
               'https://cdn.example.com/first-frame.jpg', ?, ?, ?,
               ?, 'READY', ?, now(), now())
            """, tenantId, projectId, episodeNo, shotNo, durationSeconds, shotResultId, videoUrl, shotResultId, videoUrl, createdBy);
        Long storyboardId = jdbc.queryForObject("select max(id) from storyboard where tenant_id = ? and project_id = ?", Long.class, tenantId, projectId);
        jdbc.update("""
            insert into shot_compose_result
              (tenant_id, project_id, task_id, storyboard_id, video_url, storage_path, cover_url,
               duration_seconds, width, height, file_size, format, material_id, is_selected, status, created_at, updated_at)
            values
              (?, ?, ?, ?,
               ?, ?, ?, ?, ?, ?, 128, 'mp4', null, true, 'ACTIVE', now(), now())
            """, tenantId, projectId, shotResultId, storyboardId, videoUrl, videoUrl, "/materials/1/1/shots/cover-%d.jpg".formatted(shotNo), durationSeconds, width, height);
        Long resultId = jdbc.queryForObject(
            "select max(id) from shot_compose_result where tenant_id = ? and project_id = ?",
            Long.class,
            tenantId,
            projectId
        );
        jdbc.update(
            "update storyboard set current_shot_result_id = ?, current_shot_video_url = ?, updated_at = now() where id = ?",
            resultId,
            videoUrl,
            storyboardId
        );
        return storyboardId;
    }

    private Long createVoiceService(String token, Long tenantId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tenants/%d/ai-service-configs".formatted(tenantId))
                .with(com.antshorttv.support.SessionTestSupport.authenticated(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name":"默认语音服务",
                      "serviceType":"VOICE",
                      "provider":"MiniMax",
                      "baseUrl":"mock://voice",
                      "apiKey":"sk-test-voice",
                      "model":"speech-2.6-hd",
                      "endpoint":"/voice/synthesis",
                      "priority":100,
                      "isDefault":true,
                      "enabled":true,
                      "remark":"测试语音服务"
                    }
                    """))
            .andExpect(status().isOk())
            .andReturn();
        return readLong(result, "$.data.id");
    }

    private Long insertPendingVoiceTask(
        Long tenantId,
        Long projectId,
        Long storyboardId,
        Long serviceConfigId,
        Long createdBy
    ) {
        jdbc.update("""
            insert into ai_voice_task
              (tenant_id, project_id, storyboard_id, service_config_id, provider_code, model,
               voice_type, speaker_name, voice_id, text_content, speed, pitch, volume,
               status, started_at, created_by, created_at, updated_at)
            values
              (?, ?, ?, ?, 'MiniMax', 'speech-2.6-hd', 'NARRATION', '旁白', 'default-cn-voice',
               '待取消任务', 1.0, 1.0, 1.0, 'PENDING', now(), ?, now(), now())
            """, tenantId, projectId, storyboardId, serviceConfigId, createdBy);
        return jdbc.queryForObject(
            "select max(id) from ai_voice_task where tenant_id = ? and project_id = ?",
            Long.class,
            tenantId,
            projectId
        );
    }

    private Long insertPendingComposeTask(
        Long tenantId,
        Long projectId,
        Long storyboardId,
        Long voiceResultId,
        Long subtitleId,
        Long createdBy
    ) {
        jdbc.update("""
            insert into shot_compose_task
              (tenant_id, project_id, storyboard_id, voice_result_id, subtitle_id, compose_config,
               status, started_at, created_by, created_at, updated_at)
            values
              (?, ?, ?, ?, ?, '{"includeSubtitle":true,"audioVolume":1,"outputFormat":"mp4"}',
               'PENDING', now(), ?, now(), now())
            """, tenantId, projectId, storyboardId, voiceResultId, subtitleId, createdBy);
        return jdbc.queryForObject(
            "select max(id) from shot_compose_task where tenant_id = ? and project_id = ?",
            Long.class,
            tenantId,
            projectId
        );
    }

    private void grantTeamPoints(Long tenantId, int amount) {
        jdbc.update("""
            insert into team_point_account
              (tenant_id, balance, total_granted, total_consumed, created_at, updated_at)
            values (?, ?, ?, 0, now(), now())
            """, tenantId, amount, amount);
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
                    {"name":"%s","type":"STUDIO","description":"五期测试"}
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
                    {"name":"%s","code":"%s","description":"五期项目","ownerId":%d}
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

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String stripQuery(String value) {
        int index = value.indexOf('?');
        return index < 0 ? value : value.substring(0, index);
    }
}
