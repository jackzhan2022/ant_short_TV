package com.antshorttv.workflowagent.skill;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class EpisodeSplittingSkillContractTest {
    @Autowired private WorkflowSkillService skills;

    @Test
    void splittingFrameworkDefinesExactSourceCoverageAndResponsibilityBoundary() {
        WorkflowSkillView skill = skills.detail("short-drama-episode-splitting-framework");

        assertThat(skill.content())
            .contains("startMarker", "endMarker", "完整覆盖", "逐字", "唯一")
            .contains("序幕", "尾部", "空白", "重复", "重叠", "缺口")
            .contains("不得", "content", "剧集概要", "角色", "场景", "道具")
            .contains("标题", "保存前检查", "save_episode_splitting")
            .contains("静默判断", "不得输出分析过程", "可信锚点", "不能直接作为正式分集边界");
    }
}
