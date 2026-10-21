package com.antshorttv.script;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.common.BusinessException;
import com.antshorttv.material.MaterialFileAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class StoryboardEditorQueryServiceTest {
    private JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();
    private final ProjectPermissionGuard permissions = mock(ProjectPermissionGuard.class);
    private final ScriptEpisodeSummaryRepository summaries = mock(ScriptEpisodeSummaryRepository.class);
    private final StoryboardAssetReferenceRepository references = mock(StoryboardAssetReferenceRepository.class);
    private final MaterialFileAccessService files = mock(MaterialFileAccessService.class);
    private StoryboardEditorQueryService service;

    @BeforeEach
    void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:editor_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("create table script(id bigint primary key, tenant_id bigint, project_id bigint, content clob, updated_at timestamp, deleted_at timestamp)");
        jdbc.execute("create table script_episode(id bigint primary key, tenant_id bigint, project_id bigint, script_id bigint, episode_no int, title varchar(200), content clob, content_fingerprint varchar(200), generated_by_run_id bigint, retired_at timestamp)");
        jdbc.execute("""
            create table storyboard(id bigint primary key, tenant_id bigint, project_id bigint, script_id bigint,
                episode_id bigint, episode_no int, shot_no int, storyboard_no int, shot_type varchar(30),
                visual_description clob, characters varchar(200), scene varchar(200), props varchar(200), dialogue clob,
                duration_seconds int, shot_plan_json clob, prompt_document_json clob, material_binding_status varchar(50),
                source_fingerprint varchar(200), generated_by_run_id bigint, image_prompt clob, video_prompt clob,
                first_frame_url varchar(200), current_video_result_id bigint, current_video_url varchar(200),
                current_shot_result_id bigint, current_shot_video_url varchar(200), deleted_at timestamp)
            """);
        jdbc.execute("create table ai_video_task(id bigint primary key,tenant_id bigint,project_id bigint,deleted_at timestamp)");
        jdbc.execute("create table ai_video_result(id bigint primary key,task_id bigint,storyboard_id bigint,tenant_id bigint,project_id bigint,status varchar(30))");
        jdbc.update("insert into script values (7,10,33,?,now(),null)", "unneeded script body".repeat(10000));
        jdbc.update("insert into script_episode values (71,10,33,7,1,'First episode',?,'fingerprint',8,null)", "large episode body".repeat(10000));
        jdbc.update("insert into script_episode values (72,10,33,7,2,'Second episode','body','hash2',9,null)");
        when(references.listResponsesForStoryboards(anyLong(), anyLong(), anyList())).thenReturn(Map.of());
        when(files.publicUrl(any())).thenAnswer(call -> call.getArgument(0));
        service = new StoryboardEditorQueryService(jdbc, permissions, summaries, references, files, json);
    }

    private void shot(long id, int no, int episodeNo) throws Exception {
        var plan = json.createObjectNode();
        plan.put("storyboardNo", no);
        plan.put("durationSeconds", 5);
        plan.put("time", "night");
        plan.put("lighting", "warm");
        plan.putArray("shots").addObject().put("positioning", "door").put("action", "walk");
        plan.putArray("warnings").addObject().put("code", "WARN").put("message", "check continuity");
        plan.putObject("diagnostics").put("normalizationCount", 2).putArray("classificationWarnings");
        plan.put("rawProviderResponse", "private oversized internal data".repeat(2000));
        var prompt = json.createObjectNode().put("version", 1);
        prompt.putArray("nodes").addObject().put("type", "reference").put("assetId", 1).put("displayName", "Actor");
        jdbc.update("""
            insert into storyboard(id,tenant_id,project_id,script_id,episode_id,episode_no,shot_no,
                storyboard_no,visual_description,dialogue,shot_plan_json,prompt_document_json,
                video_prompt,current_video_result_id,current_video_url)
            values (?,10,33,7,?,?,?,?,'script text','dialogue',?,?,'editable prompt',123,'internal/video.mp4')
            """, id, (long)(70+episodeNo), episodeNo, no, no, plan.toString(), prompt.toString());
    }

    @Test
    void pageProjectsInternalPlanFieldsButPreservesEverythingNeededForEditing() throws Exception {
        shot(1,1,1);
        var result = service.page(10L,33L,1,1,20);
        assertThat(result.episodes()).hasSize(2);
        assertThat(result.episodes().get(0).formalSummary()).isNull();
        assertThat(result.episodes().get(0).summary()).isNull();
        var item = result.storyboards().get(0);
        assertThat(item.shotPlan().has("rawProviderResponse")).isFalse();
        assertThat(item.shotPlan().path("shots").get(0).path("action").asText()).isEqualTo("walk");
        assertThat(item.shotPlan().path("warnings").size()).isEqualTo(1);
        assertThat(item.shotPlan().path("diagnostics").path("normalizationCount").asInt()).isEqualTo(2);
        assertThat(item.promptDocument().path("nodes").get(0).path("assetId").asInt()).isEqualTo(1);
        assertThat(item.visualDescription()).isEqualTo("script text");
        assertThat(item.videoPrompt()).isEqualTo("editable prompt");
        assertThat(item.currentVideoUrl()).isEqualTo("/api/projects/33/ai-video-results/123/playback");
        assertThat(json.writeValueAsBytes(result).length).isLessThan(10000);
        verifyNoInteractions(summaries);
        verify(permissions).require(10L,33L,"PROJECT:VIEW");
    }

    @Test
    void pageKeepsPaginationDefaultEpisodeAndTenantProjectBoundaries() throws Exception {
        for (int n=1;n<=25;n++) shot(n,n,1);
        shot(26,1,2);
        jdbc.update("insert into storyboard(id,tenant_id,project_id,episode_no,shot_no) values (99,99,33,1,1)");
        jdbc.update("insert into storyboard(id,tenant_id,project_id,episode_no,shot_no) values (100,10,99,1,1)");
        var page = service.page(10L,33L,null,2,20);
        assertThat(page.episodeNo()).isEqualTo(1);
        assertThat(page.total()).isEqualTo(25);
        assertThat(page.storyboards()).extracting(StoryboardResponse::shotNo).containsExactly(21,22,23,24,25);
        assertThat(service.page(10L,33L,2,0,999).pageSize()).isEqualTo(100);
        assertThat(service.page(10L,33L,2,0,999).storyboards()).hasSize(1);
    }

    @Test
    void pageAndTotalsExcludeOtherScriptsAndRetiredEpisodesWithTheSameNumber() throws Exception {
        shot(1,1,1);
        shot(2,2,1);
        shot(3,3,1);
        shot(4,4,1);
        jdbc.update("update storyboard set script_id=6 where id=2");
        jdbc.update("update storyboard set episode_id=70 where id=3");
        jdbc.update("update storyboard set episode_id=null where id=4");
        jdbc.update("insert into script_episode values (70,10,33,7,1,'Retired episode','old body','old hash',7,now())");
        var page=service.page(10L,33L,1,1,20);
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.storyboards()).extracting(StoryboardResponse::id).containsExactly(1L,4L);
        assertThat(service.page(10L,33L,99,1,20).total()).isZero();
    }

    @Test
    void deepLinksResolveTheCurrentPageByStoryboardIdAfterHistoricalRowsAreFiltered() throws Exception {
        for(int n=1;n<=25;n++) {
            shot(n,n,1);
            jdbc.update("update storyboard set script_id=6 where id=?",n);
        }
        shot(99,99,1);
        var page=service.page(10L,33L,1,2,20,99L);
        assertThat(page.current()).isEqualTo(1);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.storyboards()).extracting(StoryboardResponse::id).containsExactly(99L);
    }

    @Test
    void reorderedEpisodesUseStableIdentityAndReturnTheCanonicalNumber() throws Exception {
        shot(1,1,1);
        shot(2,2,2);
        shot(3,3,2);
        jdbc.update("update storyboard set episode_id=null where id=3");
        jdbc.update("update script_episode set episode_no=2 where id=71");
        jdbc.update("update script_episode set episode_no=1 where id=72");
        var second=service.page(10L,33L,2,1,20);
        assertThat(second.total()).isEqualTo(2);
        assertThat(second.storyboards()).extracting(StoryboardResponse::id).containsExactly(1L,3L);
        assertThat(second.storyboards()).extracting(StoryboardResponse::episodeNo).containsOnly(2);
        var first=service.page(10L,33L,1,1,20);
        assertThat(first.storyboards()).extracting(StoryboardResponse::id).containsExactly(2L);
        assertThat(first.storyboards().get(0).episodeNo()).isEqualTo(1);
        var link=service.page(10L,33L,1,5,20,1L);
        assertThat(link.episodeNo()).isEqualTo(2);
        assertThat(link.current()).isEqualTo(1);
        assertThat(link.storyboards()).extracting(StoryboardResponse::id).contains(1L);
    }

    @Test
    void focusedPagePreservesAllShotNavigationAndHistoryVideoFlags() throws Exception {
        for(int n=1;n<=25;n++) shot(n,n,1);
        jdbc.update("update storyboard set current_video_url=null");
        jdbc.update("update storyboard set current_video_url='/current.mp4' where id=1");
        jdbc.update("insert into ai_video_task values (1,10,33,null),(2,10,33,now()),(3,99,33,null)");
        jdbc.update("insert into ai_video_result values (1,1,25,10,33,'ACTIVE'),(2,2,24,10,33,'ACTIVE'),(3,3,23,99,33,'ACTIVE')");
        var page=service.page(10L,33L,1,1,20);
        assertThat(page.storyboards()).hasSize(20);
        assertThat(page.total()).isEqualTo(25);
        assertThat(page.storyboardNavigation()).hasSize(25);
        assertThat(page.storyboardNavigation()).extracting(StoryboardNavigationResponse::id)
            .containsExactlyElementsOf(java.util.stream.LongStream.rangeClosed(1,25).boxed().toList());
        assertThat(page.storyboardNavigation().get(0).hasVideo()).isTrue();
        assertThat(page.storyboardNavigation().get(24).hasVideo()).isTrue();
        assertThat(page.storyboardNavigation().get(23).hasVideo()).isFalse();
        assertThat(page.storyboardNavigation().get(22).hasVideo()).isFalse();
    }

    @Test
    void deniedAccessDoesNotQueryStoryboardData() {
        doThrow(new BusinessException(com.antshorttv.common.ErrorCode.FORBIDDEN,"denied"))
            .when(permissions).require(10L,33L,"PROJECT:VIEW");
        assertThatThrownBy(() -> service.page(10L,33L,1,1,20)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(references,summaries);
    }

    @Test
    void detailsLoadOnlyTheCurrentRequestedEpisodeWithoutItsScriptBody() {
        when(summaries.findCurrent(10L,7L,71L)).thenReturn(Optional.empty());
        var details = service.episodeDetails(10L,33L,71L);
        assertThat(details.episodeId()).isEqualTo(71);
        assertThat(details.content()).isNull();
        verify(summaries).findCurrent(10L,7L,71L);
        assertThatThrownBy(() -> service.episodeDetails(99L,33L,71L)).isInstanceOf(BusinessException.class);
        jdbc.update("update script_episode set retired_at=now() where id=71");
        assertThatThrownBy(() -> service.episodeDetails(10L,33L,71L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void legacyScriptFallsBackOnlyWhenNoPersistedEpisodesExist() {
        jdbc.update("delete from script_episode");
        jdbc.update("update script set content=? where id=7", "第1集 开始\n主角出门。\n第2集 后续\n主角回家。");
        var page = service.page(10L,33L,null,1,20);
        assertThat(page.episodes()).hasSize(2);
        assertThat(page.episodeNo()).isEqualTo(1);
    }
}
