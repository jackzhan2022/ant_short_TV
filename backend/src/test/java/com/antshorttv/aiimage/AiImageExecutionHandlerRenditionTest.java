package com.antshorttv.aiimage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.inOrder;

import com.antshorttv.accounting.AiUsageAccountingService;
import com.antshorttv.accounting.AiExecutionCostSummary;
import com.antshorttv.accounting.AiUsageCostStatus;
import com.antshorttv.ai.AiCapability;
import com.antshorttv.ai.AiImageResponse;
import com.antshorttv.ai.AiInvocationResult;
import com.antshorttv.ai.AiInvocationService;
import com.antshorttv.execution.AiExecutionAttemptMapper;
import com.antshorttv.execution.AiExecutionAttemptEntity;
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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class AiImageExecutionHandlerRenditionTest {

    @Test
    void resumesExecutionSuccessAfterAtomicDomainPublicationWithoutRepeatingSideEffects() {
        Fixture fixture = new Fixture();
        fixture.task.setStatus(AiImageTaskStatus.SUCCESS.name());
        fixture.task.setCompletedAt(java.time.LocalDateTime.now());
        AiImageResultEntity active = fixture.result(AiImageResultStatus.ACTIVE.name());
        when(fixture.results.selectByTask(33L)).thenReturn(List.of(active));

        AiExecutionHandlerResult resumed = fixture.handler.execute(fixture.context());

        assertThat(resumed).isEqualTo(new AiExecutionHandlerResult("AI_IMAGE_TASK", 33L));
        verify(fixture.invocations, never()).invokeImage(any());
        verify(fixture.accounting, never()).recordIfAbsent(any());
        verify(fixture.settlements, never()).finalizeOutcome(
            any(), any(), any(), any(), any(), any()
        );
        verify(fixture.renditions, never()).display(any());
        verify(fixture.variants, never()).generationSucceededIfClaimActive(
            any(), any(), any(), any(), any(), any(), any(), any()
        );
        verify(fixture.results, never()).activateForPublication(any(), any());
    }

    @Test
    void rejectsSuccessResumeWithPartialActiveResultSet() {
        Fixture fixture = new Fixture();
        fixture.task.setStatus(AiImageTaskStatus.SUCCESS.name());
        fixture.task.setCompletedAt(java.time.LocalDateTime.now());
        fixture.task.setImageCount(2);
        when(fixture.results.selectByTask(33L)).thenReturn(List.of(
            fixture.result(AiImageResultStatus.ACTIVE.name())
        ));

        assertThatThrownBy(() -> fixture.handler.execute(fixture.context()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("结果集合不一致");

        verify(fixture.invocations, never()).invokeImage(any());
        verify(fixture.renditions, never()).display(any());
        verify(fixture.results, never()).activateForPublication(any(), any());
    }

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
        )).thenReturn(fixture.settledReservation());

        assertThatThrownBy(() -> fixture.handler.execute(fixture.context()))
            .isInstanceOf(AiExecutionDeferredException.class);

        assertThat(fixture.task.getStatus()).isEqualTo("RENDERING");
        assertThat(fixture.task.getCompletedAt()).isNull();
        verify(fixture.results).updateById(org.mockito.ArgumentMatchers.argThat((AiImageResultEntity result) ->
            AiImageResultStatus.PROCESSING.name().equals(result.getStatus())
        ));
        verify(fixture.variants, never()).generationSucceededIfClaimActive(
            any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void retiresDurablyRegisteredMediaBeforeDeletingResultWhenStorageSubmissionFails() {
        Fixture fixture = new Fixture();
        when(fixture.results.selectByTask(33L)).thenReturn(List.of());
        doAnswer(invocation -> {
            ((AiImageResultEntity) invocation.getArgument(0)).setId(44L);
            return 1;
        }).when(fixture.results).insert(any(AiImageResultEntity.class));
        when(fixture.invocations.invokeImage(any())).thenReturn(fixture.providerResult(List.of(
            "data:image/png;base64,aW1hZ2U="
        )));
        when(fixture.storage.storeGenerated(any(), anyLong(), anyInt(), anyString()))
            .thenThrow(new IllegalStateException("CI submission unavailable after registration"));

        assertThatThrownBy(() -> fixture.handler.execute(fixture.context()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("CI submission unavailable after registration");

        InOrder cleanup = inOrder(fixture.renditions, fixture.results);
        cleanup.verify(fixture.renditions).retire(fixture.identity(fixture.result(
            AiImageResultStatus.PROCESSING.name()
        )));
        cleanup.verify(fixture.results).deleteById(44L);
        assertThat(fixture.task.getStatus()).isEqualTo(AiImageTaskStatus.RUNNING.name());
        verify(fixture.results, never()).activateForPublication(any(), any());
        verify(fixture.variants, never()).generationSucceededIfClaimActive(
            any(), any(), any(), any(), any(), any(), any(), any()
        );
        verify(fixture.variants, never()).generationFailedIfClaimActive(
            any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void publishesGeneratedResultOnlyAfterCallbackMadeDisplayReady() {
        Fixture fixture = new Fixture();
        fixture.task.setStatus("RENDERING");
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
        when(fixture.executions.lockActiveClaim(99L, "claim-1")).thenReturn(fixture.execution);
        when(fixture.results.activateForPublication(33L, 99L)).thenReturn(1);
        when(fixture.tasks.updateById(fixture.task)).thenReturn(1);

        AiExecutionHandlerResult completed = fixture.handler.execute(fixture.context());

        assertThat(completed).isEqualTo(new AiExecutionHandlerResult("AI_IMAGE_TASK", 33L));
        assertThat(result.getStatus()).isEqualTo(AiImageResultStatus.ACTIVE.name());
        assertThat(fixture.task.getStatus()).isEqualTo(AiImageTaskStatus.SUCCESS.name());
        assertThat(fixture.task.getCompletedAt()).isNotNull();
        verify(fixture.results).activateForPublication(33L, 99L);
        verify(fixture.variants).generationSucceededIfClaimActive(
            11L, 22L, 77L, 33L, 44L, result.getImageUrl(), 99L, "claim-1"
        );
        verify(fixture.invocations, never()).invokeImage(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void keepsPendingDisplayUnpublishedAndDefersCompletion() {
        Fixture fixture = new Fixture();
        fixture.task.setStatus("RENDERING");
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
        assertThat(fixture.task.getStatus()).isEqualTo("RENDERING");
        verify(fixture.variants, never()).generationSucceededIfClaimActive(
            any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void retriesFailedDisplayWithoutReinvokingProviderAndDefersCompletion() {
        Fixture fixture = new Fixture();
        fixture.task.setStatus("RENDERING");
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
        when(fixture.renditions.retryFailedDisplay(
            fixture.identity(result), "ai-image-result:44"
        )).thenReturn(new com.antshorttv.storage.RegisteredImageDisplay(
            result.getStoragePath(), result.getDisplayPath(), "SUBMITTED"
        ));

        assertThatThrownBy(() -> fixture.handler.execute(fixture.context()))
            .isInstanceOf(AiExecutionDeferredException.class);

        assertThat(result.getStatus()).isEqualTo(AiImageResultStatus.PROCESSING.name());
        verify(fixture.renditions).retryFailedDisplay(
            fixture.identity(result), "ai-image-result:44"
        );
        verify(fixture.variants, never()).generationSucceededIfClaimActive(
            any(), any(), any(), any(), any(), any(), any(), any()
        );
        verify(fixture.variants, never()).generationFailedIfClaimActive(
            any(), any(), any(), any(), any(), any(), any(), any()
        );
        verify(fixture.invocations, never()).invokeImage(any());
        verify(fixture.storage, never()).resource(result);
    }

    @Test
    void partialTwoImageResultSetIsDiscardedAndProviderStorageRestarts() {
        Fixture fixture = new Fixture();
        fixture.task.setImageCount(2);
        AiImageResultEntity partial = fixture.result(AiImageResultStatus.PROCESSING.name());
        when(fixture.results.selectByTask(33L)).thenReturn(List.of(partial));
        when(fixture.results.selectById(44L)).thenReturn(partial);
        java.util.concurrent.atomic.AtomicLong nextId = new java.util.concurrent.atomic.AtomicLong(45L);
        doAnswer(invocation -> {
            ((AiImageResultEntity) invocation.getArgument(0)).setId(nextId.getAndIncrement());
            return 1;
        }).when(fixture.results).insert(any(AiImageResultEntity.class));
        when(fixture.invocations.invokeImage(any())).thenReturn(fixture.providerResult(List.of(
            "data:image/png;base64,b25l", "data:image/png;base64,dHdv"
        )));
        when(fixture.storage.storeGenerated(any(), anyLong(), anyInt(), anyString())).thenAnswer(invocation -> {
            Long resultId = invocation.getArgument(1);
            String original = "materials/11/22/images/202609/" + resultId + "/result/original.png";
            return new StoredImage(
                original,
                original.replace("/original.png", "/derived/display.png"),
                original.replace("/original.png", "/derived/display.png"),
                "image/png", 800, 400, 321L
            );
        });
        fixture.settlementSucceeds();

        assertThatThrownBy(() -> fixture.handler.execute(fixture.context()))
            .isInstanceOf(AiExecutionDeferredException.class);

        InOrder cleanup = inOrder(fixture.renditions, fixture.results);
        cleanup.verify(fixture.renditions).retire(fixture.identity(partial));
        cleanup.verify(fixture.results).deleteById(44L);
        verify(fixture.invocations).invokeImage(any());
        verify(fixture.storage, times(2)).storeGenerated(any(), anyLong(), anyInt(), anyString());
        assertThat(fixture.task.getStatus()).isEqualTo("RENDERING");
        verify(fixture.variants, never()).generationSucceededIfClaimActive(
            any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void settlementFailureResumesWithoutReinvokingProvider() {
        Fixture fixture = new Fixture();
        fixture.task.setStyle("3D风格-定格动画");
        AtomicReference<AiImageResultEntity> created = new AtomicReference<>();
        when(fixture.results.selectByTask(33L)).thenReturn(List.of()).thenAnswer(
            invocation -> List.of(created.get())
        );
        doAnswer(invocation -> {
            AiImageResultEntity result = invocation.getArgument(0);
            result.setId(44L);
            created.set(result);
            return 1;
        }).when(fixture.results).insert(any(AiImageResultEntity.class));
        when(fixture.invocations.invokeImage(any())).thenReturn(fixture.providerResult(List.of(
            "data:image/png;base64,b25l"
        )));
        when(fixture.storage.storeGenerated(any(), anyLong(), anyInt(), anyString())).thenReturn(new StoredImage(
            "materials/11/22/images/202609/44/result/original.png",
            "materials/11/22/images/202609/44/result/derived/display.png",
            "materials/11/22/images/202609/44/result/derived/display.png",
            "image/png", 800, 400, 321L
        ));
        AiPointReservationEntity reservation = fixture.reservation();
        when(fixture.accounting.priceExecution(any(), any())).thenReturn(
            new AiExecutionCostSummary(99L, AiUsageCostStatus.PRICED, Map.of())
        );
        when(fixture.reservations.selectByExecutionId(99L)).thenReturn(reservation);
        when(fixture.settlements.finalizeOutcome(any(), any(), any(), any(), any(), any()))
            .thenThrow(new IllegalStateException("settlement unavailable"))
            .thenReturn(fixture.settledReservation());
        AiExecutionAttemptEntity providerAttempt = new AiExecutionAttemptEntity();
        providerAttempt.id = 100L;
        providerAttempt.aiCallLogId = 900L;
        providerAttempt.modelId = 8L;
        when(fixture.attempts.selectByExecutionId(99L)).thenReturn(List.of(providerAttempt));

        assertThatThrownBy(() -> fixture.handler.execute(fixture.context()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("settlement unavailable");
        assertThat(fixture.task.getStatus()).isEqualTo("SETTLING");

        assertThatThrownBy(() -> fixture.handler.execute(fixture.context()))
            .isInstanceOf(AiExecutionDeferredException.class);
        assertThat(fixture.task.getStatus()).isEqualTo("RENDERING");
        var request = org.mockito.ArgumentCaptor.forClass(com.antshorttv.ai.AiInvocationRequest.class);
        verify(fixture.invocations, times(1)).invokeImage(request.capture());
        assertThat(request.getValue().imageRequest().prompt())
            .isEqualTo("画面风格：3D风格-定格动画\n\n" + fixture.task.getPrompt());
        assertThat(request.getValue().requestSummary()).isEqualTo(request.getValue().imageRequest().prompt());
    }

    @Test
    void readyAndPendingRenditionsLeaveAllResultsNonActive() {
        Fixture fixture = new Fixture();
        fixture.task.setImageCount(2);
        fixture.task.setStatus("RENDERING");
        AiImageResultEntity ready = fixture.result(AiImageResultStatus.PROCESSING.name());
        AiImageResultEntity pending = fixture.result(AiImageResultStatus.PROCESSING.name());
        pending.setId(45L);
        pending.setStoragePath(ready.getStoragePath().replace("/44/", "/45/"));
        pending.setDisplayPath(ready.getDisplayPath().replace("/44/", "/45/"));
        when(fixture.results.selectByTask(33L)).thenReturn(List.of(ready, pending));
        when(fixture.renditions.display(fixture.identity(ready))).thenReturn(
            new RegisteredMediaObject(72L, fixture.identity(ready), "DISPLAY_IMAGE_SLIM",
                ready.getDisplayPath(), "READY")
        );
        when(fixture.renditions.display(fixture.identity(pending))).thenReturn(
            new RegisteredMediaObject(73L, fixture.identity(pending), "DISPLAY_IMAGE_SLIM",
                pending.getDisplayPath(), "PENDING")
        );

        assertThatThrownBy(() -> fixture.handler.execute(fixture.context()))
            .isInstanceOf(AiExecutionDeferredException.class);

        assertThat(ready.getStatus()).isEqualTo(AiImageResultStatus.PROCESSING.name());
        assertThat(pending.getStatus()).isEqualTo(AiImageResultStatus.PROCESSING.name());
        verify(fixture.results, never()).updateById(any(AiImageResultEntity.class));
        verify(fixture.variants, never()).generationSucceededIfClaimActive(
            any(), any(), any(), any(), any(), any(), any(), any()
        );
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
        private final AiImageRenditionPublicationService publication =
            new AiImageRenditionPublicationService(tasks, results, executions, variants);
        private final AiImageTaskEntity task = task();
        private final AiExecutionTaskEntity execution = execution();
        private final AiImageExecutionHandler handler;

        private Fixture() {
            when(tasks.selectById(33L)).thenReturn(task);
            when(executions.selectById(99L)).thenReturn(execution);
            handler = new AiImageExecutionHandler(
                tasks, results, storage, renditions, publication, invocations, executions, attempts,
                accounting, reservations, settlements, new ObjectMapper(), variants
            );
        }

        private AiExecutionContext context() {
            return new AiExecutionContext(
                execution,
                new AiExecutionClaim(99L, 100L, "claim-1", 1, "SUBMIT")
            );
        }

        private AiInvocationResult<AiImageResponse> providerResult(List<String> imageUrls) {
            return AiInvocationResult.success(
                AiCapability.IMAGE, "CHARACTER",
                new AiImageResponse(imageUrls, "provider-1", 25L, Map.of()),
                null, 900L, "provider-1", 8L, 7L, "provider",
                null, null, null, 25L
            );
        }

        private AiPointReservationEntity reservation() {
            AiPointReservationEntity reservation = new AiPointReservationEntity();
            reservation.id = 501L;
            reservation.status = "RESERVED";
            reservation.reservedPoints = BigDecimal.ONE;
            reservation.settledPoints = BigDecimal.ONE;
            reservation.releasedPoints = BigDecimal.ZERO;
            return reservation;
        }

        private AiPointReservationEntity settledReservation() {
            AiPointReservationEntity reservation = reservation();
            reservation.status = "SETTLED";
            return reservation;
        }

        private void settlementSucceeds() {
            AiPointReservationEntity reservation = reservation();
            when(accounting.priceExecution(any(), any())).thenReturn(
                new AiExecutionCostSummary(99L, AiUsageCostStatus.PRICED, Map.of())
            );
            when(reservations.selectByExecutionId(99L)).thenReturn(reservation);
            when(settlements.finalizeOutcome(any(), any(), any(), any(), any(), any()))
                .thenReturn(settledReservation());
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
            result.setStoragePath(
                "materials/11/22/images/202609/44/result-44/original.jpg"
            );
            result.setDisplayPath(
                "materials/11/22/images/202609/44/result-44/derived/display.jpg"
            );
            result.setMimeType("image/jpeg");
            result.setFileSize(321L);
            result.setWidth(800);
            result.setHeight(400);
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
            task.setTaskType("CHARACTER");
            task.setPrompt("portrait");
            task.setAspectRatio("1:1");
            task.setQuality("STANDARD");
            task.setModelId(8L);
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
