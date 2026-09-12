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

    @Test
    void recognitionFrameworkDefinesAssetPromptTemplatesAndExample() {
        WorkflowSkillView skill = skills.detail("short-drama-asset-recognition-framework");

        assertThat(skill.content())
            .contains("提示词不是证据", "不得与已知事实冲突")
            .contains("上半身正面平视特写", "全身三视图", "约 40%", "约 60%")
            .contains("四宫格", "正视图", "俯视图", "背视图", "侧视图")
            .contains("不出现人物", "静态事物", "无动态", "不输出文字信息")
            .contains("只输出相对主体形象的视觉增量", "性别:女；衣着描述:白色连衣裙，裙摆湿透，赤脚")
            .contains("只描述道具本体", "双环交叠徽章")
            .contains("完整输出示例", "\"prompt\"")
            .contains("已有非空提示词不得覆盖")
            .contains("hasPrompt=true", "hasPrompt=false", "不得复述或提交替换提示词")
            .contains("身份敏感字段", "未明确")
            .contains("只有一个可见造型", "不得重复创建")
            .contains("仅适用于 Schema 已声明 `prompt`")
            .contains("最小身份锚点", "不得改变资产的核心几何")
            .contains("空间拓扑", "剧情功能");
    }
}
