package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.antshorttv.common.BusinessException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class ScriptElementDraftAndConfirmationServiceTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ScriptElementDraftService draftService;

    @Autowired
    private ScriptElementConfirmationService confirmationService;

    @Test
    void replacesOnlyUnconfirmedDraftsForRequestedElementTypeAndKeepsConfirmedRows() {
        Long tenantId = 6201L;
        Long projectId = 6301L;
        Long userId = 6401L;
        Long confirmedId = insertCharacter(tenantId, projectId, "林晚", "CONFIRMED", null);
        Long oldDraftId = insertCharacter(tenantId, projectId, "旧草稿角色", "DRAFT", null);
        Long sceneDraftId = insertScene(tenantId, projectId, "旧草稿场景", "DRAFT", null);

        draftService.replaceDrafts(
            tenantId,
            projectId,
            userId,
            new ScriptElementExtractionResult(
                ScriptElementType.CHARACTER,
                List.of(character("新角色")),
                List.of(),
                List.of()
            )
        );

        assertThat(activeCharacter(confirmedId).get("status")).isEqualTo("CONFIRMED");
        assertThat(deletedAt("character_asset", oldDraftId)).isNotNull();
        assertThat(deletedAt("scene_asset", sceneDraftId)).isNull();
        List<Map<String, Object>> activeCharacters = jdbc.queryForList("""
            select name, status, merge_target_id
              from character_asset
             where tenant_id = ? and project_id = ? and deleted_at is null
             order by id
            """, tenantId, projectId);
        assertThat(activeCharacters).extracting(row -> row.get("name"))
            .containsExactly("林晚", "新角色");
        assertThat(activeCharacters.get(1).get("status")).isEqualTo("DRAFT");
        assertThat(activeCharacters.get(1).get("merge_target_id")).isNull();
    }

    @Test
    void preparesPendingReviewMergeTargetOnlyWithinSameTenantProjectAndType() {
        Long tenantId = 6211L;
        Long projectId = 6311L;
        Long userId = 6411L;
        Long matchingTargetId = insertCharacter(tenantId, projectId, "林晚", "CONFIRMED", null);
        insertCharacter(tenantId + 1, projectId, "林晚", "CONFIRMED", null);
        insertCharacter(tenantId, projectId + 1, "林晚", "CONFIRMED", null);
        insertScene(tenantId, projectId, "林晚", "CONFIRMED", null);

        draftService.replaceDrafts(
            tenantId,
            projectId,
            userId,
            new ScriptElementExtractionResult(
                ScriptElementType.CHARACTER,
                List.of(character("林晚")),
                List.of(),
                List.of()
            )
        );

        Map<String, Object> pending = jdbc.queryForMap("""
            select name, status, merge_target_id
              from character_asset
             where tenant_id = ? and project_id = ? and name = ? and status = 'PENDING_REVIEW' and deleted_at is null
            """, tenantId, projectId, "林晚");
        assertThat(pending.get("merge_target_id")).isEqualTo(matchingTargetId);
    }

    @Test
    void confirmationMergesPendingReviewIntoTargetAndDeletesPendingRow() {
        Long tenantId = 6221L;
        Long projectId = 6321L;
        Long targetId = insertCharacter(tenantId, projectId, "林晚", "CONFIRMED", null);
        Long pendingId = insertCharacter(tenantId, projectId, "林晚", "PENDING_REVIEW", targetId);
        jdbc.update("""
            update character_asset
               set role_type = 'LEAD', gender = '女', age_range = '25-30', identity = '回归千金',
                   personality = '冷静、果断', appearance = '黑色风衣', prompt = '林晚角色定妆照'
             where id = ?
            """, pendingId);

        confirmationService.confirm(tenantId, projectId, ScriptElementType.CHARACTER, pendingId);

        Map<String, Object> target = activeCharacter(targetId);
        assertThat(target.get("role_type")).isEqualTo("LEAD");
        assertThat(target.get("identity")).isEqualTo("回归千金");
        assertThat(target.get("prompt")).isEqualTo("林晚角色定妆照");
        assertThat(deletedAt("character_asset", pendingId)).isNotNull();
    }

    @Test
    void confirmationMergesScenePendingReviewIntoTargetAndDeletesPendingRow() {
        Long tenantId = 6222L;
        Long projectId = 6322L;
        Long targetId = insertScene(tenantId, projectId, "天台", "CONFIRMED", null);
        Long pendingId = insertScene(tenantId, projectId, "天台", "PENDING_REVIEW", targetId);
        jdbc.update("""
            update scene_asset
               set scene_type = 'EXTERIOR', time_atmosphere = '深夜冷风',
                   description = '城市高楼天台', visual_style = '冷色电影感', prompt = '深夜天台'
             where id = ?
            """, pendingId);

        confirmationService.confirm(tenantId, projectId, ScriptElementType.SCENE, pendingId);

        Map<String, Object> target = jdbc.queryForMap("select * from scene_asset where id = ? and deleted_at is null", targetId);
        assertThat(target.get("scene_type")).isEqualTo("EXTERIOR");
        assertThat(target.get("time_atmosphere")).isEqualTo("深夜冷风");
        assertThat(target.get("prompt")).isEqualTo("深夜天台");
        assertThat(deletedAt("scene_asset", pendingId)).isNotNull();
    }

    @Test
    void confirmationMergesPropPendingReviewIntoTargetAndDeletesPendingRow() {
        Long tenantId = 6223L;
        Long projectId = 6323L;
        Long targetId = insertProp(tenantId, projectId, "录音笔", "CONFIRMED", null);
        Long pendingId = insertProp(tenantId, projectId, "录音笔", "PENDING_REVIEW", targetId);
        jdbc.update("""
            update prop_asset
               set prop_type = 'KEY_PROP', appearance = '银色小型录音笔',
                   plot_function = '证明遗嘱被篡改', related_character = '林晚', prompt = '录音笔特写'
             where id = ?
            """, pendingId);

        confirmationService.confirm(tenantId, projectId, ScriptElementType.PROP, pendingId);

        Map<String, Object> target = jdbc.queryForMap("select * from prop_asset where id = ? and deleted_at is null", targetId);
        assertThat(target.get("appearance")).isEqualTo("银色小型录音笔");
        assertThat(target.get("plot_function")).isEqualTo("证明遗嘱被篡改");
        assertThat(target.get("related_character")).isEqualTo("林晚");
        assertThat(deletedAt("prop_asset", pendingId)).isNotNull();
    }

    @Test
    void confirmationDoesNotDeletePendingReviewWhenMergeTargetIsMissing() {
        Long tenantId = 6224L;
        Long projectId = 6324L;
        Long pendingId = insertCharacter(tenantId, projectId, "林晚", "PENDING_REVIEW", 999999L);

        assertThatThrownBy(() -> confirmationService.confirm(tenantId, projectId, ScriptElementType.CHARACTER, pendingId))
            .isInstanceOf(BusinessException.class);

        Map<String, Object> pending = jdbc.queryForMap("select status, deleted_at from character_asset where id = ?", pendingId);
        assertThat(pending.get("status")).isEqualTo("PENDING_REVIEW");
        assertThat(pending.get("deleted_at")).isNull();
    }

    @Test
    void confirmationPromotesStandaloneDraftsForAllElementTypesAndClearsMergeTarget() {
        Long tenantId = 6231L;
        Long projectId = 6331L;
        Long characterId = insertCharacter(tenantId, projectId, "林晚", "DRAFT", 997L);
        Long sceneId = insertScene(tenantId, projectId, "天台", "DRAFT", 998L);
        Long propId = insertProp(tenantId, projectId, "录音笔", "DRAFT", 999L);

        confirmationService.confirm(tenantId, projectId, ScriptElementType.CHARACTER, characterId);
        confirmationService.confirm(tenantId, projectId, ScriptElementType.SCENE, sceneId);
        confirmationService.confirm(tenantId, projectId, ScriptElementType.PROP, propId);

        assertConfirmedWithoutMergeTarget("character_asset", characterId);
        assertConfirmedWithoutMergeTarget("scene_asset", sceneId);
        assertConfirmedWithoutMergeTarget("prop_asset", propId);
    }

    private ScriptElementExtractionResult.CharacterElement character(String name) {
        return new ScriptElementExtractionResult.CharacterElement(
            name,
            "SUPPORTING",
            "女",
            "25-30",
            "角色身份",
            List.of("冷静"),
            "角色外观",
            "角色提示词"
        );
    }

    private Long insertCharacter(Long tenantId, Long projectId, String name, String status, Long mergeTargetId) {
        jdbc.update("""
            insert into character_asset
              (tenant_id, project_id, name, role_type, gender, age_range, identity, personality, appearance,
               relationship_text, plot_function, prompt, status, merge_target_id, created_by, created_at, updated_at)
            values (?, ?, ?, 'SUPPORTING', null, null, null, null, null, null, null, null, ?, ?, 1, now(), now())
            """, tenantId, projectId, name, status, mergeTargetId);
        return jdbc.queryForObject("select max(id) from character_asset where tenant_id = ? and project_id = ?", Long.class, tenantId, projectId);
    }

    private Long insertScene(Long tenantId, Long projectId, String name, String status, Long mergeTargetId) {
        jdbc.update("""
            insert into scene_asset
              (tenant_id, project_id, name, scene_type, time_atmosphere, description, visual_style, plot_reference,
               prompt, status, merge_target_id, created_by, created_at, updated_at)
            values (?, ?, ?, 'INTERIOR', null, null, null, null, null, ?, ?, 1, now(), now())
            """, tenantId, projectId, name, status, mergeTargetId);
        return jdbc.queryForObject("select max(id) from scene_asset where tenant_id = ? and project_id = ?", Long.class, tenantId, projectId);
    }

    private Long insertProp(Long tenantId, Long projectId, String name, String status, Long mergeTargetId) {
        jdbc.update("""
            insert into prop_asset
              (tenant_id, project_id, name, prop_type, appearance, plot_function, related_character,
               prompt, status, merge_target_id, created_by, created_at, updated_at)
            values (?, ?, ?, 'KEY_PROP', null, null, null, null, ?, ?, 1, now(), now())
            """, tenantId, projectId, name, status, mergeTargetId);
        return jdbc.queryForObject("select max(id) from prop_asset where tenant_id = ? and project_id = ?", Long.class, tenantId, projectId);
    }

    private Map<String, Object> activeCharacter(Long id) {
        return jdbc.queryForMap("select * from character_asset where id = ? and deleted_at is null", id);
    }

    private Object deletedAt(String table, Long id) {
        return jdbc.queryForObject("select deleted_at from %s where id = ?".formatted(table), Object.class, id);
    }

    private void assertConfirmedWithoutMergeTarget(String table, Long id) {
        Map<String, Object> row = jdbc.queryForMap("select status, merge_target_id from %s where id = ?".formatted(table), id);
        assertThat(row.get("status")).isEqualTo("CONFIRMED");
        assertThat(row.get("merge_target_id")).isNull();
    }
}
