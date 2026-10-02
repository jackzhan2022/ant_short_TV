package com.antshorttv.script;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.antshorttv.common.BusinessException;
import com.antshorttv.project.ProjectAccessResolver;
import java.util.List;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

public class StoryboardMediaQueryServiceTest {
    private JdbcTemplate jdbc;
    private ProjectAccessResolver access;
    private StoryboardMediaQueryService service;

    @BeforeEach
    void setUp() {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:storyboard-media-" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        access = mock(ProjectAccessResolver.class);
        when(access.requireView(1L, 2L)).thenReturn(new com.antshorttv.project.ProjectAccessContext(
            new com.antshorttv.security.TenantContext(1L, 1L, 1L, "OWNER"), new com.antshorttv.project.ProjectEntity(),
            com.antshorttv.project.ProjectAccessSource.TENANT_WIDE, null, null,
            java.util.Set.of("PROJECT:VIEW", "ELEMENT:VIEW", "AI_IMAGE_TASK:VIEW", "AI_VIDEO_TASK:VIEW", "AI_VOICE_TASK:VIEW"), null));
        service = new StoryboardMediaQueryService(jdbc, access);
        jdbc.execute("create table storyboard(id bigint,tenant_id bigint,project_id bigint,episode_id bigint,characters varchar(300),scene varchar(300),props varchar(300),first_frame_image_id bigint,current_video_result_id bigint,current_voice_result_id bigint,deleted_at timestamp)");
        jdbc.execute("create table script_episode(id bigint,tenant_id bigint,project_id bigint,episode_no int,title varchar(100),status varchar(30),retired_at timestamp)");
        jdbc.execute("create table storyboard_asset_reference(tenant_id bigint,project_id bigint,storyboard_id bigint,asset_type varchar(30),asset_id bigint,variant_id bigint,retired_at timestamp)");
        jdbc.execute("create table asset_visual_variant(id bigint,tenant_id bigint,project_id bigint,asset_type varchar(30),asset_id bigint,name varchar(100),source_type varchar(30),generation_status varchar(30),current_image_result_id bigint,current_image_url varchar(300),is_primary boolean,deleted_at timestamp)");
        jdbc.execute("create table asset_visual_variant_episode(id bigint,tenant_id bigint,project_id bigint,script_id bigint,episode_id bigint,asset_type varchar(30),asset_id bigint,variant_id bigint,is_preferred boolean,binding_status varchar(30),created_by bigint,created_at timestamp,updated_at timestamp,retired_at timestamp)");
        for (String type : List.of("character", "scene", "prop")) {
            jdbc.execute("create table " + type + "_asset(id bigint,tenant_id bigint,project_id bigint,name varchar(100),deleted_at timestamp)");
        }
        for (String kind : List.of("image", "video", "voice")) {
            jdbc.execute("create table ai_" + kind + "_task(id bigint,tenant_id bigint,project_id bigint,storyboard_id bigint,target_type varchar(30),target_id bigint,task_type varchar(30),model_id bigint,provider_code varchar(50),model varchar(50),aspect_ratio varchar(20),image_count int,duration_seconds int,status varchar(30),error_message varchar(100),created_at timestamp,execution_id bigint,deleted_at timestamp)");
            jdbc.execute("create table ai_" + kind + "_result(id bigint,tenant_id bigint,project_id bigint,task_id bigint,storyboard_id bigint,target_type varchar(30),target_id bigint,image_url varchar(300),thumbnail_url varchar(300),display_path varchar(300),video_url varchar(300),storage_path varchar(300),cover_url varchar(300),audio_url varchar(300),duration_seconds decimal(8,2),width int,height int,file_size bigint,format varchar(30),material_id bigint,is_selected boolean,status varchar(30),created_at timestamp)");
        }
        jdbc.execute("create table ai_execution_task(id bigint,tenant_id bigint,project_id bigint,status varchar(30),phase varchar(30),progress int,execution_version int,source_execution_id bigint,root_execution_id bigint,retryable boolean,result_type varchar(30),result_id bigint,error_code varchar(60),error_message varchar(100),usage_cost_status varchar(30),provider_cost_summary_json text,business_call_count int,technical_retry_count int,point_settlement_status varchar(30),reserved_points decimal(12,2),settled_points decimal(12,2),released_points decimal(12,2),started_at timestamp,created_at timestamp,updated_at timestamp,completed_at timestamp,canceled_at timestamp)");
        jdbc.update("insert into storyboard(id,tenant_id,project_id,episode_id,current_video_result_id) values(10,1,2,501,100)");
        jdbc.update("insert into storyboard(id,tenant_id,project_id) values(11,1,2)");
        jdbc.update("insert into storyboard(id,tenant_id,project_id) values(12,9,8)");
        for (int id = 1; id <= 200; id++) {
            jdbc.update("insert into ai_video_task(id,tenant_id,project_id,storyboard_id,status,created_at) values(?,1,2,10,'SUCCEEDED',current_timestamp)", id);
            jdbc.update("insert into ai_video_result(id,tenant_id,project_id,task_id,storyboard_id,video_url,storage_path,cover_url,material_id,is_selected,status,created_at) values(?,1,2,?,10,?,?,?,777,false,'ACTIVE',current_timestamp)", id == 1 ? 100 : 1000 + id, id, "/video-" + id, "materials/1/2/videos/" + id + ".mp4", "/materials/1/2/images/cover.png");
        }
    }

    @Test
    void retainsAnOldBoundVideoWithoutLoadingTwoHundredHistoricalTasks() {
        var result = service.summary(1L, 2L, List.of(10L));
        var tasks = result.get("videoTasks");
        assertThat(tasks).hasSize(2);
        assertThat(tasks).extracting(task -> ((Number) task.get("id")).longValue()).containsExactly(200L, 1L);
        assertThat(tasks.get(1).get("results").toString()).contains("/api/projects/2/ai-video-results/100/playback");
        assertThat(tasks.get(1).get("results").toString()).contains("materialId=777");
        assertThat(tasks.get(1).get("results").toString()).contains("/api/projects/2/ai-video-results/100/cover");
        assertThat(tasks.toString()).doesNotContain("prompt", "snapshot");
        verify(access).requireView(1L, 2L);
    }

    @Test
    void rejectsForeignIdsInsteadOfReturningPartialData() {
        assertThatThrownBy(() -> service.summary(1L, 2L, List.of(10L, 12L)))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsAnUnboundedBatch() {
        assertThatThrownBy(() -> service.summary(1L, 2L,
            java.util.stream.LongStream.rangeClosed(1, 101).boxed().toList()))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void resolvesOnlyCurrentAssociatedAssetVariantsInABatch() {
        jdbc.update("insert into scene_asset values(30,1,2,'Parking',null),(31,1,2,'Unrelated',null)");
        jdbc.update("insert into asset_visual_variant values(33,1,2,'SCENE',30,'Parking primary','AI','SUCCEEDED',77,'/original',true,null)");
        jdbc.update("insert into asset_visual_variant values(34,1,2,'SCENE',31,'Unrelated primary','AI','SUCCEEDED',78,'/original',true,null)");
        jdbc.update("update storyboard set scene='Parking' where id=10");
        var result = service.summary(1L, 2L, List.of(10L));
        assertThat(result.get("assetVisuals")).hasSize(1);
        assertThat(result.get("assetVisuals").get(0).get("key")).isEqualTo("SCENE:30");
    }

    @Test
    void keepsEpisodePreferredVariantAndActiveBindingOutsideThePrimaryChoice() {
        jdbc.update("insert into script_episode values(501,1,2,1,'Episode 1','ACTIVE',null)");
        jdbc.update("insert into scene_asset values(30,1,2,'Parking',null),(31,1,2,'Unrelated',null)");
        jdbc.update("insert into asset_visual_variant values(33,1,2,'SCENE',30,'Primary','AI','SUCCEEDED',77,'/primary',true,null)");
        jdbc.update("insert into asset_visual_variant values(35,1,2,'SCENE',30,'Night','AI','SUCCEEDED',79,'/night',false,null)");
        jdbc.update("insert into asset_visual_variant values(36,1,2,'SCENE',31,'Unrelated preferred','AI','SUCCEEDED',80,'/other',false,null)");
        jdbc.update("insert into asset_visual_variant_episode(id,tenant_id,project_id,script_id,episode_id,asset_type,asset_id,variant_id,is_preferred,binding_status,created_by,created_at,updated_at,retired_at) values(1,1,2,1,501,'SCENE',30,35,true,'ACTIVE',1,current_timestamp,current_timestamp,null)");
        jdbc.update("insert into asset_visual_variant_episode(id,tenant_id,project_id,script_id,episode_id,asset_type,asset_id,variant_id,is_preferred,binding_status,created_by,created_at,updated_at,retired_at) values(2,1,2,1,501,'SCENE',31,36,true,'ACTIVE',1,current_timestamp,current_timestamp,null)");
        jdbc.update("update storyboard set scene='Parking' where id=10");
        var visuals = service.summary(1L, 2L, List.of(10L)).get("assetVisuals");
        assertThat(visuals.toString()).contains("Night").contains("episodeId=501").contains("preferred=true");
        assertThat(visuals.toString()).doesNotContain("Unrelated preferred");
    }

    @Test
    void attachesRunningImageExecutionWithoutPerTaskDetailReads() {
        jdbc.update("insert into ai_execution_task(id,tenant_id,project_id,status,phase,progress,execution_version,retryable,business_call_count,technical_retry_count,created_at,updated_at) values(700,1,2,'RUNNING','GENERATING',42,1,false,0,0,current_timestamp,current_timestamp)");
        jdbc.update("insert into ai_image_task(id,tenant_id,project_id,storyboard_id,target_type,target_id,task_type,model_id,provider_code,model,aspect_ratio,image_count,status,created_at,execution_id) values(701,1,2,10,'STORYBOARD',10,'STORYBOARD_FIRST_FRAME',1,'test','image','9:16',1,'RUNNING',current_timestamp,700)");
        var tasks = service.summary(1L, 2L, List.of(10L)).get("imageTasks");
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).get("execution").toString()).contains("status=RUNNING").contains("progress=42");
    }

    public static void main(String[] args) {
        var tests = new StoryboardMediaQueryServiceTest();
        tests.setUp();
        tests.retainsAnOldBoundVideoWithoutLoadingTwoHundredHistoricalTasks();
        tests.setUp();
        tests.rejectsForeignIdsInsteadOfReturningPartialData();
        tests.setUp();
        tests.rejectsAnUnboundedBatch();
        tests.setUp();
        tests.resolvesOnlyCurrentAssociatedAssetVariantsInABatch();
        tests.setUp();
        tests.keepsEpisodePreferredVariantAndActiveBindingOutsideThePrimaryChoice();
        tests.setUp();
        tests.attachesRunningImageExecutionWithoutPerTaskDetailReads();
    }
}
