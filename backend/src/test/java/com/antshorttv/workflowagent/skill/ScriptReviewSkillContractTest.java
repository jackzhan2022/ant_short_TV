package com.antshorttv.workflowagent.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScriptReviewSkillContractTest {
    private static final List<String> DIMENSION_SKILLS = List.of(
        "script-review-dimension-plot-logic",
        "script-review-dimension-dialogue",
        "script-review-dimension-character-relationship",
        "script-review-dimension-character-knowledge",
        "script-review-dimension-character-motivation",
        "script-review-dimension-timeline",
        "script-review-dimension-scene-continuity",
        "script-review-dimension-prop-continuity",
        "script-review-dimension-visual-continuity",
        "script-review-dimension-shootability",
        "script-review-dimension-emotion",
        "script-review-dimension-suspense-reversal",
        "script-review-dimension-foreshadowing"
    );

    private final WorkflowSkillService skills = new WorkflowSkillService(
        new FileSkillRepository(Path.of("skills"), 1024 * 1024, new SkillDocumentParser()),
        mock(SkillReferenceLookup.class));
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void productReviewSkillsExposeOnlyMarkdownPhasesAndCurrentVersionEvidence() throws Exception {
        String foundation = Files.readString(Path.of("skills/script-review-foundation/SKILL.md"));
        String framework = Files.readString(Path.of("skills/script-review-execution-framework/SKILL.md"));
        assertThat(foundation).doesNotContain("范围内正文和历史")
            .contains("不得读取或参考历史审核问题");
        assertThat(framework)
            .contains("MARKDOWN_QUICK", "MARKDOWN_DEEP_CHILD", "MARKDOWN_DEEP_AGGREGATION")
            .doesNotContain("DEEP_SEMANTIC", "save_review_semantic_decisions",
                "legacy structured", "历史结构化任务");
        assertThat(Files.exists(Path.of("skills/script-review-semantic-quality/SKILL.md"))).isFalse();
    }

    @Test
    void commonAndDimensionSkillsDefineTrustedEvidenceAndMarkdownBoundaries() {
        assertThat(skills.detail("script-review-foundation").content())
            .contains("可信来源", "精确引文", "严重程度", "不确定", "范围", "内容已变化", "Markdown")
            .doesNotContain("必须以一次成功 `save_review_result`");
        assertThat(skills.detail("script-review-execution-framework").content())
            .contains("QUICK", "DEEP_CHILD", "DEEP_AGGREGATION", "覆盖", "去重", "Markdown")
            .doesNotContain("调用 `save_review_result`");

        assertThat(DIMENSION_SKILLS.stream().map(skills::detail).toList()).allSatisfy(skill ->
            assertThat(skill.content())
                .contains("检查项", "不应报告", "证据要求", "严重程度", "可执行建议")
                .doesNotContain("tenantId", "projectId", "taskId", "versionId"));
        assertThat(skills.detail("script-review-cross-episode-synthesis").content())
            .contains("跨单元", "身份", "时间线", "场景", "道具", "视觉", "情绪", "因果", "悬念", "反转", "伏笔",
                "Markdown", "相同维度", "根因", "稳定位置", "保留全部不同引用");
        assertThat(skills.list("script-review")).extracting(WorkflowSkillView::code)
            .doesNotContain("script-review-semantic-quality");
    }

    @Test
    void serverOwnedDimensionCatalogIsExactDeterministicAndRejectsUnknownValues() throws Exception {
        Class<?> type = Class.forName("com.antshorttv.review.ReviewDimension");
        Method parseAll = type.getMethod("parseAll", List.class);
        Method skillCodes = type.getMethod("skillCodes", List.class, boolean.class);

        @SuppressWarnings("unchecked")
        List<Object> selected = (List<Object>) parseAll.invoke(null, List.of("道具连续性", "台词合理性", "道具连续性"));
        @SuppressWarnings("unchecked")
        List<String> codes = (List<String>) skillCodes.invoke(null, selected, false);
        assertThat(codes).containsExactly(
            "script-review-foundation", "script-review-execution-framework",
            "script-review-dimension-dialogue", "script-review-dimension-prop-continuity");

        org.assertj.core.api.Assertions.assertThatExceptionOfType(java.lang.reflect.InvocationTargetException.class)
            .isThrownBy(() -> parseAll.invoke(null, List.of("任意客户端Skill")))
            .withCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void behaviorFixturesCoverRequiredJudgmentCases() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/workflowagent/script-review-behavior-fixtures.json")) {
            assertThat(input).isNotNull();
            JsonNode root = objectMapper.readTree(input);
            assertThat(root.path("fixtures")).hasSizeGreaterThanOrEqualTo(6);
            assertThat(root.toString()).contains(
                "valid_issue", "false_positive", "uncertain_evidence", "multi_hit",
                "dimension_isolation", "cross_episode");
        }
    }
}
