package com.antshorttv.workflowagent.skill;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class StoryboardSkillContractTest {
    @Autowired private WorkflowSkillService skills;

    @Test
    void definesPlanningMaterialAndExtensibleSeedanceContracts() {
        assertThat(skills.detail("short-drama-storyboard-planning").content())
            .contains("整集", "10 至 15 秒", "1.5 至 4 秒", "一个主要动作", "原文",
                "schemaVersion: 2", "sourceFrom", "sourceTo", "soundSegmentIds",
                "完整覆盖", "服务端已准备", "save_episode_storyboards")
            .doesNotContain("sourceStartMarker", "sourceEndMarker", "完成全部读取后");
        assertThat(skills.detail("short-drama-storyboard-material-reference").content())
            .contains("实际使用", "assetKey", "当前剧集绑定", "项目主形态", "精确匹配", "ASSET_PENDING");
        assertThat(skills.detail("short-drama-seedance-video-prompt").content())
            .contains("镜头切换", "镜头拉近", "镜头跟随", "固定镜头", "推荐示例", "不是封闭枚举");
    }
}
