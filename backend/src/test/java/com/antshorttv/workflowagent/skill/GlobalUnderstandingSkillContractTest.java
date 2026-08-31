package com.antshorttv.workflowagent.skill;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class GlobalUnderstandingSkillContractTest {
    @Autowired
    private WorkflowSkillService skills;

    @Test
    void foundationSkillDefinesSourceAuthorizationAndCompletionBoundaries() {
        WorkflowSkillView skill = skills.detail("short-drama-analysis-foundation");

        assertThat(skill.content())
            .contains("read_current_script", "save_global_understanding")
            .contains("当前剧本", "不得捏造", "命名一致", "内容已变化")
            .contains("不能以最终文本代替保存");
    }

    @Test
    void frameworkSkillDefinesTheVersionOneFormalDocumentContract() {
        WorkflowSkillView skill = skills.detail("short-drama-global-understanding-framework");

        assertThat(skill.content())
            .contains("schemaVersion", "logline", "synopsis", "worldSetting", "coreConflict")
            .contains("genres", "themes", "relationships", "turningPoints")
            .contains("ending", "endingHook", "narrativeStyle", "targetAudience")
            .contains("剧集拆分", "角色", "场景", "道具", "保存前检查")
            .contains("完整 JSON", "精简", "不得重复");
    }
}
