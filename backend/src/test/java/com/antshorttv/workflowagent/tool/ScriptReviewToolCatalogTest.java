package com.antshorttv.workflowagent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ScriptReviewToolCatalogTest {
    private static final List<String> CODES = List.of(
        "read_review_context", "read_review_content", "read_review_issue_history",
        "save_review_unit_result", "read_review_unit_results", "save_review_result");
    private static final Set<String> FORBIDDEN_IDS = Set.of(
        "tenantId", "userId", "projectId", "taskId", "versionId", "snapshotId", "unitId", "agentRunId");

    @Autowired private WorkflowToolRegistry registry;

    @Test
    void registersSixUniqueStrictBoundedReviewToolsWithoutBusinessIds() {
        assertThat(registry.catalog()).extracting(WorkflowToolMetadata::code).containsAll(CODES);
        assertThat(CODES).doesNotHaveDuplicates();
        CODES.stream().map(registry::require).forEach(tool -> {
            assertThat(tool.inputSchema().path("type").asText()).isEqualTo("object");
            assertThat(tool.inputSchema().path("additionalProperties").asBoolean()).isFalse();
            assertThat(tool.outputSchema().path("type").asText()).isEqualTo("object");
            assertThat(tool.inputSchema().path("properties").fieldNames()).toIterable()
                .doesNotContainAnyElementsOf(FORBIDDEN_IDS);
        });
        assertThat(registry.require("read_review_content").inputSchema().path("properties").path("limit").path("maximum").asInt()).isEqualTo(50000);
        assertThat(registry.require("read_review_issue_history").inputSchema().path("properties").path("pageSize").path("maximum").asInt()).isEqualTo(100);
        assertThat(registry.require("save_review_unit_result").inputSchema().path("properties").path("candidates").path("maxItems").asInt()).isEqualTo(100);
        var hitProperties = registry.require("save_review_unit_result").inputSchema().path("properties")
            .path("candidates").path("items").path("properties").path("hits").path("items").path("properties");
        assertThat(hitProperties.path("startOffset").path("minimum").asInt()).isZero();
        assertThat(hitProperties.path("endOffset").path("minimum").asInt()).isOne();
        assertThat(registry.require("save_review_result").inputSchema().path("properties").path("issues").path("maxItems").asInt()).isEqualTo(500);
    }

    @Test
    void assignsReadAndWriteRiskByPhaseRole() {
        assertThat(CODES.subList(0, 3).stream().map(registry::require).map(WorkflowToolDefinition::riskLevel))
            .containsOnly(ToolRiskLevel.READ_ONLY);
        assertThat(registry.require("read_review_unit_results").riskLevel()).isEqualTo(ToolRiskLevel.READ_ONLY);
        assertThat(registry.require("save_review_unit_result").riskLevel()).isEqualTo(ToolRiskLevel.WRITE);
        assertThat(registry.require("save_review_result").riskLevel()).isEqualTo(ToolRiskLevel.WRITE);
    }
}
