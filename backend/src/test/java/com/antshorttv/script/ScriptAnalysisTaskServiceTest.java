package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScriptAnalysisTaskServiceTest {

    @Mock
    private ScriptAnalysisTaskMapper taskMapper;

    @Mock
    private ScriptAnalysisStageMapper stageMapper;

    private ScriptAnalysisTaskService service;

    @BeforeEach
    void setUp() {
        service = new ScriptAnalysisTaskService(taskMapper, stageMapper);
    }

    @Test
    void createInitialTaskIfAbsentReturnsExistingTaskWithoutCreatingAnotherOne() {
        ScriptAnalysisTaskEntity existing = new ScriptAnalysisTaskEntity();
        existing.setId(11L);
        when(taskMapper.selectByIdempotencyKey(1L, "SCRIPT_INITIAL_ANALYSIS:99")).thenReturn(existing);

        ScriptAnalysisTaskEntity result = service.createInitialTaskIfAbsent(
            1L,
            2L,
            script(7L),
            version(99L, "第1集\n内容"),
            3L,
            LocalDateTime.of(2026, 8, 23, 10, 0)
        );

        assertThat(result).isSameAs(existing);
        verify(taskMapper, never()).insert(any(ScriptAnalysisTaskEntity.class));
        verify(stageMapper, never()).insert(any(ScriptAnalysisStageEntity.class));
    }

    @Test
    void createInitialTaskIfAbsentCreatesFourOrderedStages() {
        when(taskMapper.selectByIdempotencyKey(1L, "SCRIPT_INITIAL_ANALYSIS:99")).thenReturn(null);

        ScriptAnalysisTaskEntity result = service.createInitialTaskIfAbsent(
            1L,
            2L,
            script(7L),
            version(99L, "第1集\n内容"),
            3L,
            LocalDateTime.of(2026, 8, 23, 10, 0)
        );

        ArgumentCaptor<ScriptAnalysisTaskEntity> taskCaptor = ArgumentCaptor.forClass(ScriptAnalysisTaskEntity.class);
        verify(taskMapper).insert(taskCaptor.capture());
        assertThat(result).isSameAs(taskCaptor.getValue());
        assertThat(result.getWorkflowCode()).isEqualTo(ScriptAnalysisTaskService.WORKFLOW_CODE);
        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getCurrentStage()).isEqualTo("GLOBAL_UNDERSTANDING");
        assertThat(result.getCurrentAction()).isEqualTo("等待开始剧情全局理解");

        ArgumentCaptor<ScriptAnalysisStageEntity> stageCaptor = ArgumentCaptor.forClass(ScriptAnalysisStageEntity.class);
        verify(stageMapper, times(4)).insert(stageCaptor.capture());
        assertThat(stageCaptor.getAllValues()).hasSize(4);
        assertThat(stageCaptor.getAllValues()).extracting(ScriptAnalysisStageEntity::getStageCode)
            .containsExactly("GLOBAL_UNDERSTANDING", "EPISODE_SPLITTING", "EPISODE_SUMMARY", "CHARACTER_SCENE_RECOGNITION");
        assertThat(stageCaptor.getAllValues()).extracting(ScriptAnalysisStageEntity::getStageOrder)
            .containsExactly(1, 2, 3, 4);
        assertThat(stageCaptor.getAllValues()).extracting(ScriptAnalysisStageEntity::getCurrentAction)
            .containsExactly("正在理解剧情全局", "正在智能拆分剧集", "正在提炼剧集概要", "正在识别角色和场景");
    }

    @Test
    void retryStageResetsTargetAndLaterStagesWhileKeepingEarlierSuccess() {
        ScriptAnalysisTaskEntity task = new ScriptAnalysisTaskEntity();
        task.setId(88L);
        task.setTenantId(1L);
        task.setProjectId(2L);
        task.setStatus("FAILED");
        task.setCurrentStage("EPISODE_SPLITTING");
        task.setCurrentAction("分析失败");
        task.setOverallProgress(50);
        when(taskMapper.selectOne(any())).thenReturn(task);

        ScriptAnalysisStageEntity stage1 = stage("GLOBAL_UNDERSTANDING", 1, "SUCCEEDED", "已完成");
        ScriptAnalysisStageEntity stage2 = stage("EPISODE_SPLITTING", 2, "FAILED", "出错");
        stage2.setErrorCode("AI_RESPONSE_INVALID");
        stage2.setErrorMessage("bad split");
        stage2.setRetryable(true);
        ScriptAnalysisStageEntity stage3 = stage("EPISODE_SUMMARY", 3, "SUCCEEDED", "已完成");
        ScriptAnalysisStageEntity stage4 = stage("CHARACTER_SCENE_RECOGNITION", 4, "FAILED", "出错");
        when(stageMapper.selectByTask(88L)).thenReturn(List.of(stage1, stage2, stage3, stage4));

        ScriptAnalysisTaskEntity result = service.retryStage(1L, 2L, "EPISODE_SUMMARY");

        assertThat(result.getCurrentStage()).isEqualTo("EPISODE_SUMMARY");
        assertThat(result.getOverallProgress()).isEqualTo(50);
        verify(taskMapper).updateById(task);
        assertThat(stage1.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(stage2.getStatus()).isEqualTo("FAILED");
        assertThat(stage3.getStatus()).isEqualTo("PENDING");
        assertThat(stage3.getCurrentAction()).isEqualTo("正在提炼剧集概要");
        assertThat(stage3.getErrorCode()).isNull();
        assertThat(stage4.getStatus()).isEqualTo("PENDING");
        assertThat(stage4.getCurrentAction()).isEqualTo("等待上一阶段完成");
    }

    private ScriptEntity script(long id) {
        ScriptEntity script = new ScriptEntity();
        script.setId(id);
        return script;
    }

    private ScriptVersionEntity version(long id, String content) {
        ScriptVersionEntity version = new ScriptVersionEntity();
        version.setId(id);
        version.setContent(content);
        return version;
    }

    private ScriptAnalysisStageEntity stage(String code, int order, String status, String currentAction) {
        ScriptAnalysisStageEntity stage = new ScriptAnalysisStageEntity();
        stage.setStageCode(code);
        stage.setStageOrder(order);
        stage.setStatus(status);
        stage.setCurrentAction(currentAction);
        stage.setAttemptNo(1);
        stage.setRetryable(false);
        return stage;
    }
}
