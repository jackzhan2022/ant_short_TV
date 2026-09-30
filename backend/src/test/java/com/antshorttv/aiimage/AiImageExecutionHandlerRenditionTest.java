package com.antshorttv.aiimage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.accounting.AiUsageAccountingService;
import com.antshorttv.accounting.AiExecutionCostSummary;
import com.antshorttv.accounting.AiUsageCostStatus;
import com.antshorttv.ai.AiCapability;
import com.antshorttv.ai.AiImageResponse;
import com.antshorttv.ai.AiInvocationResult;
import com.antshorttv.ai.AiInvocationService;
import com.antshorttv.execution.AiExecutionAttemptMapper;
import com.antshorttv.execution.AiExecutionClaim;
import com.antshorttv.execution.AiExecutionContext;
import com.antshorttv.execution.AiExecutionDeferredException;
import com.antshorttv.execution.AiExecutionHandlerResult;
import com.antshorttv.execution.AiExecutionStatus;
import com.antshorttv.execution.AiExecutionTaskEntity;
import com.antshorttv.execution.AiExecutionTaskMapper;
import com.antshorttv.points.AiPointReservationMapper;
import com.antshorttv.points.AiPointReservationEntity;
import com.antshorttv.points.AiPointSettlementService;
import com.antshorttv.script.AssetVisualVariantService;
import com.antshorttv.storage.ImageDisplayRenditionService;
import com.antshorttv.storage.MediaObjectIdentity;
import com.antshorttv.storage.RegisteredMediaObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiImageExecutionHandlerRenditionTest {

    @Test
    void defersCompletionAfterSubmittingGeneratedImageForDisplayProcessing() {
        Fixture fixture = new Fixture();
        fixture.task.setTaskType("CHARACTER");
        fixture.task.setPrompt("portrait");
        fixture.task.setAspectRatio("1:1");
        fixture.task.setQuality("STANDARD");
        when(fixture.results.selectByTask(33L)).thenReturn(List.of());
        doAnswer(invocation -> {
            ((AiImageResultEntity) invocation.getArgument(0)).setId(44L);
            return 1;
        }).when(fixture.results).insert(any(AiImageResultEntity.class));
        when(fixture.invocations.invokeImage(any())).thenReturn(AiInvocationResult.success(
            AiCapability.IMAGE,
            "CHARACTER",
            new AiImageResponse(List.of("data:image/png;base64,aW1hZ2U="), "provider-1", 25L, Map.of()),
            null,
            900L,
            "provider-1",
            8L,
            7L,
            "provider",
            null,
            null,
            null,
            25L
        ));
        when(fixture.storage.storeGenerated(fixture.task, 44L, 1,
            "data:image/png;base64,aW1hZ2U="
        )).thenReturn(new StoredImage(
            "materials/11/22/images/202609/44/result-44/original.png",
            "materials/11/22/images/202609/44/result-44/derived/display.png",
            "materials/11/22/images/202609/44/result-44/derived/display.png",
            "image/png",
            800,
            400,
            321L
        ));
        when(fixture.accounting.priceExecution(any(), any())).thenReturn(
            new AiExecutionCostSummary(99L, AiUsageCostStatus.PRICED, Map.of())
        );
        AiPointReservationEntity reservation = new AiPointReservationEntity();
        reservation.id = 501L;
        reservation.status = "RESERVED";
        reservation.reservedPoints = BigDecimal.ONE;
        reservation.settledPoints = BigDecimal.ONE;
        reservation.releasedPoints = BigDecimal.ZERO;
        when(fixture.reservations.selectByExecutionId(99L)).thenReturn(reservation);
        when(fixture.settlements.finalizeOutcome(
            any(), any(), any(), any(), any(), any()
        )).thenReturn(reservation);

        assertThatThrownBy(() -> fixture.handler.execute(fixture.context()))
            .isInstanceOf(AiExecutionDeferredException.class);

        assertThat(fixture.task.getStatus()).isEqualTo(AiImageTaskStatus.RUNNING.name());
        assertThat(fixture.task.getCompletedAt()).isNull();
        verify(fixture.results).updateById(org.mockito.ArgumentMatchers.argThat((AiImageResultEntity result) ->
            AiImageResultStatus.PROCESSING.name().equals(result.getStatus())
        ));
        verify(fixture.variants, never()).generationSucceededIfClaimActive(
            any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void publishesGeneratedResultOnlyAfterCallbackMadeDisplayReady() {
        Fixture fixture = new Fixture();
        AiImageResultEntity result = fixture.result(AiImageResultStatus.PROCESSING.name());
        when(fixture.results.selectByTask(33L)).thenReturn(List.of(result));
        when(fixture.renditions.display(fixture.identity(result))).thenReturn(
            new RegisteredMediaObject(
                72L,
                fixture.identity(result),
                "DISPLAY_IMAGE_SLIM",
                result.getDisplayPath(),
                "READY"
            )
        );
        when(fixture.variants.generationSucceededIfClaimActive(
            11L, 22L, 77L, 33L, 44L, result.getImageUrl(), 99L, "claim-1"
        )).thenReturn(true);

        AiExecutionHandlerResult completed = fixture.handler.execute(fixture.context());

        assertThat(completed).isEqualTo(new AiExecutionHandlerResult("AI_IMAGE_TASK", 33L));
        assertThat(result.getStatus()).isEqualTo(AiImageResultStatus.ACTIVE.name());
        assertThat(fixture.task.getStatus()).isEqualTo(AiImageTaskStatus.SUCCESS.name());
        assertThat(fixture.task.getCompletedAt()).isNotNull();
        verify(fixture.results).updateById(result);
        verify(fixture.variants).generationSucceededIfClaimActive(
            11L, 22L, 77L, 33L, 44L, result.getImageUrl(), 99L, "claim-1"
        );
        verify(fixture.invocations, never()).invokeImage(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void keepsPendingDisplayUnpublishedAndDefersCompletion() {
        Fixture fixture = new Fixture();
        AiImageResultEntity result = fixture.result(AiImageResultStatus.PROCESSING.name());
        when(fixture.results.selectByTask(33L)).thenReturn(List.of(result));
        when(fixture.renditions.display(fixture.identity(result))).thenReturn(
            new RegisteredMediaObject(
                72L,
                fixture.identity(result),
                "DISPLAY_IMAGE_SLIM",
                result.getDisplayPath(),
                "PENDING"
            )
        );

        assertThatThrownBy(() -> fixture.handler.execute(fixture.context()))
            .isInstanceOf(AiExecutionDeferredException.class);

        assertThat(result.getStatus()).isEqualTo(AiImageResultStatus.PROCESSING.name());
        assertThat(fixture.task.getStatus()).isEqualTo(AiImageTaskStatus.RUNNING.name());
        verify(fixture.variants, never()).generationSucceededIfClaimActive(
            any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void failedDisplayRemainsFailedAndRetryableWithoutPublishingOriginal() {
        Fixture fixture = new Fixture();
        AiImageResultEntity result = fixture.result(AiImageResultStatus.PROCESSING.name());
        when(fixture.results.selectByTask(33L)).thenReturn(List.of(result));
        when(fixture.renditions.display(fixture.identity(result))).thenReturn(
            new RegisteredMediaObject(
                72L,
                fixture.identity(result),
                "DISPLAY_IMAGE_SLIM",
                result.getDisplayPath(),
                "FAILED"
            )
        );
        when(fixture.tasks.markFailedIfClaimActive(
            33L, 99L, "claim-1", "AI 图片展示版本处理失败。", 900L
        )).thenReturn(1);
        when(fixture.variants.generationFailedIfClaimActive(
            11L, 22L, 77L, 33L, "IMAGE_DISPLAY_RENDITION_FAILED",
            "AI 图片展示版本处理失败。", 99L, "claim-1"
        )).thenReturn(true);

        assertThatThrownBy(() -> fixture.handler.execute(fixture.context()))
            .isInstanceOf(IllegalStateException.class)
            .isNotInstanceOf(AiExecutionDeferredException.class)
            .hasMessage("AI 图片展示版本处理失败。");

        assertThat(result.getStatus()).isEqualTo(AiImageResultStatus.FAILED.name());
        verify(fixture.results).updateById(result);
        verify(fixture.tasks).markFailedIfClaimActive(
            33L, 99L, "claim-1", "AI 图片展示版本处理失败。", 900L
        );
        verify(fixture.variants).generationFailedIfClaimActive(
            11L, 22L, 77L, 33L, "IMAGE_DISPLAY_RENDITION_FAILED",
            "AI 图片展示版本处理失败。", 99L, "claim-1"
        );
        verify(fixture.variants, never()).generationSucceededIfClaimActive(
            any(), any(), any(), any(), any(), any(), any(), any()
        );
        verify(fixture.storage, never()).resource(result);
    }

    private static final class Fixture {
        private final AiImageTaskMapper tasks = mock(AiImageTaskMapper.class);
        private final AiImageResultMapper results = mock(AiImageResultMapper.class);
        private final AiImageStorageService storage = mock(AiImageStorageService.class);
        private final ImageDisplayRenditionService renditions = mock(ImageDisplayRenditionService.class);
        private final AiInvocationService invocations = mock(AiInvocationService.class);
        private final AiExecutionTaskMapper executions = mock(AiExecutionTaskMapper.class);
        private final AiExecutionAttemptMapper attempts = mock(AiExecutionAttemptMapper.class);
        private final AiUsageAccountingService accounting = mock(AiUsageAccountingService.class);
        private final AiPointReservationMapper reservations = mock(AiPointReservationMapper.class);
        private final AiPointSettlementService settlements = mock(AiPointSettlementService.class);
        private final AssetVisualVariantService variants = mock(AssetVisualVariantService.class);
        private final AiImageTaskEntity task = task();
        private final AiExecutionTaskEntity execution = execution();
        private final AiImageExecutionHandler handler;

        private Fixture() {
            when(tasks.selectById(33L)).thenReturn(task);
            when(executions.selectById(99L)).thenReturn(execution);
            handler = new AiImageExecutionHandler(
                tasks, results, storage, renditions, invocations, executions, attempts, accounting,
                reservations, settlements, new ObjectMapper(), variants
            );
        }

        private AiExecutionContext context() {
            return new AiExecutionContext(
                execution,
                new AiExecutionClaim(99L, 100L, "claim-1", 1, "SUBMIT")
            );
        }

        private AiImageResultEntity result(String status) {
            AiImageResultEntity result = new AiImageResultEntity();
            result.setId(44L);
            result.setTenantId(11L);
            result.setProjectId(22L);
            result.setTaskId(33L);
            result.setExecutionId(99L);
            result.setTargetType("VISUAL_VARIANT");
            result.setTargetId(77L);
            result.setImageUrl("/api/projects/22/ai-image-results/44/display");
            result.setDisplayPath(
                "materials/11/22/images/202609/44/result-44/derived/display.jpg"
            );
            result.setStatus(status);
            return result;
        }

        private MediaObjectIdentity identity(AiImageResultEntity result) {
            return new MediaObjectIdentity(
                result.getTenantId(), result.getProjectId(), "AI_IMAGE_RESULT", result.getId(),
                "result-" + result.getId()
            );
        }

        private static AiImageTaskEntity task() {
            AiImageTaskEntity task = new AiImageTaskEntity();
            task.setId(33L);
            task.setTenantId(11L);
            task.setProjectId(22L);
            task.setTargetType("VISUAL_VARIANT");
            task.setTargetId(77L);
            task.setImageCount(1);
            task.setExecutionId(99L);
            task.setAiCallLogId(900L);
            task.setStatus(AiImageTaskStatus.RUNNING.name());
            return task;
        }

        private static AiExecutionTaskEntity execution() {
            AiExecutionTaskEntity execution = new AiExecutionTaskEntity();
            execution.id = 99L;
            execution.tenantId = 11L;
            execution.projectId = 22L;
            execution.businessId = 33L;
            execution.status = AiExecutionStatus.RUNNING.name();
            execution.claimToken = "claim-1";
            execution.executionVersion = 1;
            execution.phase = "SUBMIT";
            execution.userId = 66L;
            execution.requestedModelId = 8L;
            execution.traceId = "trace-1";
            return execution;
        }
    }
}
