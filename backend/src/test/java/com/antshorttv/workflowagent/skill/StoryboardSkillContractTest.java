package com.antshorttv.workflowagent.skill;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class StoryboardSkillContractTest {
    @Autowired private WorkflowSkillService skills;

    @Test
    void definesOneUnifiedProductionSkillWithDirectingAndMaterialContracts() {
        assertThat(com.antshorttv.workflowagent.agent.StoryboardAgentBootstrap.SKILLS)
            .containsExactly("short-drama-storyboard-production");
        assertThat(skills.detail("short-drama-storyboard-production").name()).isEqualTo("短剧分镜制作");
        assertThat(skills.detail("short-drama-storyboard-production").content())
            .contains("整集", "10 至 15 秒", "1.5 至 4 秒", "一个主要动作", "原文",
                "schemaVersion: 3", "sourceAnchor", "sourceTo", "后端", "表演", "情绪", "运镜",
                "完整覆盖", "服务端已准备", "save_episode_storyboards", "进出场", "道具状态", "可见结果",
                "3–5 字", "口型", "现场画外", "情绪与决定", "揭示、发现与证据", "能力、专业技术与奇观")
            .doesNotContain("每个内部镜头都提交 `soundSegmentIds`")
            .doesNotContain("sourceStartMarker", "sourceEndMarker", "完成全部读取后");
        assertThat(skills.detail("short-drama-storyboard-production").content())
            .contains("实际使用", "assetKey", "variantKey", "role", "sourceName",
                "当前剧集绑定", "项目主形态", "精确匹配", "UNRESOLVED", "ASSET_PENDING")
            .contains("不得按显示名称反查")
            .contains("服务端选择")
            .doesNotContain("未匹配名称保留为普通文本");
        assertThat(skills.detail("short-drama-storyboard-production").content())
            .contains("镜头切换", "镜头拉近", "镜头跟随", "固定镜头", "推荐示例", "不是封闭枚举");
    }
}
