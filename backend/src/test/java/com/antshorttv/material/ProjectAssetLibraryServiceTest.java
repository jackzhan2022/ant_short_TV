package com.antshorttv.material;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.project.*;
import com.antshorttv.security.TenantContext;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ProjectAssetLibraryServiceTest {
    @Autowired private ProjectAssetLibraryService service;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private ProjectAccessResolver access;
    private static final Set<String> ALL = Set.of("MENU:ASSET_LIBRARY");

    private void grant(long tenant, long project, Set<String> codes) {
        ProjectEntity entity = new ProjectEntity(); entity.id=project; entity.tenantId=tenant;
        when(access.requireView(tenant, project)).thenReturn(new ProjectAccessContext(
            new TenantContext(99L, tenant, 98L, "MEMBER"), entity, ProjectAccessSource.PROJECT_MEMBER,
            null, null, codes, null));
    }

    @BeforeEach
    void seed() {
        grant(91001, 91002, ALL);
        grant(91001, 91003, ALL);
        jdbc.update("""
            insert into character_asset (id,tenant_id,project_id,name,role_type,status,created_by,created_at,updated_at)
            values (91101,91001,91002,'林夏','LEAD','CONFIRMED',99,now(),now())
            """);
        jdbc.update("""
            insert into scene_asset (id,tenant_id,project_id,name,scene_type,status,created_by,created_at,updated_at)
            values (91102,91001,91002,'地下车库','INDOOR','CONFIRMED',99,now(),now())
            """);
        jdbc.update("""
            insert into prop_asset (id,tenant_id,project_id,name,prop_type,status,created_by,created_at,updated_at)
            values (91103,91001,91002,'怀表','KEY_PROP','CONFIRMED',99,now(),now())
            """);
        String[] types = {"CHARACTER","SCENE","PROP"};
        for (int i=0; i<3; i++) {
            jdbc.update("""
                insert into asset_visual_variant
                  (id,tenant_id,project_id,asset_type,asset_id,name,source_type,generation_status,is_primary,current_image_result_id,created_by,created_at,updated_at)
                values (?,91001,91002,?,?,'默认形象','MANUAL','COMPLETED',true,?,99,now(),now())
                """, 91201+i, types[i], 91101+i, 91301+i);
            image(91301+i, 91001, 91002, "VISUAL_VARIANT", 91201+i, "ACTIVE");
        }
        jdbc.update("""
            insert into storyboard (id,tenant_id,project_id,episode_no,shot_no,visual_description,status,created_by,created_at,updated_at)
            values (91401,91001,91002,1,1,'相遇','DRAFT',99,now(),now()),
                   (91402,91001,91002,2,3,'转身','DRAFT',99,now(),now())
            """);
        image(91304,91001,91002,"STORYBOARD",91401,"ACTIVE");
        image(91305,91001,91003,"VISUAL_VARIANT",91201,"ACTIVE");
        image(91306,99999,91002,"VISUAL_VARIANT",91201,"ACTIVE");
        image(91307,91001,91002,"VISUAL_VARIANT",91201,"DELETED");
        jdbc.update("""
            insert into ai_video_task
              (id,tenant_id,project_id,storyboard_id,provider_code,model,prompt,duration_seconds,aspect_ratio,status,created_by,created_at,updated_at)
            values (91501,91001,91002,91401,'mock','mock','test',5,'16:9','SUCCEEDED',99,now(),now()),
                   (91502,91001,91002,91402,'mock','mock','test',6,'16:9','SUCCEEDED',99,now(),now())
            """);
        jdbc.update("""
            insert into shot_compose_task (id,tenant_id,project_id,storyboard_id,status,created_by,created_at,updated_at)
            values (91503,91001,91002,91401,'SUCCEEDED',99,now(),now())
            """);
        for (int i=0; i<2; i++) {
            jdbc.update("""
                insert into ai_video_result
                  (id,tenant_id,project_id,task_id,storyboard_id,video_url,storage_path,duration_seconds,is_selected,status,created_at,updated_at)
                values (?,91001,91002,?,?, '/video','materials/91001/91002/video.mp4',5,true,'ACTIVE',now(),now())
                """,91601+i,91501+i,91401+i);
        }
        jdbc.update("""
            insert into shot_compose_result
              (id,tenant_id,project_id,task_id,storyboard_id,video_url,storage_path,is_selected,status,created_at,updated_at)
            values (91603,91001,91002,91503,91401,'/shot','materials/91001/91002/shot.mp4',true,'ACTIVE',now(),now())
            """);
        jdbc.update("""
            insert into episode_compose_task
              (id,tenant_id,project_id,episode_no,task_name,storyboard_count,status,created_by,created_at,updated_at)
            values (91504,91001,91002,1,'第一集',1,'SUCCEEDED',99,now(),now())
            """);
        jdbc.update("""
            insert into episode_video_version
              (id,tenant_id,project_id,episode_no,compose_task_id,version_no,version_name,video_url,storage_path,is_current,status,created_by,created_at,updated_at)
            values (91604,91001,91002,1,91504,1,'第一集成片','/episode','materials/91001/91002/episode.mp4',true,'ACTIVE',99,now(),now())
            """);
    }

    private void image(long id,long tenant,long project,String type,long target,String status) {
        jdbc.update("""
            insert into ai_image_result (id,tenant_id,project_id,target_type,target_id,image_url,display_path,is_selected,status,created_at,updated_at)
            values (?,?,?,?,?,'/image','materials/display.webp',false,?,now(),now())
            """,id,tenant,project,type,target,status);
    }

    @Test
    void browsesScopedImagesWithCategorySearchCountsAndPagination() {
        var all = service.list(91001L,91002L,"IMAGE","ALL",null,null,1,2);
        assertThat(all.items().total()).isEqualTo(4);
        assertThat(all.items().data()).hasSize(2);
        assertThat(all.categoryCounts()).containsEntry("CHARACTER",1L).containsEntry("SCENE",1L).containsEntry("PROP",1L).containsEntry("OTHER",1L);
        var character = service.list(91001L,91002L,"IMAGE","CHARACTER",null,"林夏",1,24);
        assertThat(character.items().data()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(91301L);
            assertThat(item.current()).isTrue();
            assertThat(item.sourceType()).isEqualTo("本地上传");
            assertThat(item.previewUrl()).isEqualTo("/api/projects/91002/ai-image-results/91301/display");
        });
        assertThat(service.list(91001L,91003L,"IMAGE","ALL",null,null,1,24).items().total()).isZero();
        assertThat(service.list(91001L,91002L,"IMAGE","ALL",null,"%",1,24).items().total()).isZero();
        assertThat(service.list(91001L,91002L,"IMAGE","ALL",null,null,99,24).items().data()).isEmpty();
        jdbc.update("update asset_visual_variant set deleted_at=now() where id=91201");
        assertThat(service.list(91001L,91002L,"IMAGE","CHARACTER",null,null,1,24).items().total()).isZero();
    }

    @Test
    void assetMenuIncludesAllVideoKindsAndDownloadsWithEpisodeFiltering() {
        var all=service.list(91001L,91002L,"VIDEO",null,null,null,1,24);
        assertThat(all.items().total()).isEqualTo(4);
        assertThat(all.episodeNumbers()).containsExactly(1,2);
        assertThat(all.items().data()).extracting(ProjectAssetLibraryService.LibraryItem::resourceType)
            .contains("AI_VIDEO_RESULT","SHOT_COMPOSE_RESULT","EPISODE_VIDEO_VERSION");
        assertThat(service.list(91001L,91002L,"VIDEO",null,2,null,1,24).items().data())
            .singleElement().satisfies(item -> assertThat(item.id()).isEqualTo(91602L));
        grant(91001,91002,Set.of("MENU:ASSET_LIBRARY"));
        var generated=service.list(91001L,91002L,"VIDEO",null,null,null,1,24);
        assertThat(generated.items().total()).isEqualTo(4);
        assertThat(generated.items().data()).anySatisfy(item -> assertThat(item.downloadUrl()).isNotBlank());
        jdbc.update("update ai_video_task set deleted_at=now() where id=91501");
        assertThat(service.list(91001L,91002L,"VIDEO",null,null,null,1,24).items().total()).isEqualTo(3);
    }

    @Test
    void browsesUploadedAndGeneratedAudioWithinTheSelectedProject() {
        jdbc.update("""
            insert into project_audio_asset(tenant_id,project_id,name,upload_session_token,storage_path,mime_type,
              file_size,duration_seconds,status,created_by,created_at,updated_at)
            values (91001,91002,'林夏参考.wav','audio-one','materials/91001/91002/audio.wav','audio/wav',100,12,'ACTIVE',99,now(),now()),
                   (91001,91003,'其他项目.wav','audio-two','materials/91001/91003/audio.wav','audio/wav',100,12,'ACTIVE',99,now(),now()),
                   (99999,91002,'其他团队.wav','audio-three','materials/99999/91002/audio.wav','audio/wav',100,12,'ACTIVE',99,now(),now())
            """);
        jdbc.update("""
            insert into ai_voice_task(id,tenant_id,project_id,storyboard_id,provider_code,model,
              voice_type,speaker_name,voice_id,text_content,speed,pitch,volume,status,created_by,created_at,updated_at)
            values (91901,91001,91002,91401,'mock','mock','DIALOGUE','林夏','voice','对白',1,1,1,'SUCCEEDED',99,now(),now())
            """);
        jdbc.update("""
            insert into ai_voice_result(id,tenant_id,project_id,task_id,storyboard_id,audio_url,storage_path,duration_seconds,is_selected,status,created_at,updated_at)
            values (91902,91001,91002,91901,91401,'/audio','materials/91001/91002/generated.wav',5,true,'ACTIVE',now(),now())
            """);
        var all = service.list(91001L,91002L,"AUDIO",null,null,null,1,24);
        assertThat(all.items().total()).isEqualTo(2);
        assertThat(all.categoryCounts()).containsEntry("REFERENCE",1L).containsEntry("GENERATED",1L);
        assertThat(all.items().data()).extracting(ProjectAssetLibraryService.LibraryItem::resourceType)
            .containsExactlyInAnyOrder("PROJECT_AUDIO_ASSET","AI_VOICE_RESULT");
        assertThat(all.items().data()).allSatisfy(item -> assertThat(item.previewUrl()).endsWith("/playback"));
        assertThat(service.list(91001L,91002L,"AUDIO","REFERENCE",null,"林夏",1,24).items().data()).hasSize(1);
        assertThat(service.list(91001L,91003L,"AUDIO",null,null,null,1,24).items().data()).hasSize(1);
        assertThat(service.list(91001L,91002L,"AUDIO",null,null,null,2,1).items().data()).hasSize(1);
        jdbc.update("update ai_voice_task set deleted_at=now() where id=91901");
        assertThat(service.list(91001L,91002L,"AUDIO",null,null,null,1,24).items().total()).isEqualTo(1);
        grant(91001,91002,Set.of());
        assertThatThrownBy(() -> service.list(91001L,91002L,"AUDIO",null,null,null,1,24)).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsUnavailableProjectsAndMissingMediaPermissions() {
        when(access.requireView(99999L,91002L)).thenThrow(new BusinessException(ErrorCode.FORBIDDEN,"denied"));
        assertThatThrownBy(() -> service.list(99999L,91002L,"IMAGE",null,null,null,1,24)).hasMessage("denied");
        grant(91001,91002,Set.of());
        assertThatThrownBy(() -> service.list(91001L,91002L,"IMAGE",null,null,null,1,24)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.list(91001L,91002L,"VIDEO",null,null,null,1,24)).isInstanceOf(BusinessException.class);
    }
}
