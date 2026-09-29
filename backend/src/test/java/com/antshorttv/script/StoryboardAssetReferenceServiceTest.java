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
    private StoryboardAssetReferenceService service;

    @BeforeEach
    void seed() {
        ProjectPermissionGuard guard = mock(ProjectPermissionGuard.class);
        when(guard.require(9901L, 9911L, "STORYBOARD:EDIT"))
            .thenReturn(new TenantContext(1L, 9901L, 1L, "OWNER"));
        service = new StoryboardAssetReferenceService(guard, jdbc, repository);

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
