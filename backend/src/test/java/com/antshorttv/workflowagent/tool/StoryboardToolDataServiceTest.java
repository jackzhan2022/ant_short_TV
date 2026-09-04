package com.antshorttv.workflowagent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class StoryboardToolDataServiceTest {
    @Autowired private StoryboardToolDataService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;

    private long tenantId;
    private long projectId;
    private long scriptId;
    private long episodeId;
    private long userId;
    private final String source = "开场\nSerena: No...\n结束";

    @BeforeEach
    void setUp() {
        tenantId = Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000) + 50_000;
        projectId = tenantId + 1;
        userId = tenantId + 2;
        jdbc.update("""
            insert into project
              (id, tenant_id, name, code, owner_id, status, visual_style, created_by, created_at, updated_at)
            values (?, ?, 'Storyboard Project', ?, ?, 'ACTIVE', '现代都市通用', ?, now(), now())
            """, projectId, tenantId, "SB_" + tenantId, userId, userId);
        jdbc.update("""
            insert into script
              (tenant_id, project_id, title, source_type, content, status, created_by, created_at, updated_at)
            values (?, ?, 'Storyboard Script', 'MANUAL_EDIT', ?, 'DRAFT', ?, now(), now())
            """, tenantId, projectId, source, userId);
        scriptId = jdbc.queryForObject("select id from script where project_id = ?", Long.class, projectId);
        jdbc.update("""
            insert into script_episode
              (tenant_id, project_id, script_id, stable_key, episode_no, title, content,
               content_fingerprint, reconciliation_status, status, created_at, updated_at)
            values (?, ?, ?, 'episode-1', 1, 'Episode 1', ?, 'fp-1', 'MATCHED', 'ACTIVE', now(), now())
            """, tenantId, projectId, scriptId, source);
        episodeId = jdbc.queryForObject(
            "select id from script_episode where project_id = ?", Long.class, projectId);
        jdbc.update("""
            insert into storyboard
              (tenant_id, project_id, script_id, episode_id, episode_no, shot_no, storyboard_no,
               visual_description, status, created_by, created_at, updated_at)
            values (?, ?, ?, ?, 1, 1, 1, 'old storyboard', 'CONFIRMED', ?, now(), now())
            """, tenantId, projectId, scriptId, episodeId, userId);
    }

    @Test
    void atomicallyReplacesEpisodeAndRendersConfirmedPromptWithRoundedCompatibilityDuration() throws Exception {
        JsonNode saved = service.saveEpisodeStoryboards(context(), validPayload());

        assertThat(saved.path("saved").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("""
            select count(*) from storyboard where episode_id = ? and deleted_at is null
            """, Integer.class, episodeId)).isEqualTo(1);
        var row = jdbc.queryForMap("""
            select duration_seconds, video_prompt, prompt_document_json, shot_plan_json,
                   material_binding_status, generated_by_run_id
              from storyboard where episode_id = ? and deleted_at is null
            """, episodeId);
        assertThat(row.get("duration_seconds")).isEqualTo(13);
        assertThat(row.get("video_prompt").toString())
            .startsWith("画风: 现代都市通用")
            .contains(StoryboardToolDataService.FIXED_MEDIA_CONSTRAINT)
            .contains("镜头4 3.8s", "Serena: No...", StoryboardToolDataService.FIXED_CONSISTENCY_CONSTRAINT);
        assertThat(row.get("prompt_document_json").toString()).contains("\"version\":1", "\"nodes\"");
        assertThat(row.get("shot_plan_json").toString()).contains("\"durationSeconds\":3.8");
        assertThat(row.get("material_binding_status")).isEqualTo("BOUND");
        assertThat(row.get("generated_by_run_id")).isEqualTo(700L);
        assertThat(jdbc.queryForObject("""
            select count(*) from storyboard
             where episode_id = ? and deleted_at is not null and visual_description = 'old storyboard'
            """, Integer.class, episodeId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
            select count(*) from storyboard
             where episode_id = ? and deleted_at is null and first_frame_url is null
            """, Integer.class, episodeId)).isEqualTo(1);
    }

    @Test
    void usesEpisodeBoundVariantAndRendersMaterialMentions() throws Exception {
        jdbc.update("""
            insert into character_asset
              (tenant_id, project_id, script_id, name, normalized_name, role_type, status, source,
               content_json, created_by, created_at, updated_at)
            values (?, ?, ?, 'Serena', 'serena', 'LEAD', 'CONFIRMED', 'MANUAL',
                    '{"aliases":["Serena Aldwych"]}', ?, now(), now())
            """, tenantId, projectId, scriptId, userId);
        Long assetId = jdbc.queryForObject(
            "select id from character_asset where tenant_id = ? and project_id = ?", Long.class,
            tenantId, projectId);
        jdbc.update("""
            insert into asset_visual_variant
              (tenant_id, project_id, asset_type, asset_id, name, source_type, generation_status,
               is_primary, created_by, created_at, updated_at)
            values
              (?, ?, 'CHARACTER', ?, '项目主造型', 'MANUAL', 'COMPLETED', true, ?, now(), now()),
              (?, ?, 'CHARACTER', ?, '本集造型', 'MANUAL', 'COMPLETED', false, ?, now(), now())
            """, tenantId, projectId, assetId, userId,
            tenantId, projectId, assetId, userId);
        Long episodeVariantId = jdbc.queryForObject(
            "select id from asset_visual_variant where asset_id = ? and name = '本集造型'", Long.class,
            assetId);
        jdbc.update("""
            insert into asset_visual_variant_episode
              (tenant_id, project_id, script_id, episode_id, asset_type, asset_id, variant_id,
               is_preferred, binding_status, created_by, created_at, updated_at)
            values (?, ?, ?, ?, 'CHARACTER', ?, ?, true, 'ACTIVE', ?, now(), now())
            """, tenantId, projectId, scriptId, episodeId, assetId, episodeVariantId, userId);

        JsonNode payload = validPayload();
        ((ArrayNode) payload.path("storyboards").get(0).path("usedAssetKeys").path("characters"))
            .add("c_" + assetId);
        service.saveEpisodeStoryboards(context(), payload);

        String prompt = jdbc.queryForObject(
            "select video_prompt from storyboard where episode_id = ? and deleted_at is null",
            String.class, episodeId);
        String document = jdbc.queryForObject(
            "select prompt_document_json from storyboard where episode_id = ? and deleted_at is null",
            String.class, episodeId);
        assertThat(prompt).contains("【人物】", "<Serena>对应Serena");
        assertThat(document)
            .contains("\"type\":\"mention\"")
            .contains("\"assetType\":\"CHARACTER\"")
            .contains("\"assetId\":" + assetId)
            .contains("\"variantId\":" + episodeVariantId)
            .contains("\"displayName\":\"Serena\"");
    }

    @Test
    void invalidOrStalePayloadPreservesPriorStoryboardSet() throws Exception {
        JsonNode invalid = validPayload();
        invalid.path("storyboards").get(0).path("shots").get(1)
            .deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) invalid.path("storyboards").get(0)
            .path("shots").get(1)).put("dialogue", "Serena: translated");

        assertThatThrownBy(() -> service.saveEpisodeStoryboards(context(), invalid))
            .isInstanceOf(com.antshorttv.common.BusinessException.class)
            .hasMessageContaining("逐字保留");
        assertThat(jdbc.queryForObject("""
            select visual_description from storyboard where episode_id = ? and deleted_at is null
            """, String.class, episodeId)).isEqualTo("old storyboard");

        jdbc.update("update script_episode set content_fingerprint = 'changed' where id = ?", episodeId);
        assertThatThrownBy(() -> service.saveEpisodeStoryboards(context(), validPayload()))
            .isInstanceOf(com.antshorttv.common.BusinessException.class)
            .hasMessageContaining("已变化");
    }

    private ToolExecutionContext context() {
        WorkflowToolRunState state = new WorkflowToolRunState();
        state.put("currentEpisodeId", episodeId);
        state.put("currentEpisodeScriptId", scriptId);
        state.put("currentEpisodeFingerprint", "fp-1");
        state.put("currentEpisodeContent", source);
        return new ToolExecutionContext(tenantId, userId, projectId, episodeId, scriptId,
            500L, null, 700L, 800L, 900L, 1, Set.of("SCRIPT:VIEW", "SCRIPT:EDIT"),
            Instant.now().plusSeconds(30), state);
    }

    private JsonNode validPayload() throws Exception {
        return json.readTree("""
            {
              "schemaVersion":1,
              "episodeFingerprint":"fp-1",
              "storyboards":[{
                "storyboardNo":1,
                "sourceStartMarker":"开场",
                "sourceEndMarker":"结束",
                "time":"夜",
                "lighting":"暖黄色侧光",
                "usedAssetKeys":{"characters":[],"scenes":[],"props":[]},
                "unmatchedMaterials":{"characters":[],"scenes":[],"props":[]},
                "shots":[
                  {"shotNo":1,"durationSeconds":3,"positioning":"空镜","action":"固定镜头拍摄开场"},
                  {"shotNo":2,"durationSeconds":3,"positioning":"Serena站立","action":"镜头缓缓拉近","dialogue":"Serena: No..."},
                  {"shotNo":3,"durationSeconds":3,"positioning":"Serena站立","action":"Serena神情转为坚定"},
                  {"shotNo":4,"durationSeconds":3.8,"positioning":"走廊尽头","action":"镜头拉远至结束"}
                ]
              }]
            }
            """);
    }
}
