package com.antshorttv.workflowagent.skill;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class EpisodeSummarySkillContractTest {
    @Autowired private WorkflowSkillService skills;

    @Test
    void summaryFrameworkDefinesOneEpisodeFidelityAndFormalFields() {
        WorkflowSkillView skill = skills.detail("short-drama-episode-summary-framework");

        assertThat(skill.content())
            .contains("read_current_episode", "summary", "highlights", "endingHook")
            .contains("时间顺序", "因果", "2", "5", "不重复", "null")
            .contains("当前剧集", "不得编造", "其他剧集", "全局理解")
            .contains("角色", "场景", "道具", "保存前检查", "save_episode_summary");
    }
}
