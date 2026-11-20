package com.antshorttv.script;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.security.TenantContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class CharacterVoiceSettingServiceTest {
    @Autowired private CharacterVoiceSettingService service;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private ProjectPermissionGuard permissions;

    @BeforeEach
    void seed() {
        when(permissions.require(anyLong(), anyLong(), anyString())).thenReturn(new TenantContext(99L, 92001L, 98L, "MEMBER"));
        jdbc.update("""
            insert into character_asset (id,tenant_id,project_id,name,role_type,status,created_by,created_at,updated_at)
            values (92101,92001,92002,'陈渡','MAIN','CONFIRMED',99,now(),now()),
                   (92102,92001,92002,'艾斯','MAIN','CONFIRMED',99,now(),now()),
                   (92103,92001,92003,'其他项目','MAIN','CONFIRMED',99,now(),now())
            """);
        jdbc.update("""
            insert into storyboard (id,tenant_id,project_id,episode_no,shot_no,visual_description,status,created_by,created_at,updated_at)
            values (92201,92001,92002,1,1,'相遇','DRAFT',99,now(),now()),
                   (92202,92001,92002,1,2,'转身','DRAFT',99,now(),now()),
                   (92203,92001,92003,1,1,'其他项目','DRAFT',99,now(),now())
            """);
        jdbc.update("""
            insert into asset_visual_variant (id,tenant_id,project_id,asset_type,asset_id,name,source_type,generation_status,is_primary,created_by,created_at,updated_at)
            values (92301,92001,92002,'CHARACTER',92101,'灼伤','MANUAL','COMPLETED',true,99,now(),now()),
                   (92302,92001,92002,'CHARACTER',92101,'正常','MANUAL','COMPLETED',false,99,now(),now()),
                   (92303,92001,92002,'CHARACTER',92102,'默认','MANUAL','COMPLETED',true,99,now(),now())
            """);
    }

    @Test
    void persistsCharacterDefaultsAndExactShotLookOverridesWithoutChangingOtherScopes() {
        service.save(92001L,92002L,new CharacterVoiceSettingService.Command(92101L,0L,0L," role "," 沉稳男声 "));
        service.save(92001L,92002L,new CharacterVoiceSettingService.Command(92101L,92201L,92301L,"shot","少年男声"));
        assertThat(service.list(92001L,92002L,List.of(92201L))).hasSize(2);
        assertThat(service.list(92001L,92002L,List.of(92202L))).singleElement().satisfies(row -> {
            assertThat(row.storyboardId()).isZero(); assertThat(row.voiceId()).isEqualTo("role");
        });
        service.save(92001L,92002L,new CharacterVoiceSettingService.Command(92101L,92201L,92301L,"edited","更新音色"));
        assertThat(jdbc.queryForObject("select count(*) from character_voice_setting where character_id=92101",Integer.class)).isEqualTo(2);
        assertThat(new CharacterVoiceSettingService(jdbc,permissions).list(92001L,92002L,List.of(92201L)))
            .extracting(CharacterVoiceSettingService.Setting::voiceId).containsExactlyInAnyOrder("role","edited");
        service.delete(92001L,92002L,92101L,92201L,92301L);
        assertThat(service.list(92001L,92002L,List.of(92201L))).singleElement().satisfies(row -> assertThat(row.voiceName()).isEqualTo("沉稳男声"));
        verify(permissions,atLeastOnce()).require(92001L,92002L,"ELEMENT:EDIT");
        verify(permissions,atLeastOnce()).require(92001L,92002L,"STORYBOARD:EDIT");
    }

    @Test
    void httpSaveReadAndResetUseTheSamePersistedConfiguration() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new CharacterVoiceSettingController(service)).build();
        String endpoint = "/api/projects/92002/character-voice-settings";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(endpoint)
                .header("X-Tenant-Id",92001).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""
                  {"characterId":92101,"storyboardId":0,"variantId":0,"voiceId":"role","voiceName":"角色男声"}
                  """))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.voiceId").value("role"));
        service.save(92001L,92002L,new CharacterVoiceSettingService.Command(92101L,92201L,92301L,"shot","本分镜"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(endpoint)
                .header("X-Tenant-Id",92001).param("storyboardIds","92201"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.length()").value(2));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(endpoint)
                .header("X-Tenant-Id",92001).param("characterId","92101").param("storyboardId","92201").param("variantId","92301"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        assertThat(service.list(92001L,92002L,List.of(92201L))).singleElement()
            .satisfies(row -> assertThat(row.voiceId()).isEqualTo("role"));
        assertThat(service.list(92001L,92003L,List.of())).isEmpty();
    }

    @Test
    void rejectsForeignProjectsAndMismatchedLooksBeforeSaving() {
        for (var command : List.of(
            new CharacterVoiceSettingService.Command(92103L,0L,0L,"bad",null),
            new CharacterVoiceSettingService.Command(92101L,92203L,92301L,"bad",null),
            new CharacterVoiceSettingService.Command(92101L,92201L,92303L,"bad",null),
            new CharacterVoiceSettingService.Command(92101L,0L,92301L,"bad",null))) {
            assertThatThrownBy(() -> service.save(92001L,92002L,command)).isInstanceOf(BusinessException.class);
        }
        assertThat(jdbc.queryForObject("select count(*) from character_voice_setting",Integer.class)).isZero();
    }

    @Test
    void requiresThePermissionForTheScopeBeingChanged() {
        doThrow(new BusinessException(ErrorCode.FORBIDDEN,"Denied")).when(permissions).require(92001L,92002L,"ELEMENT:EDIT");
        assertThatThrownBy(() -> service.save(92001L,92002L,new CharacterVoiceSettingService.Command(92101L,0L,0L,"role",null)))
            .isInstanceOf(BusinessException.class);
        service.save(92001L,92002L,new CharacterVoiceSettingService.Command(92101L,92201L,92301L,"shot",null));
        assertThat(service.list(92001L,92002L,List.of(92201L))).hasSize(1);
    }
}
