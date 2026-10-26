package com.antshorttv.script;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.antshorttv.common.BusinessException;
import com.antshorttv.rbac.ProjectPermissionGuard;
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
class StoryboardVideoSettingsServiceTest {
    @Autowired private StoryboardVideoSettingsService service;
    @Autowired private StoryboardMapper storyboards;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private ProjectPermissionGuard permissions;
    @BeforeEach
    void seed() {
        jdbc.update("""
            insert into storyboard (id,tenant_id,project_id,episode_no,shot_no,visual_description,video_prompt,duration_seconds,status,created_by,created_at,updated_at)
            values (93101,93001,93002,1,1,'原始剧本','原始提示词',5,'DRAFT',99,now(),now()),
                   (93102,93001,93002,1,2,'其他分镜','其他提示词',8,'DRAFT',99,now(),now()),
                   (93103,93001,93003,1,1,'其他项目','其他提示词',5,'DRAFT',99,now(),now())
            """);
    }
    @Test
    void storesAllFourParametersWithoutChangingScriptDurationOrOtherShotsAndSurvivesReadModels() {
        var settings = new StoryboardVideoSettingsService.Settings(15,"1080p",true,false);
        service.save(93001L,93002L,93101L,settings);
        assertThat(service.get(93001L,93002L,93101L)).isEqualTo(settings);
        var entity = storyboards.selectById(93101L);
        assertThat(entity.durationSeconds).isEqualTo(5);
        assertThat(entity.visualDescription).isEqualTo("原始剧本");
        assertThat(entity.videoPrompt).isEqualTo("原始提示词");
        var browser = StoryboardResponse.from(entity).withAssetReferences(List.of()).forBrowser();
        assertThat(browser.videoGenerationSettings().path("durationSeconds").asInt()).isEqualTo(15);
        assertThat(browser.videoGenerationSettings().path("generateAudio").asBoolean()).isTrue();
        assertThat(service.get(93001L,93002L,93102L)).isNull();
        assertThat(jdbc.queryForObject("select duration_seconds from storyboard where id=93102",Integer.class)).isEqualTo(8);
        service.save(93001L,93002L,93101L,new StoryboardVideoSettingsService.Settings(-1,"720p",false,true));
        assertThat(service.get(93001L,93002L,93101L).durationSeconds()).isEqualTo(-1);
        verify(permissions,atLeastOnce()).require(93001L,93002L,"STORYBOARD:EDIT");
        verify(permissions,atLeastOnce()).require(93001L,93002L,"STORYBOARD:VIEW");
    }
    @Test
    void unrelatedStaleEntityUpdatesCannotOverwriteNewerVideoSettings() {
        var first=new StoryboardVideoSettingsService.Settings(8,"720p",false,true);
        var latest=new StoryboardVideoSettingsService.Settings(15,"1080p",true,false);
        service.save(93001L,93002L,93101L,first);
        var stale=storyboards.selectById(93101L);
        service.save(93001L,93002L,93101L,latest);
        stale.currentVideoUrl="/newly-bound-video.mp4";
        storyboards.updateById(stale);
        assertThat(service.get(93001L,93002L,93101L)).isEqualTo(latest);
    }

    @Test
    void rejectsForeignDeletedAndInvalidInputsWithoutWritingAnything() {
        var valid = new StoryboardVideoSettingsService.Settings(15,"720p",true,false);
        assertThatThrownBy(() -> service.save(93001L,93002L,93103L,valid)).isInstanceOf(BusinessException.class);
        for (var settings : List.of(new StoryboardVideoSettingsService.Settings(0,"720p",true,false),
            new StoryboardVideoSettingsService.Settings(-2,"720p",true,false),
            new StoryboardVideoSettingsService.Settings(601,"720p",true,false),
            new StoryboardVideoSettingsService.Settings(15,"invalid",true,false),
            new StoryboardVideoSettingsService.Settings(15,"720p",null,false))) {
            assertThatThrownBy(() -> service.save(93001L,93002L,93101L,settings)).isInstanceOf(BusinessException.class);
        }
        jdbc.update("update storyboard set deleted_at=now() where id=93101");
        assertThatThrownBy(() -> service.save(93001L,93002L,93101L,valid)).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("select count(*) from storyboard where video_generation_settings_json is not null",Integer.class)).isZero();
    }
    @Test
    void httpSaveAndReloadReturnTheSavedValues() throws Exception {
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new StoryboardVideoSettingsController(service)).build();
        String url="/api/projects/93002/storyboards/93101/video-generation-settings";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url)
                .header("X-Tenant-Id",93001).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"durationSeconds\":15,\"resolution\":\"720p\",\"generateAudio\":false,\"watermark\":true}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url).header("X-Tenant-Id",93001))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.durationSeconds").value(15))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.watermark").value(true));
    }
}
