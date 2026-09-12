package com.antshorttv.workflowagent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ScriptReviewToolCatalogTest {
    private static final List<String> CODES = List.of("read_review_context", "read_review_content");
    private static final Set<String> FORBIDDEN_IDS = Set.of(
        "tenantId", "userId", "projectId", "taskId", "versionId", "snapshotId", "unitId", "agentRunId");

    @Autowired private WorkflowToolRegistry registry;

    @Test
    void registersStrictBoundedReviewToolsWithoutBusinessIds() {
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
        assertThat(registry.catalog()).extracting(WorkflowToolMetadata::code).doesNotContain(
            "read_review_issue_history", "save_review_unit_result", "read_review_unit_results",
            "read_review_candidates", "save_review_semantic_decisions", "save_review_result");
        assertThat(CODES.stream().map(registry::require).map(WorkflowToolDefinition::riskLevel))
            .containsOnly(ToolRiskLevel.READ_ONLY);
    }
}
