package com.antshorttv.workflowagent.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RemainingAnalysisSkillCompositionContractTest {
    private static final List<String> FRAMEWORK_CODES = List.of(
        "short-drama-episode-splitting-framework",
        "short-drama-episode-summary-framework",
        "short-drama-asset-recognition-framework"
    );

    @Autowired private WorkflowSkillService skills;

    @Test
    void newFrameworkSkillsLoadWithValidMetadataAndNarrowResponsibilities() {
        List<WorkflowSkillView> frameworks = FRAMEWORK_CODES.stream().map(skills::detail).toList();

        assertThat(frameworks).allSatisfy(skill -> {
            assertThat(skill.name()).isNotBlank();
            assertThat(skill.description()).isNotBlank();
            assertThat(skill.revision()).isNotBlank();
            assertThat(skill.content()).doesNotContain("tenantId", "projectId", "scriptId", "agentRunId");
        });
        assertThat(frameworks.get(0).content()).doesNotContain("save_episode_summary", "save_episode_assets");
        assertThat(frameworks.get(1).content()).doesNotContain("save_episode_splitting", "save_episode_assets");
        assertThat(frameworks.get(2).content()).doesNotContain("save_episode_splitting", "save_episode_summary");
    }

    @Test
    void sharedFoundationSupportsScriptAndEpisodeScopedAgentCompositions() {
        WorkflowSkillView foundation = skills.detail("short-drama-analysis-foundation");

        assertThat(foundation.content())
            .contains("read_current_script", "read_current_episode")
            .contains("Agent 工具白名单", "唯一读取工具", "唯一保存工具")
            .contains("保存成功后", "不得再次保存", "内容已变化");
    }
}
