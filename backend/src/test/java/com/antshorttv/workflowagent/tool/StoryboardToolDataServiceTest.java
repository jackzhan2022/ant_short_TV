package com.antshorttv.workflowagent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.antshorttv.script.StoryboardPromptCompiler;
import java.time.Instant;
import java.util.List;
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
    @Autowired private EpisodeSourceSegmenter segmenter;
    @Autowired private StoryboardPromptCompiler promptCompiler;

    @org.springframework.boot.test.mock.mockito.SpyBean
    private com.antshorttv.script.StoryboardAssetReferenceRepository referenceRepository;

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
    void freshStoryboardsDoNotRetireReferencesForNewIds() throws Exception {
        JsonNode saved = service.saveEpisodeStoryboards(context(), validPayload());
        assertThat(saved.path("storyboardCount").asInt()).isGreaterThan(0);
        org.mockito.Mockito.verify(referenceRepository, org.mockito.Mockito.never()).replace(
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyList(),
            org.mockito.ArgumentMatchers.any());
    }

    @Test
    void v2SavesShortSingleShotWithoutPaddingButLegacyRejectsIt() throws Exception {
        JsonNode payload = validPayload();
        ArrayNode shots = (ArrayNode) payload.path("storyboards").get(0).path("shots");
        while (shots.size() > 1) shots.remove(shots.size() - 1);
        ((ObjectNode) shots.get(0)).put("durationSeconds", 5.5);
        assertThatThrownBy(() -> service.saveEpisodeStoryboards(context(), payload))
            .isInstanceOf(com.antshorttv.common.BusinessException.class);
        jdbc.update("update project set storyboard_skill_version='V2' where id=?", projectId);
        assertThat(service.saveEpisodeStoryboards(context(), payload).path("saved").asBoolean()).isTrue();
        String plan = jdbc.queryForObject("select shot_plan_json from storyboard where episode_id=? and deleted_at is null",
            String.class, episodeId);
        JsonNode saved = json.readTree(plan);
        assertThat(saved.path("shots").size()).isEqualTo(1);
        assertThat(saved.path("shots").get(0).path("durationSeconds").decimalValue()).isEqualByComparingTo("5.5");
        assertThat(saved.path("shots").get(0).path("dialogue").asText()).contains("Serena: No...");
    }

    @Test
    void v2StillRejectsClipsBelowFourSecondsAndAboveFifteenSeconds() throws Exception {
        jdbc.update("update project set storyboard_skill_version='V2' where id=?", projectId);
        JsonNode payload = validPayload();
        ArrayNode shots = (ArrayNode) payload.path("storyboards").get(0).path("shots");
        for (JsonNode shot : shots) ((ObjectNode) shot).put("durationSeconds", 4.5);
        assertThatThrownBy(() -> service.saveEpisodeStoryboards(context(), payload)).hasMessageContaining("4 至 15");
        while (shots.size() > 1) shots.remove(shots.size() - 1);
        ((ObjectNode) shots.get(0)).put("durationSeconds", 3);
        assertThatThrownBy(() -> service.saveEpisodeStoryboards(context(), payload)).hasMessageContaining("4 至 15");
    }

    @Test
    void v3RejectsSpeechThatCannotFitItsAssignedShotWithoutReplacingSavedBoards() throws Exception {
        String spoken = "郑怡（咄咄逼人）：\"第二，做人要有点边界感！不能主动联系我，晚上八点后更不能给承泽打电话！他现在是我老公，不是你们随叫随到的跑腿！\"";
        jdbc.update("update project set storyboard_skill_version='V3' where id=?", projectId);
        jdbc.update("update script_episode set content=? where id=?", spoken, episodeId);
        ToolExecutionContext trusted = context();
        trusted.runState().put("currentEpisodeContent", spoken);
        trusted.runState().put("currentEpisodeSourceSegments", segmenter.segment(spoken));
        ObjectNode payload = (ObjectNode) validPayload();
        ObjectNode board = (ObjectNode) payload.path("storyboards").get(0);
        board.put("sourceTo", "S0001");
        ArrayNode shots = (ArrayNode) board.path("shots");
        while (shots.size() > 2) shots.remove(shots.size() - 1);
        ((ObjectNode) shots.get(0)).put("sourceAnchor", "S0001").put("durationSeconds", 11);
        ((ObjectNode) shots.get(1)).put("sourceAnchor", "S0001").put("durationSeconds", 4);
        assertThatThrownBy(() -> service.saveEpisodeStoryboards(trusted, payload))
            .hasMessageContaining("台词时长不足");
        assertThat(jdbc.queryForObject("select visual_description from storyboard where episode_id=? and deleted_at is null",
            String.class, episodeId)).isEqualTo("old storyboard");
    }

    @Test
    void v3SavesOneFifteenSecondShotWithOriginalDialogue() throws Exception {
        jdbc.update("update project set storyboard_skill_version='V3',visual_style=null where id=?", projectId);
        JsonNode payload = validPayload();
        ArrayNode shots = (ArrayNode) payload.path("storyboards").get(0).path("shots");
        while (shots.size() > 1) shots.remove(shots.size() - 1);
        ((ObjectNode) shots.get(0)).put("durationSeconds", 15);
        assertThat(service.saveEpisodeStoryboards(context(), payload).path("saved").asBoolean()).isTrue();
        var row = jdbc.queryForMap("select duration_seconds,shot_plan_json from storyboard where episode_id=? and deleted_at is null", episodeId);
        JsonNode saved = json.readTree(row.get("shot_plan_json").toString());
        assertThat(row.get("duration_seconds")).isEqualTo(15);
        assertThat(saved.path("shots").size()).isEqualTo(1);
        assertThat(saved.path("shots").get(0).path("durationSeconds").asInt()).isEqualTo(15);
        assertThat(saved.path("shots").get(0).path("dialogue").asText()).contains("Serena: No...");
    }

    @Test
    void v3SavesLongSpeechAcrossGroupsWithExactWordsAndSpeaker() throws Exception {
        String utterance = "“第二，做人要有点边界感！不能主动联系我，晚上八点后更不能给承泽打电话！他现在是我老公，不是你们随叫随到的跑腿！”";
        String spoken = "郑怡（咄咄逼人）：" + utterance;
        var segments = segmenter.segmentStoryboardV3(spoken,
            new EpisodeSourceSegmenter.SegmentationContext(Set.of("郑怡"), Set.of()));
        assertThat(segments.size()).isGreaterThan(1);
        jdbc.update("update project set storyboard_skill_version='V3' where id=?", projectId);
        jdbc.update("update script_episode set content=? where id=?", spoken, episodeId);
        ToolExecutionContext trusted = context();
        trusted.runState().put("currentEpisodeContent", spoken);
        trusted.runState().put("currentEpisodeSourceSegments", segments);
        ObjectNode payload = (ObjectNode) validPayload();
        ObjectNode template = ((ObjectNode) payload.path("storyboards").get(0)).deepCopy();
        ArrayNode boards = payload.putArray("storyboards");
        for (int index = 0; index < segments.size(); index++) {
            ObjectNode board = template.deepCopy();
            board.put("storyboardNo", index + 1).put("sourceTo", segments.get(index).id());
            ObjectNode shot = ((ObjectNode) template.path("shots").get(0)).deepCopy();
            shot.put("positioning", "郑怡站在前景").put("action", "郑怡保持原说话姿态");
            ArrayNode shots = board.putArray("shots");
            if (index > 0) shots.add(shot.deepCopy().put("durationSeconds", 2).put("shotPurpose", "CONTINUITY_START"));
            shots.add(shot.deepCopy().put("durationSeconds", 15 - (index > 0 ? 2 : 0) - (index < segments.size() - 1 ? 2 : 0))
                .put("sourceAnchor", segments.get(index).id()));
            if (index < segments.size() - 1) shots.add(shot.deepCopy().put("durationSeconds", 2).put("shotPurpose", "CONTINUITY_END"));
            boards.add(board);
        }
        assertThat(service.saveEpisodeStoryboards(trusted, payload).path("saved").asBoolean()).isTrue();
        var rows = jdbc.queryForList("select duration_seconds,video_prompt,shot_plan_json from storyboard where episode_id=? and deleted_at is null order by storyboard_no", episodeId);
        assertThat(rows).hasSize(segments.size());
        StringBuilder restored = new StringBuilder();
        for (int index = 0; index < rows.size(); index++) {
            var row = rows.get(index);
            var speech = segmenter.speechText(segments, index);
            assertThat(row.get("duration_seconds")).isEqualTo(15);
            assertThat(row.get("video_prompt").toString()).contains(
                "郑怡（咄咄逼人）的台词配音是：「" + speech.utterance() + "」");
            JsonNode plan = json.readTree(row.get("shot_plan_json").toString());
            String assigned = java.util.stream.StreamSupport.stream(plan.path("shots").spliterator(), false)
                .map(shot -> shot.path("dialogue").asText()).filter(value -> !value.isEmpty())
                .collect(java.util.stream.Collectors.joining());
            assertThat(assigned).isEqualTo(segments.get(index).text());
            restored.append(speech.utterance());
        }
        assertThat(restored.toString()).isEqualTo(utterance);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"CHARACTER", "PROP"})
    void v3PreservesDistinctVariantsOfOneAssetInTheSameGroup(String assetType) throws Exception {
        jdbc.update("update project set storyboard_skill_version='V3' where id=?", projectId);
        Long assetId = "CHARACTER".equals(assetType)
            ? insertCharacter("Serena", "serena") : insertProp("手机", "phone");
        Long firstVariant = insertVariant(assetType, assetId);
        jdbc.update("""
            insert into asset_visual_variant
              (tenant_id,project_id,asset_type,asset_id,name,source_type,generation_status,
               current_image_url,is_primary,created_by,created_at,updated_at)
            values (?,?,?,?,'回放形态','MANUAL','COMPLETED','/replay.png',false,?,now(),now())
            """, tenantId, projectId, assetType, assetId, userId);
        Long secondVariant = jdbc.queryForObject(
            "select id from asset_visual_variant where tenant_id=? and project_id=? and asset_type=? and asset_id=? and name='回放形态'",
            Long.class, tenantId, projectId, assetType, assetId);
        ObjectNode payload = (ObjectNode) validPayload();
        ObjectNode board = (ObjectNode) payload.path("storyboards").get(0);
        ArrayNode shots = (ArrayNode) board.path("shots");
        while (shots.size() > 1) shots.remove(shots.size() - 1);
        ((ObjectNode) shots.get(0)).put("durationSeconds", 15);
        ArrayNode references = (ArrayNode) board.path("usedAssetKeys")
            .path("CHARACTER".equals(assetType) ? "characters" : "props");
        String key = ("CHARACTER".equals(assetType) ? "c_" : "p_") + assetId;
        references.addObject().put("assetKey", key).put("variantKey", "v_" + firstVariant)
            .put("role", "VISIBLE").put("sourceName", "现场资产");
        references.addObject().put("assetKey", key).put("variantKey", "v_" + secondVariant)
            .put("role", "VISIBLE").put("sourceName", "回放资产");

        assertThat(service.saveEpisodeStoryboards(context(), payload).path("saved").asBoolean()).isTrue();
        Long storyboardId = currentStoryboardId();
        assertThat(jdbc.queryForList("""
            select variant_id from storyboard_asset_reference
             where storyboard_id=? and retired_at is null order by sort_order
            """, Long.class, storyboardId)).containsExactly(firstVariant, secondVariant);
        JsonNode document = json.readTree(jdbc.queryForObject(
            "select prompt_document_json from storyboard where id=?", String.class, storyboardId));
        assertThat(java.util.stream.StreamSupport.stream(document.path("nodes").spliterator(), false)
            .filter(node -> "mention".equals(node.path("type").asText()))
            .map(node -> node.path("sourceId").asLong()).toList())
            .containsExactly(firstVariant, secondVariant);
        var compiled = promptCompiler.compile(document, List.of(
            new StoryboardPromptCompiler.BindingReference(assetType, assetId, firstVariant, "现场资产", "VISIBLE", 0),
            new StoryboardPromptCompiler.BindingReference(assetType, assetId, secondVariant, "回放资产", "VISIBLE", 1)));
        assertThat(compiled.references().stream().map(reference -> reference.sourceId()).toList())
            .containsExactly(firstVariant, secondVariant);
        assertThat(compiled.text()).contains("现场资产为", "回放资产为");

        for (int lockedCount : new int[] {1, 2}) {
            jdbc.update("""
                update storyboard_asset_reference set locked_by_user=true,source_type='MANUAL'
                 where storyboard_id=? and retired_at is null and sort_order<?
                """, storyboardId, lockedCount);
            assertThat(service.saveEpisodeStoryboards(context(), payload).path("saved").asBoolean()).isTrue();
            storyboardId = currentStoryboardId();
            assertThat(jdbc.queryForList("""
                select variant_id from storyboard_asset_reference
                 where storyboard_id=? and retired_at is null order by sort_order
                """, Long.class, storyboardId)).containsExactly(firstVariant, secondVariant);
            assertThat(jdbc.queryForObject("""
                select count(*) from storyboard_asset_reference
                 where storyboard_id=? and retired_at is null and locked_by_user=true
                """, Integer.class, storyboardId)).isEqualTo(lockedCount);
        }

        references.remove(1);
        references.addObject().put("assetKey", key).put("role", "VISIBLE");
        assertThatThrownBy(() -> service.saveEpisodeStoryboards(context(), payload))
            .isInstanceOfSatisfying(WorkflowToolValidationException.class, failure ->
                assertThat(failure.details().get("validationCode")).isEqualTo("ASSET_REFERENCE_DUPLICATE"));
        assertThat(currentStoryboardId()).isEqualTo(storyboardId);
    }

    @Test
    void legacySkillStillRejectsRepeatedAssetKeysWithDifferentVariantKeys() throws Exception {
        Long assetId = insertCharacter("Serena", "serena");
        Long variant = insertVariant("CHARACTER", assetId);
        ObjectNode payload = (ObjectNode) validPayload();
        ArrayNode references = (ArrayNode) payload.path("storyboards").get(0).path("usedAssetKeys").path("characters");
        references.addObject().put("assetKey", "c_" + assetId).put("variantKey", "v_" + variant).put("role", "VISIBLE");
        references.addObject().put("assetKey", "c_" + assetId).put("variantKey", "v_999999999").put("role", "VISIBLE");

        assertThatThrownBy(() -> service.saveEpisodeStoryboards(context(), payload))
            .isInstanceOfSatisfying(WorkflowToolValidationException.class, failure ->
                assertThat(failure.details().get("validationCode")).isEqualTo("ASSET_REFERENCE_DUPLICATE"));
        assertPriorStoryboardUnchanged();
    }

    @Test
    void v3RejectsFourteenAndSixteenSecondTotalsWithoutReplacingExistingBoards() throws Exception {
        jdbc.update("update project set storyboard_skill_version='V3' where id=?", projectId);
        for (int total : new int[] {14, 16}) {
            JsonNode payload = validPayload();
            ArrayNode shots = (ArrayNode) payload.path("storyboards").get(0).path("shots");
            while (shots.size() > 2) shots.remove(shots.size() - 1);
            for (JsonNode shot : shots) ((ObjectNode) shot).put("durationSeconds", total / 2);
            assertThatThrownBy(() -> service.saveEpisodeStoryboards(context(), payload))
                .hasMessageContaining("必须为 15 秒");
        }
        assertThat(jdbc.queryForObject("select visual_description from storyboard where episode_id=? and deleted_at is null",
            String.class, episodeId)).isEqualTo("old storyboard");
    }

    @Test
    void v3DoesNotSilentlyClampAnInvalidSingleShotIntoAValidFifteenSecondClip() throws Exception {
        jdbc.update("update project set storyboard_skill_version='V3' where id=?", projectId);
        JsonNode payload = validPayload();
        ArrayNode shots = (ArrayNode) payload.path("storyboards").get(0).path("shots");
        while (shots.size() > 1) shots.remove(shots.size() - 1);
        ((ObjectNode) shots.get(0)).put("durationSeconds", 16);
        assertThatThrownBy(() -> service.saveEpisodeStoryboards(context(), payload))
            .hasMessageContaining("内部镜头时长必须大于 0 且不超过 15 秒");
    }

    @Test
    void v3SupportsPositiveSubsecondShotsButRejectsZeroDuration() throws Exception {
        jdbc.update("update project set storyboard_skill_version='V3' where id=?", projectId);
        JsonNode payload = validPayload();
        ArrayNode shots = (ArrayNode) payload.path("storyboards").get(0).path("shots");
        while (shots.size() > 2) shots.remove(shots.size() - 1);
        ((ObjectNode) shots.get(0)).put("durationSeconds", 0);
        ((ObjectNode) shots.get(1)).put("durationSeconds", 15);
        assertThatThrownBy(() -> service.saveEpisodeStoryboards(context(), payload))
            .hasMessageContaining("大于 0");
        ((ObjectNode) shots.get(0)).put("durationSeconds", 0.5);
        ((ObjectNode) shots.get(1)).put("durationSeconds", 14.5);
        assertThat(service.saveEpisodeStoryboards(context(), payload).path("saved").asBoolean()).isTrue();
        JsonNode plan = json.readTree(jdbc.queryForObject(
            "select shot_plan_json from storyboard where episode_id=? and deleted_at is null", String.class, episodeId));
        assertThat(plan.path("shots").get(0).path("durationSeconds").decimalValue()).isEqualByComparingTo("0.5");
        assertThat(plan.path("shots").get(1).path("durationSeconds").decimalValue()).isEqualByComparingTo("14.5");
    }

    @Test
    void v3SavesTheOriginalBenchmarkExampleAcrossThreeFifteenSecondGroups() throws Exception {
        JsonNode fixture;
        try (var stream = getClass().getResourceAsStream("/storyboard-v3-benchmark.json")) {
            fixture = json.readTree(stream);
        }
        ObjectNode payload = (ObjectNode) fixture.path("payload");
        payload.put("episodeFingerprint", "fp-1");
        var assetKeys = java.util.Map.of(
            "c_1", "c_" + insertCharacter("张正阳", "张正阳"),
            "c_2", "c_" + insertCharacter("杨梓", "杨梓"),
            "s_1", "s_" + insertScene("废弃仓库", "废弃仓库"),
            "p_1", "p_" + insertProp("手机", "手机"),
            "p_2", "p_" + insertProp("手机卡", "手机卡"));
        for (JsonNode board : payload.path("storyboards")) {
            for (JsonNode group : board.path("usedAssetKeys")) {
                for (JsonNode reference : group) {
                    ((ObjectNode) reference).put("assetKey", assetKeys.get(reference.path("assetKey").asText()));
                }
            }
        }
        new WorkflowToolSchemaValidator().validate(
            new ScreenplayToolConfiguration().saveEpisodeStoryboardsTool(null, json).inputSchema(), payload);
        String benchmarkSource = fixture.path("source").asText();
        ToolExecutionContext trusted = context();
        trusted.runState().put("currentEpisodeContent", benchmarkSource);
        trusted.runState().put("currentEpisodeSourceSegments", segmenter.segment(benchmarkSource));
        jdbc.update("update project set storyboard_skill_version='V3' where id=?", projectId);
        jdbc.update("update script_episode set content=? where id=?", benchmarkSource, episodeId);
        assertThat(service.saveEpisodeStoryboards(trusted, payload).path("saved").asBoolean()).isTrue();
        var rows = jdbc.queryForList("select duration_seconds,video_prompt,shot_plan_json from storyboard where episode_id=? and deleted_at is null order by storyboard_no", episodeId);
        assertThat(rows).hasSize(3);
        String all = rows.stream().map(row -> row.get("video_prompt").toString())
            .collect(java.util.stream.Collectors.joining("\n"));
        assertThat(all).containsOnlyOnce("张正阳的台词配音是：「你敢耍我？！」")
            .containsOnlyOnce("张正阳的台词配音是：「我会让你付出代价...」")
            .containsOnlyOnce("杨梓的台词配音是：「怎么？这就发火了？」")
            .contains("掌心血液沿指缝滑落", "第一声脚步", "第二声脚步", "第三声脚步",
                "将手机卡放入上衣的口袋", "皮鞋踩碎玻璃")
            .doesNotContain("[站位]", "[动作]", "[表演]", "[声音]");
        JsonNode previousEnd = null;
        for (var row : rows) {
            assertThat(row.get("duration_seconds")).isEqualTo(15);
            JsonNode plan = json.readTree(row.get("shot_plan_json").toString());
            assertThat(row.get("video_prompt").toString()).contains("完整画面描述：" + plan.path("visualSummary").asText());
            JsonNode shots = plan.path("shots");
            if (previousEnd != null) {
                for (String field : List.of("positioning", "action", "performance", "camera", "cameraMovement", "soundEffects")) {
                    assertThat(shots.get(0).path(field)).isEqualTo(previousEnd.path(field));
                }
            }
            for (JsonNode shot : shots) {
                if (!"NARRATIVE".equals(shot.path("shotPurpose").asText())) {
                    assertThat(shot.path("soundSegmentIds").isEmpty()).isTrue();
                    assertThat(shot.path("dialogue").asText()).isEmpty();
                }
            }
            previousEnd = shots.get(shots.size() - 1);
        }
        assertThat(rows.get(2).get("video_prompt").toString()).contains("【00:12至00:15】", "极慢动作", "定格")
            .doesNotContain("321卡");
    }

    @Test
    void v3RendersOriginalContinuousTemplateWithSummaryAndSilentMarker() throws Exception {
        jdbc.update("update project set storyboard_skill_version='V3' where id=?", projectId);
        JsonNode payload = validPayload();
        ObjectNode board = (ObjectNode) payload.path("storyboards").get(0);
        board.put("visualSummary", "展示Serena的愤怒爆发。");
        ArrayNode shots = (ArrayNode) board.path("shots");
        while (shots.size() > 2) shots.remove(shots.size() - 1);
        ObjectNode first = (ObjectNode) shots.get(0);
        first.put("durationSeconds", 12).put("sourceAnchor", "S0003")
            .put("shotTitle", "Serena摔手机中景")
            .put("positioning", "Serena位于中景层画面正中面向镜头，")
            .put("action", "猛地将手机摔碎在地上，")
            .put("performance", "咬紧下颌线，脖颈青筋暴起")
            .put("camera", "摄影机：ARRI ALEXA 65；焦段：50mm；光圈：T2")
            .put("cameraMovement", "中景+仰视+纵向快速推镜")
            .put("soundEffects", "玻璃碎裂声+急促呼吸声");
        ((ObjectNode) shots.get(1)).put("durationSeconds", 3).put("sourceAnchor", "S0003")
            .put("shotTitle", "最后三秒定格").put("action", "定格在原文结束时的姿态。");
        service.saveEpisodeStoryboards(context(), payload);
        var row = jdbc.queryForMap("select video_prompt,shot_plan_json from storyboard where episode_id=? and deleted_at is null", episodeId);
        String prompt = row.get("video_prompt").toString();
        assertThat(prompt).contains("完整画面描述：展示Serena的愤怒爆发。",
            "【00:00至00:12】⚠️摄影机：ARRI ALEXA 65；焦段：50mm；光圈：T2，Serena摔手机中景",
            "Serena位于中景层画面正中面向镜头，猛地将手机摔碎在地上，[微表情：咬紧下颌线，脖颈青筋暴起]，中景+仰视+纵向快速推镜，玻璃碎裂声+急促呼吸声，Serena的台词配音是：「No...」",
            "【配音：无】，台词：「无」负面提示词：（无背景音乐，无字幕 ）。")
            .doesNotContain("[站位]", "[动作]", "[表演]", "[情绪]", "[声音]");
        assertThat(prompt.indexOf("完整画面描述：")).isLessThan(prompt.indexOf("【00:00至00:12】"));
        JsonNode saved = json.readTree(row.get("shot_plan_json").toString());
        assertThat(saved.path("visualSummary").asText()).isEqualTo("展示Serena的愤怒爆发。");
        assertThat(saved.path("shots").get(0).path("soundEffects").asText()).isEqualTo("玻璃碎裂声+急促呼吸声");
        assertThat(saved.path("shots").get(1).path("dialogue").asText()).isEmpty();
    }

    @Test
    void v3RendersBenchmarkStyleTimedBlocksAndProductionCuesWithTrustedSounds() throws Exception {
        jdbc.update("update project set storyboard_skill_version='V3' where id=?", projectId);
        JsonNode payload = validPayload();
        long character = insertCharacter("Serena", "serena");
        long variant = insertVariant("CHARACTER", character);
        ((ArrayNode) payload.path("storyboards").get(0).path("usedAssetKeys").path("characters"))
            .addObject().put("assetKey", "c_" + character).put("variantKey", "v_" + variant).put("role", "VISIBLE");
        ArrayNode boards = (ArrayNode) payload.path("storyboards");
        ObjectNode first = (ObjectNode) boards.get(0);
        ObjectNode second = first.deepCopy();
        first.put("sourceTo", "S0001");
        ArrayNode firstShots = (ArrayNode) first.path("shots");
        while (firstShots.size() > 2) firstShots.remove(firstShots.size() - 1);
        ((ObjectNode) firstShots.get(0)).put("durationSeconds", 13).put("sourceAnchor", "S0001");
        ((ObjectNode) firstShots.get(1)).put("durationSeconds", 2).put("shotPurpose", "CONTINUITY_END");
        second.put("sourceTo", "S0003");
        boards.add(second);
        ArrayNode shots = (ArrayNode) second.path("shots");
        while (shots.size() > 2) shots.remove(shots.size() - 1);
        ((ObjectNode) shots.get(0)).put("durationSeconds", 2).put("shotPurpose", "CONTINUITY_START");
        ((ObjectNode) shots.get(1)).put("durationSeconds", 13).put("sourceAnchor", "S0003");
        for (JsonNode shot : shots) ((ObjectNode) shot).put("camera", "摄影机：ARRI ALEXA 65；焦段：50mm；光圈：T4；固定镜头");
        ((ObjectNode) shots.get(0)).remove("camera");
        String faithfulSource = "开场\nSerena: Serena, meet at 18:30; bring $20.\n结束";
        ToolExecutionContext trusted = context();
        trusted.runState().put("currentEpisodeContent", faithfulSource);
        trusted.runState().put("currentEpisodeSourceSegments", segmenter.segment(faithfulSource));
        jdbc.update("update script_episode set content=? where id=?", faithfulSource, episodeId);
        service.saveEpisodeStoryboards(trusted, payload);
        var row = jdbc.queryForMap("select video_prompt,shot_plan_json,prompt_document_json from storyboard where episode_id=? and storyboard_no=2 and deleted_at is null", episodeId);
        String prompt = row.get("video_prompt").toString();
        assertThat(prompt).contains("【镜02】 时间范围：00:00至00:15", "🎬 本镜资产锁定", "延长视频生成以下内容",
            "dark thriller atmosphere", "Kodak Vision3 500T", "【00:00至00:02】", "【00:02至00:15】",
            "Serena的台词配音是：「Serena, meet at 18:30; bring $20.」", "【配音：321开始】，台词：「321开始」", "负面提示词：（无背景音乐，无字幕 ）。")
            .contains("音色资产", "远景+俯视+固定镜头")
            .doesNotContain("321卡", "画风: 现代都市通用", StoryboardToolDataService.FIXED_MEDIA_CONSTRAINT);
        assertThat(jdbc.queryForObject("select video_prompt from storyboard where episode_id=? and storyboard_no=1 and deleted_at is null", String.class, episodeId))
            .contains("【00:13至00:15】", "【配音：321卡】，台词：「321卡」").doesNotContain("321开始");
        assertThat(prompt.indexOf("摄影机：ARRI ALEXA 65")).isLessThan(prompt.indexOf("Serena的台词配音是："));
        var savedShots = json.readTree(row.get("shot_plan_json").toString()).path("shots");
        assertThat(savedShots.get(0).path("dialogue").asText()).isEmpty();
        assertThat(savedShots.get(1).path("dialogue").asText()).contains("Serena: Serena, meet at 18:30; bring $20.");
        assertThat(json.readTree(row.get("prompt_document_json").toString()).path("version").asInt()).isEqualTo(2);
        var compiled = promptCompiler.compile(json.readTree(row.get("prompt_document_json").toString()),
            List.of(new StoryboardPromptCompiler.BindingReference("CHARACTER", character, variant, "Serena", "VISIBLE", 0)));
        assertThat(compiled.text()).contains("Serena为图片1", "Serena的台词配音是：「Serena, meet at 18:30; bring $20.」")
            .doesNotContain("台词：「Serena:");
        assertThat(compiled.references()).hasSize(1);
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
            .contains("镜头4 3.8s", "[声音] 对白：Serena: No...", StoryboardToolDataService.FIXED_CONSISTENCY_CONSTRAINT);
        assertThat(row.get("prompt_document_json").toString()).contains("\"version\":2", "\"nodes\"");
        assertThat(row.get("shot_plan_json").toString()).contains("\"durationSeconds\":3.8");
        assertThat(row.get("shot_plan_json").toString())
            .contains("\"dialogue\":\"Serena: No...\"")
            .contains("\"soundSegmentIds\":[\"S0002\"]");
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
            .contains("\"mediaType\":\"IMAGE\"")
            .contains("\"sourceType\":\"ASSET_VISUAL_VARIANT\"")
            .contains("\"sourceId\":" + episodeVariantId)
            .contains("\"assetType\":\"CHARACTER\"")
            .contains("\"assetId\":" + assetId)
            .contains("\"variantId\":" + episodeVariantId)
            .contains("\"displayName\":\"Serena\"");
        assertThat(jdbc.queryForObject("""
            select count(*) from storyboard_asset_reference reference
              join storyboard board on board.id=reference.storyboard_id
             where board.episode_id=? and board.deleted_at is null and reference.retired_at is null
               and reference.asset_type='CHARACTER' and reference.asset_id=?
               and reference.variant_id=? and reference.source_type='AI'
            """, Integer.class, episodeId, assetId, episodeVariantId)).isEqualTo(1);
    }

    @Test
    void regenerationCarriesLockedReferencesAcrossTheSameTrustedSourceRange() throws Exception {
        Long lockedAssetId = insertCharacter("Serena locked", "serena locked");
        Long aiAssetId = insertCharacter("Rowan", "rowan");
        Long oldStoryboardId = currentStoryboardId();
        jdbc.update("update storyboard set shot_plan_json=? where id=?",
            "{\"sourceFrom\":\"S0001\",\"sourceTo\":\"S0003\"}", oldStoryboardId);
        insertLockedReference(oldStoryboardId, lockedAssetId, 0);
        JsonNode payload = validPayload();
        ((ArrayNode) payload.path("storyboards").get(0).path("usedAssetKeys").path("characters"))
            .addObject().put("assetKey", "c_" + aiAssetId).put("role", "VISIBLE");

        service.saveEpisodeStoryboards(context(false), payload);

        var active = jdbc.queryForList("""
            select reference.asset_id,reference.source_type,reference.locked_by_user
              from storyboard_asset_reference reference
              join storyboard board on board.id=reference.storyboard_id
             where board.episode_id=? and board.deleted_at is null and reference.retired_at is null
             order by reference.sort_order
            """, episodeId);
        assertThat(active).anySatisfy(row -> {
            assertThat(((Number) row.get("asset_id")).longValue()).isEqualTo(lockedAssetId);
            assertThat(row.get("source_type")).isEqualTo("MANUAL");
            assertThat(row.get("locked_by_user")).isEqualTo(true);
        });
        assertThat(jdbc.queryForObject(
            "select count(*) from storyboard_asset_reference where storyboard_id=? and retired_at is null",
            Integer.class, oldStoryboardId)).isZero();
    }

    @Test
    void explicitMaterialOverwriteReplacesLockedReferences() throws Exception {
        Long lockedAssetId = insertCharacter("Serena locked", "serena locked");
        Long aiAssetId = insertCharacter("Rowan", "rowan");
        Long oldStoryboardId = currentStoryboardId();
        jdbc.update("update storyboard set shot_plan_json=? where id=?",
            "{\"sourceFrom\":\"S0001\",\"sourceTo\":\"S0003\"}", oldStoryboardId);
        insertLockedReference(oldStoryboardId, lockedAssetId, 0);
        JsonNode payload = validPayload();
        ((ArrayNode) payload.path("storyboards").get(0).path("usedAssetKeys").path("characters"))
            .addObject().put("assetKey", "c_" + aiAssetId).put("role", "VISIBLE");

        service.saveEpisodeStoryboards(context(true), payload);

        assertThat(jdbc.queryForList("""
            select reference.asset_id from storyboard_asset_reference reference
              join storyboard board on board.id=reference.storyboard_id
             where board.episode_id=? and board.deleted_at is null and reference.retired_at is null
            """, Long.class, episodeId)).containsExactly(aiAssetId);
    }

    @Test
    void unmatchedLockedReferenceProducesWarningWithoutUnsafeAttachment() throws Exception {
        Long lockedAssetId = insertCharacter("Serena locked", "serena locked");
        Long oldStoryboardId = currentStoryboardId();
        jdbc.update("update storyboard set shot_plan_json=? where id=?",
            "{\"sourceFrom\":\"S0098\",\"sourceTo\":\"S0099\"}", oldStoryboardId);
        insertLockedReference(oldStoryboardId, lockedAssetId, 0);

        service.saveEpisodeStoryboards(context(false), validPayload());

        JsonNode plan = json.readTree(jdbc.queryForObject(
            "select shot_plan_json from storyboard where episode_id=? and deleted_at is null",
            String.class, episodeId));
        assertThat(plan.path("diagnostics").path("lockCarryWarnings").toString())
            .contains("MANUAL_LOCK_NOT_CARRIED");
        assertThat(jdbc.queryForObject("""
            select count(*) from storyboard_asset_reference reference
              join storyboard board on board.id=reference.storyboard_id
             where board.episode_id=? and board.deleted_at is null and reference.retired_at is null
            """, Integer.class, episodeId)).isZero();
    }

    @Test
    void stalePayloadPreservesPriorStoryboardSet() throws Exception {
        Long lockedAssetId = insertCharacter("Stale lock", "stale lock");
        Long storyboardId = currentStoryboardId();
        insertLockedReference(storyboardId, lockedAssetId, 0);
        jdbc.update("update script_episode set content_fingerprint = 'changed' where id = ?", episodeId);
        assertThatThrownBy(() -> service.saveEpisodeStoryboards(context(), validPayload()))
            .isInstanceOf(com.antshorttv.common.BusinessException.class)
            .hasMessageContaining("已变化");
        assertPriorStoryboardUnchanged();
        assertThat(jdbc.queryForObject(
            "select count(*) from storyboard_asset_reference where storyboard_id=? and retired_at is null",
            Integer.class, storyboardId)).isEqualTo(1);
    }

    @Test
    void stableAssetKeySelectsOneOfDuplicateDisplayNamesWithoutAmbiguity() throws Exception {
        jdbc.update("""
            insert into character_asset
              (tenant_id, project_id, script_id, name, normalized_name, role_type, status, source,
               content_json, created_by, created_at, updated_at)
            values
              (?, ?, ?, 'Serena Aldwych', 'serena aldwych', 'LEAD', 'CONFIRMED', 'MANUAL',
               '{"aliases":["Serena"]}', ?, now(), now()),
              (?, ?, ?, 'Serena Vale', 'serena vale', 'SUPPORTING', 'CONFIRMED', 'MANUAL',
               '{"aliases":["Serena"]}', ?, now(), now())
            """, tenantId, projectId, scriptId, userId,
            tenantId, projectId, scriptId, userId);
        Long selectedId = jdbc.queryForObject(
            "select min(id) from character_asset where tenant_id = ? and project_id = ?",
            Long.class, tenantId, projectId);
        JsonNode payload = validPayload();
        ObjectNode reference = ((ArrayNode) payload.path("storyboards").get(0)
            .path("usedAssetKeys").path("characters")).addObject();
        reference.put("assetKey", "c_" + selectedId)
            .put("role", "VISIBLE")
            .put("sourceName", "Serena");

        JsonNode saved = service.saveEpisodeStoryboards(context(), payload);

        assertThat(saved.path("saved").asBoolean()).isTrue();
        JsonNode plan = json.readTree(jdbc.queryForObject(
            "select shot_plan_json from storyboard where episode_id = ? and deleted_at is null",
            String.class, episodeId));
        assertThat(plan.path("usedAssetKeys").path("characters").get(0).path("assetKey").asText())
            .isEqualTo("c_" + selectedId);
        assertThat(plan.path("usedAssetKeys").path("characters").get(0).path("role").asText())
            .isEqualTo("VISIBLE");
    }

    @Test
    void project41AmbiguousSerenaAliasIsPreservedAsUnresolvedInsteadOfFailingTheEpisode() throws Exception {
        jdbc.update("""
            insert into character_asset
              (tenant_id, project_id, script_id, name, normalized_name, role_type, status, source,
               content_json, created_by, created_at, updated_at)
            values
              (?, ?, ?, 'Serena Aldwych', 'serena aldwych', 'LEAD', 'CONFIRMED', 'MANUAL',
               '{"aliases":["Serena"]}', ?, now(), now()),
              (?, ?, ?, 'Serena Vale', 'serena vale', 'SUPPORTING', 'CONFIRMED', 'MANUAL',
               '{"aliases":["Serena"]}', ?, now(), now())
            """, tenantId, projectId, scriptId, userId,
            tenantId, projectId, scriptId, userId);
        JsonNode payload = validPayload();
        ((ArrayNode) payload.path("storyboards").get(0)
            .path("unmatchedMaterials").path("characters")).add("Serena");

        JsonNode saved = service.saveEpisodeStoryboards(context(), payload);

        assertThat(saved.path("saved").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject(
            "select material_binding_status from storyboard where episode_id = ? and deleted_at is null",
            String.class, episodeId)).isEqualTo("ASSET_PENDING");
    }

    @Test
    void invalidStructuredVariantReportsItsExactJsonPath() throws Exception {
        jdbc.update("""
            insert into character_asset
              (tenant_id, project_id, script_id, name, normalized_name, role_type, status, source,
               content_json, created_by, created_at, updated_at)
            values
              (?, ?, ?, 'Serena', 'serena', 'LEAD', 'CONFIRMED', 'MANUAL', '{}', ?, now(), now()),
              (?, ?, ?, 'Rowan', 'rowan', 'SUPPORTING', 'CONFIRMED', 'MANUAL', '{}', ?, now(), now())
            """, tenantId, projectId, scriptId, userId,
            tenantId, projectId, scriptId, userId);
        var ids = jdbc.queryForList(
            "select id from character_asset where tenant_id = ? and project_id = ? order by id",
            Long.class, tenantId, projectId);
        jdbc.update("""
            insert into asset_visual_variant
              (tenant_id, project_id, asset_type, asset_id, name, source_type, generation_status,
               is_primary, created_by, created_at, updated_at)
            values (?, ?, 'CHARACTER', ?, 'Rowan look', 'MANUAL', 'COMPLETED', true, ?, now(), now())
            """, tenantId, projectId, ids.get(1), userId);
        Long wrongVariantId = jdbc.queryForObject(
            "select id from asset_visual_variant where asset_id = ?", Long.class, ids.get(1));
        JsonNode payload = validPayload();
        ObjectNode reference = ((ArrayNode) payload.path("storyboards").get(0)
            .path("usedAssetKeys").path("characters")).addObject();
        reference.put("assetKey", "c_" + ids.get(0))
            .put("variantKey", "v_" + wrongVariantId)
            .put("role", "VISIBLE");

        assertThatThrownBy(() -> service.saveEpisodeStoryboards(context(), payload))
            .isInstanceOfSatisfying(WorkflowToolValidationException.class, failure -> {
                assertThat(failure.details().get("validationCode")).isEqualTo("VARIANT_OWNERSHIP_INVALID");
                assertThat(failure.details().get("jsonPath"))
                    .isEqualTo("$.storyboards[0].usedAssetKeys.characters[0].variantKey");
            });
        assertPriorStoryboardUnchanged();
    }

    @Test
    void rejectsLegacyV2WithoutReplacingExistingStoryboards() throws Exception {
        ObjectNode legacy = (ObjectNode) validPayload();
        legacy.put("schemaVersion", 2);
        assertThatThrownBy(() -> service.saveEpisodeStoryboards(context(), legacy))
            .isInstanceOf(com.antshorttv.common.BusinessException.class)
            .hasMessageContaining("Schema");
        assertPriorStoryboardUnchanged();
    }

    @Test
    void reportsStoryboardNumberForCorrectableDurationFailure() throws Exception {
        JsonNode payload = validPayload();
        for (JsonNode shot : payload.path("storyboards").get(0).path("shots")) {
            ((ObjectNode) shot).put("durationSeconds", 2);
        }

        assertThatThrownBy(() -> service.saveEpisodeStoryboards(context(), payload))
            .isInstanceOfSatisfying(WorkflowToolValidationException.class, failure -> {
                assertThat(failure.details().get("validationCode"))
                    .isEqualTo("STORYBOARD_DURATION_OUT_OF_RANGE");
                assertThat(failure.details().get("storyboardNo")).isEqualTo(1);
            });
        assertPriorStoryboardUnchanged();
    }

    @Test
    void reportsBlankShotContentAsCorrectableValidation() throws Exception {
        JsonNode payload = validPayload();
        ((ObjectNode) payload.path("storyboards").get(0).path("shots").get(0))
            .put("positioning", "   ");

        assertThatThrownBy(() -> service.saveEpisodeStoryboards(context(), payload))
            .isInstanceOf(WorkflowToolValidationException.class)
            .hasMessageContaining("positioning");
        assertPriorStoryboardUnchanged();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void automaticRunCannotOverwriteManualWorkCreatedAfterDispatch(boolean unlinkedManual) throws Exception {
        if (unlinkedManual) jdbc.update("update storyboard set episode_id=null where episode_id=?", episodeId);
        jdbc.update("""
            insert into ai_execution_task
              (id, tenant_id, user_id, project_id, scene, capability, business_type,
               status, phase, client_idempotency_key, trace_id, created_at, updated_at)
            values (?, ?, ?, ?, 'storyboard_breakdown', 'TEXT', 'SCRIPT_AI_OPERATION',
                    'RUNNING', 'SUBMIT', 'auto-race', 'auto-storyboard-event-race', now(), now())
            """, projectId, tenantId, userId, projectId);
        jdbc.update("""
            insert into storyboard_generation_admission
              (tenant_id, project_id, episode_id, source_fingerprint, origin, execution_id, created_at, updated_at)
            values (?, ?, ?, 'fp-1', 'AUTO', ?, now(), now())
            """, tenantId, projectId, episodeId, projectId);
        ToolExecutionContext original = context();
        ToolExecutionContext automatic = new ToolExecutionContext(tenantId,userId,projectId,episodeId,
            scriptId,500L,null,700L,projectId,900L,1,original.permissions(),original.deadline(),original.runState());
        assertThatThrownBy(() -> service.saveEpisodeStoryboards(automatic, validPayload()))
            .isInstanceOf(com.antshorttv.common.BusinessException.class).hasMessageContaining("已有分镜");
        assertThat(jdbc.queryForObject("select count(*) from storyboard where project_id=? and visual_description='old storyboard' and deleted_at is null",Integer.class,projectId)).isEqualTo(1);
    }

    @Test
    void normalizesAdjacentRangesAndRejectsUnknownOrReversedEnds() throws Exception {
        JsonNode twoBoards = twoBoardPayload();
        assertThat(service.saveEpisodeStoryboards(context(), twoBoards).path("storyboardCount").asInt())
            .isEqualTo(2);

        jdbc.update("update storyboard set deleted_at = now() where episode_id = ? and generated_by_run_id = 700", episodeId);
        jdbc.update("update storyboard set deleted_at = null where episode_id = ? and generated_by_run_id is null", episodeId);
        JsonNode unknown = twoBoardPayload();
        ((ObjectNode) unknown.path("storyboards").get(0)).put("sourceTo", "S9999");
        assertValidation(unknown, 0, "SOURCE_SEGMENT_UNKNOWN");
        JsonNode reversed = twoBoardPayload();
        ((ObjectNode) reversed.path("storyboards").get(1)).put("sourceTo", "S0001");
        assertValidation(reversed, 1, "SOURCE_SEGMENT_REVERSED");
    }

    @Test
    void derivesSoundsInsteadOfUsingModelSuppliedSoundSegments() throws Exception {
        JsonNode payload = validPayload();
        ((ObjectNode) payload.path("storyboards").get(0).path("shots").get(2))
            .putArray("soundSegmentIds").add("S9999");

        assertThat(service.saveEpisodeStoryboards(context(), payload).path("derivedSoundCount").asInt())
            .isEqualTo(1);
    }

    @Test
    void savesV3WithCanonicalNumbersRangesAndBackendDerivedSounds() throws Exception {
        ObjectNode payload = (ObjectNode) validPayload();
        ObjectNode board = (ObjectNode) payload.path("storyboards").get(0);
        board.put("storyboardNo", 12);
        for (int index = 0; index < board.path("shots").size(); index++) {
            ObjectNode shot = (ObjectNode) board.path("shots").get(index);
            shot.put("shotNo", 20 + index);
        }
        ((ObjectNode) board.path("shots").get(0)).put("sourceAnchor", "S0001");
        ((ObjectNode) board.path("shots").get(1)).put("sourceAnchor", "S0002")
            .put("performance", "肩膀微微收紧")
            .put("emotion", "克制的恐惧")
            .put("camera", "缓慢推近至特写");

        JsonNode result = service.saveEpisodeStoryboards(context(), payload);
        JsonNode plan = json.readTree(jdbc.queryForObject(
            "select shot_plan_json from storyboard where episode_id = ? and deleted_at is null",
            String.class, episodeId));

        assertThat(result.path("normalizationCount").asInt()).isGreaterThan(0);
        assertThat(result.path("derivedSoundCount").asInt()).isEqualTo(1);
        assertThat(plan.path("diagnostics").path("normalizationCount").asInt()).isGreaterThan(0);
        assertThat(plan.path("diagnostics").path("derivedSoundCount").asInt()).isEqualTo(1);
        assertThat(plan.path("diagnostics").path("normalizationFindings").isArray()).isTrue();
        assertThat(plan.path("diagnostics").path("normalizationFindings").toString())
            .contains("SHOT_NUMBER_NORMALIZED", "REPAIRABLE", "$.storyboards[0].shots[0].shotNo");
        assertThat(plan.path("diagnostics").path("classificationWarnings").isArray()).isTrue();
        assertThat(plan.path("storyboardNo").asInt()).isEqualTo(1);
        assertThat(plan.path("sourceFrom").asText()).isEqualTo("S0001");
        assertThat(plan.path("shots").get(0).path("shotNo").asInt()).isEqualTo(1);
        assertThat(plan.path("shots").get(1).path("soundSegmentIds").toString())
            .isEqualTo("[\"S0002\"]");
        assertThat(plan.path("shots").get(1).path("dialogue").asText())
            .isEqualTo("Serena: No...");
        assertThat(jdbc.queryForObject(
            "select video_prompt from storyboard where episode_id = ? and deleted_at is null",
            String.class, episodeId)).contains(
                "[表演] 肩膀微微收紧", "[情绪] 克制的恐惧", "[运镜] 缓慢推近至特写");

        service.annotateRunDiagnostics(tenantId, projectId, episodeId, 700L, 1, 2);
        JsonNode annotated = json.readTree(jdbc.queryForObject(
            "select shot_plan_json from storyboard where episode_id = ? and deleted_at is null",
            String.class, episodeId));
        assertThat(annotated.path("diagnostics").path("businessCallCount").asInt()).isEqualTo(1);
        assertThat(annotated.path("diagnostics").path("technicalRetryCount").asInt()).isEqualTo(2);
    }

    @Test
    void exposesFallbackCorrectionAndMaterialResolutionDiagnostics() throws Exception {
        ObjectNode payload = (ObjectNode) validPayload();
        payload.put("_serverFallback", true);
        payload.put("_fallbackItemCount", 1);
        payload.put("_correctedItemCount", 1);
        ((ArrayNode) payload.path("storyboards").get(0)
            .path("unmatchedMaterials").path("characters")).add("Serena");

        JsonNode result = service.saveEpisodeStoryboards(context(), payload);
        JsonNode plan = json.readTree(jdbc.queryForObject(
            "select shot_plan_json from storyboard where episode_id = ? and deleted_at is null",
            String.class, episodeId));

        assertThat(result.path("fallbackItemCount").asInt()).isEqualTo(1);
        assertThat(result.path("correctedItemCount").asInt()).isEqualTo(1);
        assertThat(result.path("unresolvedAssetCount").asInt()).isEqualTo(1);
        assertThat(result.path("assetPendingCount").asInt()).isZero();
        assertThat(plan.path("diagnostics").path("fallbackItemCount").asInt()).isEqualTo(1);
        assertThat(plan.path("diagnostics").path("unresolvedAssetCount").asInt()).isEqualTo(1);
    }

    @Test
    void publishesMultipleIndependentBindingsAndCompilesDeduplicatedVideoReferences() throws Exception {
        List<Long> characterIds = List.of(
            insertCharacter("Serena", "serena"), insertCharacter("Rowan", "rowan"));
        List<Long> sceneIds = List.of(insertScene("走廊", "走廊"), insertScene("卧室", "卧室"));
        List<Long> propIds = List.of(insertProp("手机", "手机"), insertProp("钥匙", "钥匙"));
        List<Long> variantIds = new java.util.ArrayList<>();
        characterIds.forEach(id -> variantIds.add(insertVariant("CHARACTER", id)));
        sceneIds.forEach(id -> variantIds.add(insertVariant("SCENE", id)));
        propIds.forEach(id -> variantIds.add(insertVariant("PROP", id)));
        ObjectNode payload = (ObjectNode) validPayload();
        ObjectNode used = (ObjectNode) payload.path("storyboards").get(0).path("usedAssetKeys");
        addReferences((ArrayNode) used.path("characters"), "c_", characterIds,
            variantIds.subList(0, 2), "VISIBLE");
        addReferences((ArrayNode) used.path("scenes"), "s_", sceneIds,
            variantIds.subList(2, 4), "MAIN");
        addReferences((ArrayNode) used.path("props"), "p_", propIds,
            variantIds.subList(4, 6), "VISIBLE");

        service.saveEpisodeStoryboards(context(), payload);

        Long storyboardId = currentStoryboardId();
        var rows = jdbc.queryForList("""
            select asset_type,asset_id,variant_id,reference_role,sort_order,source_name
              from storyboard_asset_reference
             where storyboard_id=? and retired_at is null order by asset_type,sort_order
            """, storyboardId);
        assertThat(rows).hasSize(6);
        assertThat(rows.stream().map(row -> row.get("variant_id")).distinct()).hasSize(6);
        var bindings = rows.stream().map(row -> new StoryboardPromptCompiler.BindingReference(
            String.valueOf(row.get("asset_type")), ((Number) row.get("asset_id")).longValue(),
            ((Number) row.get("variant_id")).longValue(), String.valueOf(row.get("source_name")),
            String.valueOf(row.get("reference_role")), ((Number) row.get("sort_order")).intValue()
        )).toList();
        JsonNode document = json.readTree(jdbc.queryForObject(
            "select prompt_document_json from storyboard where id=?", String.class, storyboardId));
        StoryboardPromptCompiler.CompiledPrompt compiled = promptCompiler.compile(document, bindings);
        assertThat(compiled.references()).hasSize(6);
        assertThat(compiled.references().stream().map(reference -> reference.sourceId()).distinct())
            .hasSize(6);
    }

    @Test
    void repeatedAnchorFailureFallbackStillPublishesACompleteWarnedEpisode() throws Exception {
        ObjectNode invalid = (ObjectNode) validPayload();
        ((ObjectNode) invalid.path("storyboards").get(0)).put("sourceTo", "S9999");
        var failure = new WorkflowToolValidationException("bad anchor", java.util.Map.of(
            "validationCode", "SOURCE_SEGMENT_UNKNOWN", "severity", "REPAIRABLE"));
        ObjectNode fallback = new StoryboardFallbackCompleter(json).complete(context(), invalid, failure);

        JsonNode saved = service.saveEpisodeStoryboards(context(), fallback);

        assertThat(saved.path("saved").asBoolean()).isTrue();
        assertThat(saved.path("fallbackItemCount").asInt()).isEqualTo(1);
        JsonNode plan = json.readTree(jdbc.queryForObject(
            "select shot_plan_json from storyboard where episode_id=? and deleted_at is null",
            String.class, episodeId));
        assertThat(plan.path("sourceFrom").asText()).isEqualTo("S0001");
        assertThat(plan.path("sourceTo").asText()).isEqualTo("S0003");
        assertThat(plan.path("diagnostics").path("fallbackItemCount").asInt()).isEqualTo(1);
    }

    @Test
    void savesSequenceWordsAsWarningsWithoutRejectingQuotedThen() throws Exception {
        ObjectNode payload = (ObjectNode) validPayload();
        ObjectNode board = (ObjectNode) payload.path("storyboards").get(0);
        ((ObjectNode) board.path("shots").get(0)).put("action", "她转身，然后走向门口");
        ((ObjectNode) board.path("shots").get(1)).put("action", "她说：\"Then we leave.\"");

        JsonNode result = service.saveEpisodeStoryboards(context(), payload);

        assertThat(result.path("actionWarnings")).hasSize(1);
        assertThat(result.path("actionWarnings").get(0).path("code").asText())
            .isEqualTo("ACTION_DENSITY");
        assertThat(result.path("actionWarnings").get(0).path("storyboardNo").asInt()).isEqualTo(1);
        assertThat(result.path("actionWarnings").get(0).path("shotNo").asInt()).isEqualTo(1);
    }

    private ToolExecutionContext context() {
        return context(false);
    }

    private ToolExecutionContext context(boolean materialOverwrite) {
        WorkflowToolRunState state = new WorkflowToolRunState();
        state.put("currentEpisodeId", episodeId);
        state.put("currentEpisodeScriptId", scriptId);
        state.put("currentEpisodeFingerprint", "fp-1");
        state.put("currentEpisodeContent", source);
        state.put("currentEpisodeSourceSegments", segmenter.segment(source));
        state.put("materialOverwrite", materialOverwrite);
        return new ToolExecutionContext(tenantId, userId, projectId, episodeId, scriptId,
            500L, null, 700L, 800L, 900L, 1, Set.of("SCRIPT:VIEW", "SCRIPT:EDIT"),
            Instant.now().plusSeconds(30), state);
    }

    private JsonNode validPayload() throws Exception {
        return json.readTree("""
            {
              "schemaVersion":3,
              "episodeFingerprint":"fp-1",
              "storyboards":[{
                "storyboardNo":1,
                "sourceTo":"S0003",
                "time":"夜",
                "lighting":"暖黄色侧光",
                "usedAssetKeys":{"characters":[],"scenes":[],"props":[]},
                "unmatchedMaterials":{"characters":[],"scenes":[],"props":[]},
                "shots":[
                  {"shotNo":1,"durationSeconds":3,"positioning":"空镜","action":"固定镜头拍摄开场"},
                  {"shotNo":2,"durationSeconds":3,"positioning":"Serena站立","action":"镜头缓缓拉近"},
                  {"shotNo":3,"durationSeconds":3,"positioning":"Serena站立","action":"Serena神情转为坚定"},
                  {"shotNo":4,"durationSeconds":3.8,"positioning":"走廊尽头","action":"镜头拉远至结束"}
                ]
              }]
            }
            """);
    }

    private Long insertCharacter(String name, String normalizedName) {
        jdbc.update("""
            insert into character_asset
              (tenant_id, project_id, script_id, name, normalized_name, role_type, status, source,
               content_json, created_by, created_at, updated_at)
            values (?, ?, ?, ?, ?, 'SUPPORTING', 'CONFIRMED', 'MANUAL', '{}', ?, now(), now())
            """, tenantId, projectId, scriptId, name, normalizedName, userId);
        return jdbc.queryForObject(
            "select id from character_asset where tenant_id=? and project_id=? and normalized_name=?",
            Long.class, tenantId, projectId, normalizedName);
    }

    private Long insertScene(String name, String normalizedName) {
        jdbc.update("""
            insert into scene_asset
              (tenant_id,project_id,script_id,name,normalized_name,scene_type,status,source,
               created_by,created_at,updated_at)
            values (?,?,?,?,?,'INTERIOR','CONFIRMED','MANUAL',?,now(),now())
            """, tenantId, projectId, scriptId, name, normalizedName, userId);
        return jdbc.queryForObject(
            "select id from scene_asset where tenant_id=? and project_id=? and normalized_name=?",
            Long.class, tenantId, projectId, normalizedName);
    }

    private Long insertProp(String name, String normalizedName) {
        jdbc.update("""
            insert into prop_asset
              (tenant_id,project_id,script_id,name,normalized_name,prop_type,status,source,
               created_by,created_at,updated_at)
            values (?,?,?,?,?,'KEY_PROP','CONFIRMED','MANUAL',?,now(),now())
            """, tenantId, projectId, scriptId, name, normalizedName, userId);
        return jdbc.queryForObject(
            "select id from prop_asset where tenant_id=? and project_id=? and normalized_name=?",
            Long.class, tenantId, projectId, normalizedName);
    }

    private Long insertVariant(String assetType, Long assetId) {
        jdbc.update("""
            insert into asset_visual_variant
              (tenant_id,project_id,asset_type,asset_id,name,source_type,generation_status,
               current_image_url,is_primary,created_by,created_at,updated_at)
            values (?,?,?,?,?,'MANUAL','COMPLETED',?,true,?,now(),now())
            """, tenantId, projectId, assetType, assetId,
            assetType + "-" + assetId, "/variant-" + assetType + "-" + assetId + ".png", userId);
        return jdbc.queryForObject(
            "select id from asset_visual_variant where tenant_id=? and project_id=? and asset_type=? and asset_id=?",
            Long.class, tenantId, projectId, assetType, assetId);
    }

    private void addReferences(
        ArrayNode target,
        String prefix,
        List<Long> assetIds,
        List<Long> variantIds,
        String role
    ) {
        for (int index = 0; index < assetIds.size(); index++) {
            target.addObject().put("assetKey", prefix + assetIds.get(index))
                .put("variantKey", "v_" + variantIds.get(index))
                .put("role", role);
        }
    }

    private Long currentStoryboardId() {
        return jdbc.queryForObject(
            "select id from storyboard where episode_id=? and deleted_at is null",
            Long.class, episodeId);
    }

    private void insertLockedReference(Long storyboardId, Long assetId, int sortOrder) {
        jdbc.update("""
            insert into storyboard_asset_reference
              (tenant_id,project_id,storyboard_id,asset_type,asset_id,variant_id,reference_role,
               sort_order,resolution_status,source_type,source_name,locked_by_user,created_by,
               created_at,updated_at)
            values (?,?,?,'CHARACTER',?,null,'VISIBLE',?,'ASSET_PENDING','MANUAL','locked',true,?,now(),now())
            """, tenantId, projectId, storyboardId, assetId, sortOrder, userId);
    }

    private JsonNode twoBoardPayload() throws Exception {
        ObjectNode payload = (ObjectNode) validPayload();
        ArrayNode boards = (ArrayNode) payload.path("storyboards");
        ObjectNode first = (ObjectNode) boards.get(0);
        first.put("sourceTo", "S0002");
        ObjectNode second = first.deepCopy();
        second.put("storyboardNo", 2).put("sourceTo", "S0003");
        boards.add(second);
        return payload;
    }

    private void assertValidation(JsonNode payload, int boardIndex, String expectedCode) {
        assertThatThrownBy(() -> service.saveEpisodeStoryboards(context(), payload))
            .isInstanceOfSatisfying(WorkflowToolValidationException.class, failure -> {
                assertThat(failure.details().get("validationCode")).isEqualTo(expectedCode);
                assertThat(failure.details().get("storyboardNo")).isEqualTo(boardIndex + 1);
            });
        assertPriorStoryboardUnchanged();
    }

    private void assertPriorStoryboardUnchanged() {
        assertThat(jdbc.queryForObject("""
            select count(*) from storyboard
             where episode_id = ? and deleted_at is null and visual_description = 'old storyboard'
            """, Integer.class, episodeId)).isEqualTo(1);
    }
}
