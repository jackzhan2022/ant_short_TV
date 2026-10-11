package com.antshorttv.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.antshorttv.accounting.*;
import com.antshorttv.ai.*;
import com.antshorttv.execution.*;
import com.antshorttv.points.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class AiVideoBillingTest {
    @Test
    void recordsReportedCompletionTokensUsingFrozenTokenAuthorization() {
        Fixture f = new Fixture("{\"OUTPUT_TOKEN\":1000000}");
        ReflectionTestUtils.invokeMethod(f.service, "recordUsageAndCost", f.task, 3L, f.invocation(12345L));
        ArgumentCaptor<AiUsageCommand> usage = ArgumentCaptor.forClass(AiUsageCommand.class);
        verify(f.accounting, atLeastOnce()).recordIfAbsent(usage.capture());
        assertThat(usage.getAllValues()).singleElement().satisfies(line -> {
            assertThat(line.metric()).isEqualTo(AiUsageMetric.OUTPUT_TOKEN);
            assertThat(line.quantity()).isEqualByComparingTo("12345");
            assertThat(line.source()).isEqualTo(AiUsageSource.PROVIDER_REPORTED);
            assertThat(line.dimensions()).containsEntry("resolution", "720p").containsEntry("referenceVideo", "true");
        });
    }

    @Test
    void missingProviderTokensKeepsPointsForReviewInsteadOfFreeSuccess() {
        Fixture f = new Fixture("{\"OUTPUT_TOKEN\":1000000}");
        f.task.providerResultMetadataJson = "{}";
        ReflectionTestUtils.invokeMethod(f.service, "settleExecution", f.task,
            AiSettlementOutcome.SUCCESS, 3L, 4L, "SUCCEEDED");
        verify(f.settlements).finalizeOutcome(eq(2L), eq(AiSettlementOutcome.TRANSPORT_UNKNOWN),
            eq(Map.of()), eq(3L), eq(4L), any());
    }

    @Test
    void legacyFrozenSecondsContinueSettlingSeconds() {
        Fixture f = new Fixture("{\"CALL\":1,\"VIDEO_SECOND\":5}");
        ReflectionTestUtils.invokeMethod(f.service, "settleExecution", f.task,
            AiSettlementOutcome.SUCCESS, 3L, 4L, "SUCCEEDED");
        verify(f.settlements).finalizeOutcome(eq(2L), eq(AiSettlementOutcome.SUCCESS),
            eq(Map.of(AiUsageMetric.CALL, BigDecimal.ONE, AiUsageMetric.VIDEO_SECOND, BigDecimal.valueOf(5))),
            eq(3L), eq(4L), any());
    }

    @Test
    void reservationBudgetIncludesReferenceVideoDurationAndAutomaticOutputDuration() {
        Fixture f = new Fixture("{}");
        f.task.providerCode = "VOLCENGINE_ARK";
        f.task.durationSeconds = -1;
        f.task.requestSnapshotJson = "{\"billingDurationSeconds\":15,\"references\":[{\"reference\":{\"mediaType\":\"VIDEO\"},\"durationSeconds\":10,\"width\":1280,\"height\":720,\"fps\":30}]}";
        Map<AiUsageMetric, BigDecimal> budget = ReflectionTestUtils.invokeMethod(f.service, "videoAuthorizedUsage", f.task, 15);
        assertThat(budget).containsKey(AiUsageMetric.OUTPUT_TOKEN);
        assertThat(budget.get(AiUsageMetric.OUTPUT_TOKEN)).isGreaterThanOrEqualTo(BigDecimal.valueOf(1280L * 720 * 24 * 25 / 1024));
        assertThat(budget).doesNotContainKey(AiUsageMetric.VIDEO_SECOND);
    }

    @Test
    void successSettlesExactReportedTokensInsteadOfRequestedSeconds() {
        Fixture f = new Fixture("{\"OUTPUT_TOKEN\":1000000}");
        f.task.providerResultMetadataJson = "{\"completionTokens\":12345}";
        ReflectionTestUtils.invokeMethod(f.service, "settleExecution", f.task,
            AiSettlementOutcome.SUCCESS, 3L, 4L, "SUCCEEDED");
        verify(f.settlements).finalizeOutcome(eq(2L), eq(AiSettlementOutcome.SUCCESS),
            eq(Map.of(AiUsageMetric.OUTPUT_TOKEN, BigDecimal.valueOf(12345))), eq(3L), eq(4L), any());
    }

    @Test
    void zeroProviderTokensAreNotBillableSuccess() {
        Fixture f = new Fixture("{\"OUTPUT_TOKEN\":1000000}");
        f.task.providerResultMetadataJson = "{\"completionTokens\":0}";
        ReflectionTestUtils.invokeMethod(f.service, "settleExecution", f.task,
            AiSettlementOutcome.SUCCESS, 3L, 4L, "SUCCEEDED");
        verify(f.settlements).finalizeOutcome(eq(2L), eq(AiSettlementOutcome.TRANSPORT_UNKNOWN),
            eq(Map.of()), eq(3L), eq(4L), any());
    }

    @Test
    void downloadRetryReusesFirstProviderCompletionBillingIdentity() {
        Fixture f = new Fixture("{\"OUTPUT_TOKEN\":1000000}");
        ReflectionTestUtils.invokeMethod(f.service, "rememberProviderBilling", f.task, 3L, f.invocation(12345L));
        ReflectionTestUtils.invokeMethod(f.service, "rememberProviderBilling", f.task, 9L, f.invocation(12345L));
        ReflectionTestUtils.invokeMethod(f.service, "recordUsageAndCost", f.task, 9L, f.invocation(12345L));
        verify(f.accounting).recordIfAbsent(org.mockito.ArgumentMatchers.argThat(line ->
            line.context().attemptId().equals(3L) && line.context().aiCallLogId().equals(4L)));
    }

    private static final class Fixture {
        final AiVideoTaskService service = mock(AiVideoTaskService.class, CALLS_REAL_METHODS);
        final AiUsageAccountingService accounting = mock(AiUsageAccountingService.class);
        final AiPointSettlementService settlements = mock(AiPointSettlementService.class);
        final AiVideoTaskEntity task = new AiVideoTaskEntity();
        Fixture(String authorization) {
            AiPointReservationMapper reservations = mock(AiPointReservationMapper.class);
            AiExecutionTaskMapper executions = mock(AiExecutionTaskMapper.class);
            ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
            ReflectionTestUtils.setField(service, "usageAccountingService", accounting);
            ReflectionTestUtils.setField(service, "pointSettlementService", settlements);
            ReflectionTestUtils.setField(service, "reservationMapper", reservations);
            ReflectionTestUtils.setField(service, "executionTaskMapper", executions);
            ReflectionTestUtils.setField(service, "aiVideoTaskMapper", mock(AiVideoTaskMapper.class));
            task.id = 10L; task.executionId = 1L; task.tenantId = 11L; task.durationSeconds = 5;
            task.resolution = "720p"; task.aspectRatio = "16:9";
            task.requestSnapshotJson = "{\"references\":[{\"reference\":{\"mediaType\":\"VIDEO\"}}]}";
            AiPointReservationEntity reservation = new AiPointReservationEntity();
            reservation.id = 2L; reservation.status = "RESERVED"; reservation.authorizedUsageJson = authorization;
            when(reservations.selectByExecutionId(1L)).thenReturn(reservation);
            when(executions.selectById(1L)).thenReturn(new AiExecutionTaskEntity());
            when(accounting.priceExecution(any(), any())).thenReturn(new AiExecutionCostSummary(1L, AiUsageCostStatus.PRICED, Map.of()));
        }
        AiInvocationResult<AiVideoProviderAdapter.VideoResult> invocation(Long tokens) {
            return AiInvocationResult.success(AiCapability.VIDEO, "ai_video_generate",
                new AiVideoProviderAdapter.VideoResult("SUCCEEDED", "https://example.test/video.mp4", null,
                    "{\"completionTokens\":" + tokens + "}", BigDecimal.valueOf(5), "720p", "16:9", null,
                    BigDecimal.valueOf(24), "default", true, tokens, tokens),
                null, 4L, "request", 5L, 6L, "VOLCENGINE_ARK", null, null, null, 10L);
        }
    }
}
