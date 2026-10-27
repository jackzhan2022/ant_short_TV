package com.antshorttv.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class VideoTaskPresentationTest {
    NamedParameterJdbcTemplate sql;
    AiVideoTaskMapper videos;
    VideoTaskPresentation presentation;
    AiVideoTaskEntity source;
    @BeforeEach void setup() {
        sql=mock(NamedParameterJdbcTemplate.class);videos=mock(AiVideoTaskMapper.class);
        presentation=new VideoTaskPresentation(sql,videos,new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
        source=new AiVideoTaskEntity();source.id=62L;source.tenantId=1L;source.projectId=66L;
        source.status="FAILED";source.errorMessage="image may contain real person";source.createdAt=LocalDateTime.of(2026,10,27,14,17);
    }
    void recovery(String state,Long child,String error) {
        Map<String,Object> row=new HashMap<>();row.put("source_task_id",62L);row.put("status",state);row.put("retry_task_id",child);row.put("error_message",error);
        when(sql.queryForList(anyString(),anyMap())).thenReturn(List.of(row));
    }
    AiVideoTaskEntity child() {
        var child=new AiVideoTaskEntity();child.id=65L;child.tenantId=1L;child.projectId=66L;child.avatarRecoverySourceTaskId=62L;
        child.status="SUCCEEDED";child.createdAt=source.createdAt.plusMinutes(8);return child;
    }
    @Test void onlyMasksFailureWhileAutomaticPreparationIsActive() {
        recovery("RUNNING",null,null);
        var view=presentation.views(List.of(source)).get(0);
        assertThat(view.task().id).isEqualTo(62L);assertThat(view.task().status).isEqualTo("GENERATING");
        assertThat(view.task().executionPhase).isEqualTo("AVATAR_PREPARING");assertThat(view.task().errorMessage).isNull();
        assertThat(source.status).isEqualTo("FAILED");assertThat(source.errorMessage).isNotNull();
        recovery("FAILED",null,"素材入库失败");
        var failed=presentation.views(List.of(source)).get(0);
        assertThat(failed.task().status).isEqualTo("FAILED");assertThat(failed.task().errorMessage).isEqualTo("素材入库失败");
    }
    @Test void preservesOriginalIdentityAndUsesTheChildResultWithoutRewritingHistory() {
        recovery("SUCCEEDED",65L,null);var child=child();when(videos.selectBatchIds(anyCollection())).thenReturn(List.of(child));
        var view=presentation.views(List.of(source)).get(0);
        assertThat(view.task().id).isEqualTo(source.id);assertThat(view.task().createdAt).isEqualTo(source.createdAt);
        assertThat(view.task().status).isEqualTo("SUCCEEDED");assertThat(view.resultTaskId()).isEqualTo(child.id);
        assertThat(child.id).isEqualTo(65L);assertThat(source.status).isEqualTo("FAILED");
    }
    @Test void rejectsCrossTenantCrossProjectAndUnrelatedChildren() {
        recovery("SUCCEEDED",65L,null);
        for(int mismatch=0;mismatch<4;mismatch++) {
            var child=child();
            if(mismatch==0)child.tenantId=2L;if(mismatch==1)child.projectId=67L;
            if(mismatch==2)child.avatarRecoverySourceTaskId=61L;if(mismatch==3)child.deletedAt=LocalDateTime.now();
            when(videos.selectBatchIds(anyCollection())).thenReturn(List.of(child));
            var view=presentation.views(List.of(source)).get(0);
            assertThat(view.task().status).isEqualTo("FAILED");assertThat(view.resultTaskId()).isEqualTo(source.id);
            assertThat(view.task().errorMessage).isEqualTo("关联的视频任务已不可用。");
        }
    }
    @Test void ordinaryFailureRemainsFailureAndAnObsoleteRecoveryDoesNotReplaceALiveTask() {
        when(sql.queryForList(anyString(),anyMap())).thenReturn(List.of());
        assertThat(presentation.views(List.of(source)).get(0).task().errorMessage).isEqualTo(source.errorMessage);
        recovery("RUNNING",null,null);source.status="GENERATING";
        assertThat(presentation.views(List.of(source)).get(0).task()).isSameAs(source);
    }
}
