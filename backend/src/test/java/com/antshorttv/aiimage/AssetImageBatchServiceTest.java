package com.antshorttv.aiimage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AssetImageBatchServiceTest {

    @Test
    void classifiesEligibleSkippedAndDependentVariants() {
        assertThat(AssetImageBatchService.classify(
            "NOT_STARTED", "角色主体", true, "CHARACTER", false))
            .isEqualTo("PENDING");
        assertThat(AssetImageBatchService.classify(
            "NOT_GENERATED", "历史角色主体", true, "CHARACTER", false))
            .isEqualTo("PENDING");
        assertThat(AssetImageBatchService.classify(
            "FAILED", "礼服", false, "CHARACTER", true))
            .isEqualTo("PENDING");
        assertThat(AssetImageBatchService.classify(
            "NOT_STARTED", "礼服", false, "CHARACTER", false))
            .isEqualTo("WAITING_DEPENDENCY");
        assertThat(AssetImageBatchService.classify(
            "COMPLETED", "完成", true, "SCENE", false))
            .isEqualTo("SKIPPED_COMPLETED");
        assertThat(AssetImageBatchService.classify(
            "GENERATING", "生成中", true, "PROP", false))
            .isEqualTo("SKIPPED_GENERATING");
        assertThat(AssetImageBatchService.classify(
            "FAILED", " ", true, "SCENE", false))
            .isEqualTo("SKIPPED_MISSING_PROMPT");
        assertThat(AssetImageBatchService.classify(
            "QUEUED", "排队中", true, "SCENE", false))
            .isEqualTo("SKIPPED_OTHER");
    }

    @Test
    void summarizesMixedBatchOutcomes() {
        assertThat(AssetImageBatchService.batchStatus(2, 0, 0, 0, 0))
            .isEqualTo("PENDING");
        assertThat(AssetImageBatchService.batchStatus(0, 2, 0, 0, 0))
            .isEqualTo("RUNNING");
        assertThat(AssetImageBatchService.batchStatus(0, 0, 2, 0, 1))
            .isEqualTo("SUCCEEDED");
        assertThat(AssetImageBatchService.batchStatus(0, 0, 1, 1, 0))
            .isEqualTo("COMPLETED_WITH_FAILURES");
        assertThat(AssetImageBatchService.batchStatus(0, 0, 0, 2, 0))
            .isEqualTo("FAILED");
    }

    @Test
    void skipsDependentsWhenThePrimaryCannotRun() {
        assertThat(AssetImageBatchService.resolveDependencyClassification(
            "WAITING_DEPENDENCY", "SKIPPED_MISSING_PROMPT"))
            .isEqualTo("SKIPPED_PRIMARY_UNAVAILABLE");
        assertThat(AssetImageBatchService.resolveDependencyClassification(
            "WAITING_DEPENDENCY", "PENDING"))
            .isEqualTo("WAITING_DEPENDENCY");
        assertThat(AssetImageBatchService.resolveDependencyClassification(
            "WAITING_DEPENDENCY", "SKIPPED_COMPLETED"))
            .isEqualTo("SKIPPED_PRIMARY_UNAVAILABLE");
    }

    @Test
    void retrySelectsOnlyFailedVariantsFromMixedBatch() {
        assertThat(AssetImageBatchService.failedVariantIds(List.of(
            new AssetImageBatchItemResponse(1L, 10L, 100L, "主形象", "PRIMARY", "SUCCEEDED", null, 1L, null),
            new AssetImageBatchItemResponse(2L, 10L, 101L, "礼服", "DIRECT", "FAILED", null, 2L, "超时"),
            new AssetImageBatchItemResponse(3L, 10L, 103L, "晚宴", "DEPENDENT", "SKIPPED", 2L, null, "主形象生成失败"),
            new AssetImageBatchItemResponse(4L, 11L, 102L, "主形象", "PRIMARY", "SKIPPED", null, null, "已生成完成")
        ))).containsExactly(101L, 103L);
    }
}
