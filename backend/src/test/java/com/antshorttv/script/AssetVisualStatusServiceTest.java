package com.antshorttv.script;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.ProjectPermissionGuard;
import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AssetVisualStatusServiceTest {
    private JdbcTemplate jdbc;
    private ProjectPermissionGuard permissions;
    private AssetVisualStatusService service;

    @BeforeEach
    void setUp() {
        jdbc = spy(new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:visual_status_" + UUID.randomUUID() + ";MODE=MySQL", "sa", "")));
        // Keep a connection-independent database alive for the duration of each test.
        jdbc.execute("set db_close_delay -1");
        for (String table : List.of("character_asset", "scene_asset", "prop_asset")) {
            jdbc.execute("create table " + table + " (id bigint, tenant_id bigint, project_id bigint, deleted_at timestamp)");
            jdbc.update("insert into " + table + " values (1,10,33,null)");
        }
        jdbc.execute("create table asset_visual_variant (id bigint, tenant_id bigint, project_id bigint, asset_type varchar(30), asset_id bigint, generation_status varchar(30), generation_task_id bigint, current_image_result_id bigint, current_image_url varchar(100), virtual_avatar_asset_id varchar(100), generation_error_code varchar(100), generation_error_message varchar(100), deleted_at timestamp)");
        jdbc.execute("create table ai_image_result (id bigint, tenant_id bigint, project_id bigint, status varchar(30), display_path varchar(100))");
        permissions = mock(ProjectPermissionGuard.class);
        service = new AssetVisualStatusService(jdbc, permissions);
    }

    private void variant(long id, String type, String status) {
        jdbc.update("insert into asset_visual_variant (id,tenant_id,project_id,asset_type,asset_id,generation_status,generation_task_id) values (?,10,33,?,1,?,?)", id,type,status,1000+id);
    }

    @Test
    void batchesOneHundredMixedVariantsInOneProjectedQuery() {
        List<Long> ids = LongStream.rangeClosed(1,100).boxed().toList();
        for (long id : ids) variant(id,List.of("CHARACTER","SCENE","PROP").get((int)id%3),"GENERATING");
        clearInvocations(jdbc);
        var result = service.read(10L,33L,ids);
        assertThat(result).hasSize(100).allSatisfy(row -> {
            assertThat(row.available()).isTrue();
            assertThat(row.generationStatus()).isEqualTo("GENERATING");
            assertThat(row.displayReady()).isFalse();
        });
        verify(permissions).require(10L,33L,"ELEMENT:VIEW");
        assertThat(mockingDetails(jdbc).getInvocations().stream()
            .filter(call -> call.getMethod().getName().equals("query")
                && call.getMethod().getParameterTypes()[1] == org.springframework.jdbc.core.RowMapper.class)
            .map(call -> (String)call.getRawArguments()[0]).toList())
            .singleElement().satisfies(sql -> {
                assertThat(sql).doesNotContain("select *", "prompt", "episode", "provider_result_json");
            });
    }

    @Test
    void waitsForPublishedImageAndReportsFailureWithoutReadingDetails() {
        variant(1,"CHARACTER","COMPLETED");
        variant(2,"SCENE","FAILED");
        jdbc.update("update asset_visual_variant set current_image_result_id=71 where id=1");
        jdbc.update("insert into ai_image_result values (71,10,33,'ACTIVE',null)");
        jdbc.update("update asset_visual_variant set generation_error_code='MODEL_ERROR', generation_error_message='生成失败' where id=2");
        assertThat(service.read(10L,33L,List.of(1L)).get(0).displayReady()).isFalse();
        jdbc.update("update ai_image_result set display_path='display/image.webp' where id=71");
        var result = service.read(10L,33L,List.of(1L,2L));
        assertThat(result.get(0).displayReady()).isTrue();
        assertThat(result.get(1).errorMessage()).isEqualTo("生成失败");
        jdbc.update("update ai_image_result set tenant_id=99 where id=71");
        assertThat(service.read(10L,33L,List.of(1L)).get(0).displayReady()).isFalse();
    }

    @Test
    void hidesOtherScopesDeletedOwnersAndDeletedVariantsAsUnavailable() {
        for(long id=1;id<=5;id++) variant(id,"CHARACTER","GENERATING");
        jdbc.update("update asset_visual_variant set tenant_id=99 where id=2");
        jdbc.update("update asset_visual_variant set project_id=99 where id=3");
        jdbc.update("update asset_visual_variant set deleted_at=current_timestamp where id=4");
        jdbc.update("update asset_visual_variant set asset_id=999 where id=5");
        assertThat(service.read(10L,33L,List.of(1L,2L,3L,4L,5L,6L)))
            .extracting(AssetVisualStatusService.Status::available).containsExactly(true,false,false,false,false,false);
        jdbc.update("update character_asset set deleted_at=current_timestamp");
        assertThat(service.read(10L,33L,List.of(1L)).get(0).available()).isFalse();
    }

    @Test
    void validatesLimitsAndAuthorizesBeforeReading() {
        assertThatThrownBy(() -> service.read(10L,33L,List.of())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.read(10L,33L,List.of(0L))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.read(10L,33L,LongStream.rangeClosed(1,101).boxed().toList())).isInstanceOf(BusinessException.class);
        doThrow(new BusinessException(ErrorCode.FORBIDDEN,"无权访问")).when(permissions).require(10L,33L,"ELEMENT:VIEW");
        clearInvocations(jdbc);
        assertThatThrownBy(() -> service.read(10L,33L,List.of(1L))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void supportsLegacyUrlsAndVirtualAvatarsWithoutReturningLargeImageData() {
        variant(1,"CHARACTER","COMPLETED");
        variant(2,"CHARACTER","COMPLETED");
        variant(3,"PROP","COMPLETED");
        jdbc.update("update asset_visual_variant set current_image_url='/legacy.png' where id=1");
        jdbc.update("update asset_visual_variant set current_image_url='data:image/png;base64,abc' where id=2");
        jdbc.update("update asset_visual_variant set virtual_avatar_asset_id='avatar-1' where id=3");
        assertThat(service.read(10L,33L,List.of(3L,1L,2L,1L)))
            .extracting(Status -> Status.displayReady()).containsExactly(true,true,false);
    }
}
