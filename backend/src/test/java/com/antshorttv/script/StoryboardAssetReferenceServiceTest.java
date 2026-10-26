package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.antshorttv.common.BusinessException;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.security.TenantContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class StoryboardAssetReferenceServiceTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StoryboardAssetReferenceRepository repository;
    @Autowired private StoryboardAssetReferenceLegacyResolver legacyResolver;
    private StoryboardAssetReferenceService service;

    @BeforeEach
    void seed() {
        ProjectPermissionGuard guard = mock(ProjectPermissionGuard.class);
        when(guard.require(9901L, 9911L, "STORYBOARD:EDIT"))
            .thenReturn(new TenantContext(1L, 9901L, 1L, "OWNER"));
        service = new StoryboardAssetReferenceService(guard, jdbc, repository, legacyResolver);

        jdbc.update("insert into tenant (id,code,name,type,status,created_at,updated_at) values (9901,'bind-a','A','TEAM','ACTIVE',now(),now())");
        jdbc.update("insert into tenant (id,code,name,type,status,created_at,updated_at) values (9902,'bind-b','B','TEAM','ACTIVE',now(),now())");
        jdbc.update("insert into project (id,tenant_id,name,code,owner_id,status,created_by,created_at,updated_at) values (9911,9901,'A','bind-a',1,'ACTIVE',1,now(),now())");
        jdbc.update("insert into project (id,tenant_id,name,code,owner_id,status,created_by,created_at,updated_at) values (9912,9902,'B','bind-b',1,'ACTIVE',1,now(),now())");
        jdbc.update("insert into storyboard (id,tenant_id,project_id,episode_no,shot_no,visual_description,characters,scene,props,status,created_by,created_at,updated_at) values (9921,9901,9911,1,1,'shot','old','old scene','old prop','DRAFT',1,now(),now())");

        jdbc.update("insert into character_asset (id,tenant_id,project_id,name,role_type,status,created_by,created_at,updated_at) values (9931,9901,9911,'Serena','LEAD','CONFIRMED',1,now(),now())");
        jdbc.update("insert into scene_asset (id,tenant_id,project_id,name,scene_type,status,created_by,created_at,updated_at) values (9932,9901,9911,'走廊','INTERIOR','CONFIRMED',1,now(),now())");
        jdbc.update("insert into prop_asset (id,tenant_id,project_id,name,prop_type,status,created_by,created_at,updated_at) values (9933,9901,9911,'手机','KEY_PROP','CONFIRMED',1,now(),now())");
        jdbc.update("insert into character_asset (id,tenant_id,project_id,name,role_type,status,created_by,created_at,updated_at) values (9934,9902,9912,'Other','LEAD','CONFIRMED',1,now(),now())");

        jdbc.update("""
            insert into asset_visual_variant
              (id,tenant_id,project_id,asset_type,asset_id,name,source_type,generation_status,
               current_image_url,is_primary,created_by,created_at,updated_at)
            values
              (9941,9901,9911,'CHARACTER',9931,'日常装','MANUAL','COMPLETED','/serena.png',true,1,now(),now()),
              (9942,9901,9911,'SCENE',9932,'夜间走廊','MANUAL','COMPLETED','/hall.png',true,1,now(),now()),
              (9943,9901,9911,'PROP',9933,'默认形象','MANUAL','NOT_STARTED',null,true,1,now(),now()),
              (9944,9902,9912,'CHARACTER',9934,'Other','MANUAL','COMPLETED','/other.png',true,1,now(),now())
            """);
    }

    @Test
    void staleCharacterBindingFallsBackToSameCharactersPrimary() {
        jdbc.update("""
            insert into asset_visual_variant (id,tenant_id,project_id,asset_type,asset_id,name,source_type,generation_status,is_primary,created_by,created_at,updated_at,deleted_at)
            values (9950,9901,9911,'CHARACTER',9931,'已删除服装','MANUAL','COMPLETED',false,1,now(),now(),now())
            """);
        var result=service.replace(9901L,9911L,9921L,new ReplaceStoryboardAssetReferencesRequest(List.of(command("CHARACTER",9931L,9950L,"VISIBLE",0))));
        assertThat(result.get(0).variantId()).isEqualTo(9941L);
        assertThat(jdbc.queryForObject("select variant_id from storyboard_asset_reference where storyboard_id=9921 and retired_at is null",Long.class)).isEqualTo(9941L);
    }

    @Test
    void replacesMultipleReferencesAndSynchronizesLegacyFields() {
        var response = service.replace(9901L, 9911L, 9921L,
            new ReplaceStoryboardAssetReferencesRequest(List.of(
                command("CHARACTER", 9931L, null, "VISIBLE", 0),
                command("SCENE", 9932L, 9942L, "MAIN", 0),
                command("PROP", 9933L, null, "VISIBLE", 0)
            )));

        assertThat(response).extracting(StoryboardAssetReferenceResponse::resolutionStatus)
            .containsExactly("RESOLVED", "RESOLVED", "ASSET_PENDING");
        assertThat(response.get(0).variantId()).isEqualTo(9941L);
        assertThat(jdbc.queryForMap(
            "select characters,scene,props from storyboard where id=9921"))
            .containsEntry("CHARACTERS", "Serena")
            .containsEntry("SCENE", "走廊")
            .containsEntry("PROPS", "手机");

        assertThat(service.replace(9901L, 9911L, 9921L,
            new ReplaceStoryboardAssetReferencesRequest(List.of()))).isEmpty();
        assertThat(jdbc.queryForMap(
            "select characters,scene,props from storyboard where id=9921"))
            .containsEntry("CHARACTERS", null)
            .containsEntry("SCENE", null)
            .containsEntry("PROPS", null);
    }

    @Test
    void preservesExistingUnresolvedReferencesWhenEditingOtherAssets() {
        jdbc.update("""
            insert into storyboard_asset_reference
              (tenant_id,project_id,storyboard_id,asset_type,reference_role,sort_order,
               resolution_status,source_type,source_name,locked_by_user,created_by,created_at,updated_at)
            values
              (9901,9911,9921,'CHARACTER','VISIBLE',0,'UNRESOLVED','AI','神秘人',false,1,now(),now()),
              (9901,9911,9921,'PROP','VISIBLE',0,'UNRESOLVED','LEGACY','旧道具',false,1,now(),now())
            """);

        var commands = List.of(
            command("CHARACTER", 9931L, 9941L, "VISIBLE", 0),
            new StoryboardAssetReferenceCommand("CHARACTER", null, null, "VISIBLE", 1, "神秘人"),
            new StoryboardAssetReferenceCommand("PROP", null, null, "VISIBLE", 0, "旧道具"),
            command("SCENE", 9932L, null, "MAIN", 0)
        );
        var response = service.replace(9901L, 9911L, 9921L,
            new ReplaceStoryboardAssetReferencesRequest(commands));

        assertThat(response).extracting(StoryboardAssetReferenceResponse::resolutionStatus)
            .containsExactly("RESOLVED", "UNRESOLVED", "RESOLVED", "UNRESOLVED");
        assertThat(response.get(1).assetId()).isNull();
        assertThat(response.get(1).sourceName()).isEqualTo("神秘人");
        assertThat(response.get(1).sourceType()).isEqualTo("AI");
        assertThat(response.get(1).lockedByUser()).isFalse();
        assertThat(response.get(3).sourceType()).isEqualTo("LEGACY");
        assertThat(jdbc.queryForMap("select characters,scene,props from storyboard where id=9921"))
            .containsEntry("CHARACTERS", "Serena、神秘人")
            .containsEntry("SCENE", "走廊")
            .containsEntry("PROPS", "旧道具");
        assertThat(service.replace(9901L, 9911L, 9921L,
            new ReplaceStoryboardAssetReferencesRequest(commands))).hasSize(4);

        var confirmed = service.replace(9901L, 9911L, 9921L,
            new ReplaceStoryboardAssetReferencesRequest(List.of(
                command("CHARACTER", 9931L, null, "VISIBLE", 0)
            )));
        assertThat(confirmed).hasSize(1);
        assertThat(confirmed.get(0).sourceType()).isEqualTo("MANUAL");
        assertThat(confirmed.get(0).lockedByUser()).isTrue();
        assertThat(jdbc.queryForMap("select characters,scene,props from storyboard where id=9921"))
            .containsEntry("CHARACTERS", "Serena")
            .containsEntry("SCENE", null)
            .containsEntry("PROPS", null);
    }

    @Test
    void persistsUnresolvedLegacyNamesOnFirstControlledSave() {
        jdbc.update("update storyboard set characters='Serena、神秘人',scene=null,props='旧道具' where id=9921");
        var response = service.replace(9901L, 9911L, 9921L,
            new ReplaceStoryboardAssetReferencesRequest(List.of(
                command("CHARACTER", 9931L, null, "VISIBLE", 0),
                new StoryboardAssetReferenceCommand("CHARACTER", null, null, "VISIBLE", 1, "神秘人"),
                new StoryboardAssetReferenceCommand("PROP", null, null, "VISIBLE", 0, "旧道具")
            )));

        assertThat(response).hasSize(3);
        assertThat(response.get(1).sourceType()).isEqualTo("LEGACY");
        assertThat(response.get(1).resolutionStatus()).isEqualTo("UNRESOLVED");
        assertThat(response.get(1).lockedByUser()).isFalse();
        assertThat(repository.listActive(9901L, 9911L, 9921L)).hasSize(3);
        assertThat(jdbc.queryForObject("select props from storyboard where id=9921", String.class))
            .isEqualTo("旧道具");
    }

    @Test
    void rejectsNewOrInvalidUnboundReferencesWithoutReplacingExistingRows() {
        jdbc.update("update storyboard set characters='神秘人',scene=null,props=null where id=9921");
        jdbc.update("insert into storyboard (id,tenant_id,project_id,episode_no,shot_no,visual_description,characters,status,created_by,created_at,updated_at) values (9922,9902,9912,1,1,'other','其他人','DRAFT',1,now(),now())");
        var unknown = new StoryboardAssetReferenceCommand("CHARACTER", null, null, "VISIBLE", 0, "神秘人");
        for (var commands : List.of(
            List.of(new StoryboardAssetReferenceCommand("CHARACTER", null, null, "VISIBLE", 0, "其他人")),
            List.of(new StoryboardAssetReferenceCommand("PROP", null, null, "VISIBLE", 0, "神秘人")),
            List.of(new StoryboardAssetReferenceCommand("CHARACTER", null, 9941L, "VISIBLE", 0, "神秘人")),
            List.of(command("CHARACTER", null, null, "VISIBLE", 0)),
            List.of(unknown, new StoryboardAssetReferenceCommand("CHARACTER", null, null, "VISIBLE", 1, "神秘人"))
        )) {
            assertThatThrownBy(() -> service.replace(9901L, 9911L, 9921L,
                new ReplaceStoryboardAssetReferencesRequest(commands)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("当前项目资产");
            assertThat(repository.listActive(9901L, 9911L, 9921L)).isEmpty();
            assertThat(jdbc.queryForObject("select characters from storyboard where id=9921", String.class))
                .isEqualTo("神秘人");
        }
    }

    @Test
    void rejectsCrossProjectAssetsAndMismatchedVariantsWithoutReplacingExistingRows() {
        service.replace(9901L, 9911L, 9921L,
            new ReplaceStoryboardAssetReferencesRequest(List.of(
                command("CHARACTER", 9931L, 9941L, "VISIBLE", 0)
            )));

        assertThatThrownBy(() -> service.replace(9901L, 9911L, 9921L,
            new ReplaceStoryboardAssetReferencesRequest(List.of(
                command("CHARACTER", 9934L, 9944L, "VISIBLE", 0)
            )))).isInstanceOf(BusinessException.class).hasMessageContaining("当前项目");
        assertThatThrownBy(() -> service.replace(9901L, 9911L, 9921L,
            new ReplaceStoryboardAssetReferencesRequest(List.of(
                command("CHARACTER", 9931L, 9942L, "VISIBLE", 0)
            )))).isInstanceOf(BusinessException.class).hasMessageContaining("不属于");

        assertThat(repository.listActive(9901L, 9911L, 9921L))
            .extracting(item -> item.assetId).containsExactly(9931L);
    }

    private StoryboardAssetReferenceCommand command(
        String type, Long assetId, Long variantId, String role, int order
    ) {
        return new StoryboardAssetReferenceCommand(type, assetId, variantId, role, order, null);
    }
}
