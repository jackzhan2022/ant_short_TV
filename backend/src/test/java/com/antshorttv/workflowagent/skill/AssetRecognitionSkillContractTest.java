package com.antshorttv.workflowagent.skill;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AssetRecognitionSkillContractTest {
    @Autowired private WorkflowSkillService skills;

    @Test
    void recognitionFrameworkDefinesDeterministicIdentityVariantsAndEvidence() {
        WorkflowSkillView skill = skills.detail("short-drama-asset-recognition-framework");

        assertThat(skill.content())
            .contains("assetKey", "规范名", "别名", "精确", "歧义", "候选")
            .contains("模糊", "不得", "自动合并", "昵称", "称谓")
            .contains("characterLooks", "角色变装", "scenes", "props", "propVariants")
            .contains("时间", "气氛", "不同角色", "不同形态", "可见")
            .contains("衍生道具", "道具关系", "证据", "当前剧集")
            .contains("保存前检查", "save_episode_assets");
    }
}
