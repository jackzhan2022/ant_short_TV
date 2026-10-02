package com.antshorttv.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.antshorttv.support.SessionTestSupport;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(MediaPagingControllerTest.QueryConfiguration.class)
class MediaPagingControllerTest extends com.antshorttv.support.RegistrationTestSupport {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SqlRecorder sqlRecorder;

    @Test
    void pagesSummariesAndResultsWithStableOrderCountsAndProjectIsolation() throws Exception {
        String mobile = "13800019601";
        String token = SessionTestSupport.sessionCredential(mvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"mobile":"%s","verificationCode":"%s","nickname":"Paging","password":"Password123"}
                """.formatted(mobile, registrationVerificationCode(mvc, mobile))))
            .andExpect(status().isOk()).andReturn());
        Long tenant = id(mvc.perform(post("/api/tenants").with(SessionTestSupport.authenticated(token))
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Paging\",\"type\":\"STUDIO\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Long owner = jdbc.queryForObject("select id from app_user where mobile = ?", Long.class, mobile);
        Long project = createProject(token, tenant, owner, "PAGING_A");
        Long otherProject = createProject(token, tenant, owner, "PAGING_B");
        long imageTask = 0;
        long videoTask = 0;
        long shotTask = 0;
        long voiceTask = 0;
        long episodeTask = 0;
        for (int i = 0; i < 25; i++) {
            imageTask = insert("ai_image_task", Map.of("tenant_id", tenant, "project_id", project,
                "task_type", "CHARACTER", "target_type", "CHARACTER", "target_id", 123L,
                "provider_code", "MOCK", "model", "image", "prompt", "data:image/png;base64,secret",
                "aspect_ratio", "1:1", "image_count", 1), owner);
            videoTask = insert("ai_video_task", Map.of("tenant_id", tenant, "project_id", project,
                "storyboard_id", 123L, "provider_code", "MOCK", "model", "video", "prompt", "secret prompt",
                "first_frame_url", "data:image/png;base64,secret", "duration_seconds", 5, "aspect_ratio", "1:1"), owner);
            shotTask = insert("shot_compose_task", Map.of("tenant_id", tenant, "project_id", project,
                "storyboard_id", 123L, "compose_config", "{\"secret\":true}"), owner);
            voiceTask = insert("ai_voice_task", Map.ofEntries(Map.entry("tenant_id", tenant), Map.entry("project_id", project),
                Map.entry("storyboard_id", 123L), Map.entry("provider_code", "MOCK"), Map.entry("model", "voice"),
                Map.entry("voice_type", "DIALOGUE"), Map.entry("voice_id", "speaker"), Map.entry("text_content", "secret speech"),
                Map.entry("speed", 1), Map.entry("pitch", 1), Map.entry("volume", 1)), owner);
            episodeTask = insert("episode_compose_task", Map.of("tenant_id", tenant, "project_id", project,
                "episode_no", 1, "task_name", "Paging", "storyboard_count", 25, "compose_config", "secret config"), owner);
            jdbc.update("insert into storyboard_subtitle (tenant_id,project_id,storyboard_id,subtitle_type,content,srt_url,is_selected,status,created_by,created_at,updated_at) values (?,?,123,'DIALOGUE','secret subtitle','subtitle.srt',false,'ACTIVE',?,now(),now())", tenant, project, owner);
        }
        for (int i = 0; i < 25; i++) {
            insertResult("ai_image_result", tenant, project, imageTask, i == 0);
            insertResult("ai_video_result", tenant, project, videoTask, i == 0);
            insertResult("shot_compose_result", tenant, project, shotTask, i == 0);
            jdbc.update("insert into ai_voice_result (tenant_id,project_id,task_id,storyboard_id,audio_url,storage_path,is_selected,status,created_at,updated_at) values (?,?,?,123,'voice.mp3','voice.mp3',?,'ACTIVE',now(),now())", tenant, project, voiceTask, i == 0);
            jdbc.update("insert into episode_video_version (tenant_id,project_id,episode_no,compose_task_id,version_no,version_name,video_url,storage_path,is_current,status,created_by,created_at,updated_at) values (?,?,1,?,?,?,'episode.mp4','episode.mp4',?,'ACTIVE',?,now(),now())", tenant, project, episodeTask, i + 1, "V" + i, i == 0, owner);
            Long version = jdbc.queryForObject("select max(id) from episode_video_version", Long.class);
            jdbc.update("insert into episode_export_record (tenant_id,project_id,episode_no,video_version_id,export_type,export_status,created_by,created_at) values (?,?,1,?,'DOWNLOAD','SUCCEEDED',?,now())", tenant, project, version, owner);
            jdbc.update("insert into episode_compose_item (tenant_id,project_id,task_id,episode_no,storyboard_id,storyboard_order,status,created_at) values (?,?,?,1,123,?,'SUCCEEDED',now())", tenant, project, episodeTask, i);
        }
        for (String endpoint : List.of("ai-image-tasks", "ai-video-tasks", "shot-compose-tasks", "ai-voice-tasks")) {
            sqlRecorder.clear();
            var first = mvc.perform(get("/api/projects/" + project + "/" + endpoint)
                .with(SessionTestSupport.authenticated(token)).header("X-Tenant-Id", tenant))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(25))
                .andExpect(jsonPath("$.data.data", hasSize(20)))
                .andExpect(jsonPath("$.data.data[0].resultCount").value(25))
                .andExpect(jsonPath("$.data.data[0].results", hasSize(2)))
                .andReturn().getResponse().getContentAsString();
            assertThat(first).doesNotContain("base64", "secret prompt", "secret config", "secret speech");
            String table = endpoint.replace('-', '_').replaceAll("s$", "");
            String resultTable = table.replace("_task", "_result");
            var mediaSql = sqlRecorder.sql().stream().filter(sql -> sql.contains("from " + table) || sql.contains("from " + resultTable)).toList();
            assertThat(mediaSql).hasSize(4);
            assertThat(mediaSql).anySatisfy(sql -> assertThat(sql).contains("limit 20 offset 0", "order by created_at desc,id desc"));
            var second = mvc.perform(get("/api/projects/" + project + "/" + endpoint).param("current", "2")
                .with(SessionTestSupport.authenticated(token)).header("X-Tenant-Id", tenant))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.data", hasSize(5)))
                .andReturn().getResponse().getContentAsString();
            List<Number> firstIds = JsonPath.read(first, "$.data.data[*].id");
            List<Number> secondIds = JsonPath.read(second, "$.data.data[*].id");
            assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
            mvc.perform(get("/api/projects/" + project + "/" + endpoint).param("current", "2147483647")
                .with(SessionTestSupport.authenticated(token)).header("X-Tenant-Id", tenant))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.data", hasSize(0)));
        }
        assertResults(token, tenant, project, "ai-image-tasks", imageTask);
        assertResults(token, tenant, project, "ai-video-tasks", videoTask);
        assertResults(token, tenant, project, "shot-compose-tasks", shotTask);
        assertResults(token, tenant, project, "ai-voice-tasks", voiceTask);
        assertResults(token, tenant, project, "episode-compose-tasks", episodeTask);
        for (String endpoint : List.of("storyboard-subtitles", "episode-compose-tasks", "episode-video-versions", "episode-export-records")) {
            mvc.perform(get("/api/projects/" + project + "/" + endpoint).param("current", "2").param("episodeNo", "1")
                .with(SessionTestSupport.authenticated(token)).header("X-Tenant-Id", tenant))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(25))
                .andExpect(jsonPath("$.data.data", hasSize(5)));
        }
        mvc.perform(get("/api/projects/" + project + "/episode-compose-tasks/" + episodeTask)
            .with(SessionTestSupport.authenticated(token)).header("X-Tenant-Id", tenant))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items", hasSize(20)))
            .andExpect(jsonPath("$.data.itemCount").value(25)).andExpect(jsonPath("$.data.resultCount").value(25));
        mvc.perform(get("/api/projects/" + project + "/episode-compose-tasks/" + episodeTask + "/items").param("current", "2")
            .with(SessionTestSupport.authenticated(token)).header("X-Tenant-Id", tenant))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.data", hasSize(5)));
        mvc.perform(get("/api/projects/" + project + "/episode-video-versions/current").param("episodeNo", "1")
            .with(SessionTestSupport.authenticated(token)).header("X-Tenant-Id", tenant))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.current").value(true));
        mvc.perform(get("/api/projects/" + otherProject + "/ai-video-tasks/" + videoTask + "/results")
            .with(SessionTestSupport.authenticated(token)).header("X-Tenant-Id", tenant))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/projects/" + project + "/ai-image-tasks/" + imageTask)
            .with(SessionTestSupport.authenticated(token)).header("X-Tenant-Id", tenant))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.results", hasSize(2)));
        assertThat(jdbc.queryForObject("select count(*) from media_delivery_grant", Long.class)).isZero();
        Long videoResult = jdbc.queryForObject("select max(id) from ai_video_result where task_id = ?", Long.class, videoTask);
        jdbc.update("update ai_video_result set storage_path = ? where id = ?",
            "materials/" + tenant + "/" + project + "/videos/v1/original.mp4", videoResult);
        mvc.perform(get("/api/projects/" + project + "/ai-video-results/" + videoResult + "/playback")
            .with(SessionTestSupport.authenticated(token)).header("Range", "bytes=0-99"))
            .andExpect(status().isFound())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Location", org.hamcrest.Matchers.containsString("sign=")));
        assertThat(jdbc.queryForObject("select count(*) from media_delivery_grant", Long.class)).isEqualTo(1);
    }

    private void assertResults(String token, Long tenant, Long project, String endpoint, long task) throws Exception {
        mvc.perform(get("/api/projects/" + project + "/" + endpoint + "/" + task + "/results")
            .param("current", "2").param("pageSize", "20")
            .with(SessionTestSupport.authenticated(token)).header("X-Tenant-Id", tenant))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(25))
            .andExpect(jsonPath("$.data.data", hasSize(5)));
    }

    private Long createProject(String token, Long tenant, Long owner, String code) throws Exception {
        return id(mvc.perform(post("/api/projects").with(SessionTestSupport.authenticated(token))
            .header("X-Tenant-Id", tenant).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Paging\",\"code\":\"" + code + "\",\"ownerId\":" + owner + "}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private Long id(String json) {
        return ((Number) JsonPath.read(json, "$.data.id")).longValue();
    }

    private long insert(String table, Map<String, Object> fields, Long owner) {
        var values = new java.util.LinkedHashMap<>(fields);
        values.put("created_by", owner);
        values.put("status", "SUCCEEDED");
        String placeholders = String.join(",", java.util.Collections.nCopies(values.size(), "?"));
        jdbc.update("insert into " + table + " (" + String.join(",", values.keySet())
            + ",created_at,updated_at) values (" + placeholders + ",'2026-10-01 12:00:00','2026-10-01 12:00:00')", values.values().toArray());
        return jdbc.queryForObject("select max(id) from " + table, Long.class);
    }

    private void insertResult(String table, Long tenant, Long project, long task, boolean selected) {
        String target = table.equals("ai_image_result") ? "target_type,target_id,image_url" : "storyboard_id,video_url,storage_path";
        String targetValues = table.equals("ai_image_result") ? "'CHARACTER',123,'image'" : "123,'video.mp4','video.mp4'";
        jdbc.update("insert into " + table + " (tenant_id,project_id,task_id," + target
            + ",is_selected,status,created_at,updated_at) values (?,?,?," + targetValues + ",?,'ACTIVE',now(),now())",
            tenant, project, task, selected);
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class QueryConfiguration {
        @org.springframework.context.annotation.Bean
        SqlRecorder sqlRecorder() { return new SqlRecorder(); }
    }

    @org.apache.ibatis.plugin.Intercepts(@org.apache.ibatis.plugin.Signature(type = org.apache.ibatis.executor.Executor.class,
        method = "query", args = {org.apache.ibatis.mapping.MappedStatement.class, Object.class,
            org.apache.ibatis.session.RowBounds.class, org.apache.ibatis.session.ResultHandler.class}))
    static class SqlRecorder implements org.apache.ibatis.plugin.Interceptor {
        private final ThreadLocal<List<String>> statements = ThreadLocal.withInitial(java.util.ArrayList::new);
        @Override public Object intercept(org.apache.ibatis.plugin.Invocation invocation) throws Throwable {
            var statement = (org.apache.ibatis.mapping.MappedStatement) invocation.getArgs()[0];
            statements.get().add(statement.getBoundSql(invocation.getArgs()[1]).getSql().toLowerCase().replaceAll("\\s+", " ").trim());
            return invocation.proceed();
        }
        void clear() { statements.get().clear(); }
        List<String> sql() { return List.copyOf(statements.get()); }
    }
}
