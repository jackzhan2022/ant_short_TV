package com.antshorttv.ai;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.antshorttv.accounting.AiUsageMetric;
import com.antshorttv.points.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiTextBudgetServiceTest {
    private final AiPointReservationMapper reservations = mock(AiPointReservationMapper.class);
    private final AiPointSettlementService settlements = mock(AiPointSettlementService.class);
    private final com.antshorttv.accounting.AiModelPointPriceVersionMapper prices = mock(com.antshorttv.accounting.AiModelPointPriceVersionMapper.class);
    private final AiTextBudgetService budget = new AiTextBudgetService(reservations, settlements, new ObjectMapper(), prices);
    private final AiContext context = new AiContext(1L,2L,3L,4L,5L,"script_generate","trace",6L,7L,1,"RUN","same-provider-key");

    private void tokenReservation() {
        var reservation = new AiPointReservationEntity(); reservation.id=8L;reservation.tenantId=1L;reservation.pointPriceVersionId=9L;
        var version = new com.antshorttv.accounting.AiModelPointPriceVersionEntity();version.modelId=5L;
        when(prices.selectById(9L)).thenReturn(version);
        when(reservations.selectByExecutionId(6L)).thenReturn(reservation);
        when(settlements.usesTokenPricing(8L)).thenReturn(true);
        when(settlements.calculateModelPoints(eq(9L),anyMap(),anyMap())).thenReturn(BigDecimal.ONE);
    }

    @Test
    void reservesUtf8InputBoundAndFullOutputBeforeEachInvocation() {
        tokenReservation();
        var request = new AiTextRequest("系统", "中文输入", 0.2, 8192, null);
        budget.authorize(context, request);
        budget.authorize(context, request);
        ArgumentCaptor<Map<AiUsageMetric,BigDecimal>> usage = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(settlements,times(2)).authorizeUsage(eq(8L),usage.capture(),keys.capture());
        assertThat(usage.getValue().get(AiUsageMetric.INPUT_TOKEN)).isGreaterThanOrEqualTo(BigDecimal.valueOf(18));
        assertThat(usage.getValue()).containsEntry(AiUsageMetric.OUTPUT_TOKEN,BigDecimal.valueOf(8192));
        assertThat(keys.getAllValues()).doesNotHaveDuplicates();
    }

    @Test
    void rejectsUnboundedOutputBeforeCallingProvider() {
        tokenReservation();
        assertThatThrownBy(() -> budget.authorize(context,new AiTextRequest(null,"prompt",0.2,null,null)))
            .isInstanceOf(com.antshorttv.common.BusinessException.class);
        verify(settlements,never()).authorizeUsage(anyLong(),anyMap(),anyString());
    }

    @Test
    void tokenBillingDisablesHiddenHttpRetriesSoEachPaidAttemptHasItsOwnLog() {
        tokenReservation();
        var request = new AiTextRequest(null,"prompt",0.2,1024,null,false,null,60,3);
        assertThat(budget.authorize(context,request).retryCount()).isZero();
    }

    @Test
    void rejectsModelThatDoesNotMatchFrozenPrice() {
        tokenReservation();
        assertThatThrownBy(() -> budget.authorize(context.withModelId(99L),new AiTextRequest(null,"prompt",0.2,1024,null)))
            .isInstanceOf(com.antshorttv.common.BusinessException.class);
        verify(settlements,never()).authorizeUsage(anyLong(),anyMap(),anyString());
    }

    @Test
    void videoUrlReservesFullContextRatherThanUrlLength() {
        tokenReservation();
        budget.authorizeVideoUnderstanding(context,new com.antshorttv.video.VideoUnderstandingRequest("https://example.test/video.mp4","analyze"));
        ArgumentCaptor<Map<AiUsageMetric,BigDecimal>> usage=ArgumentCaptor.forClass(Map.class);
        verify(settlements).authorizeUsage(eq(8L),usage.capture(),anyString());
        assertThat(usage.getValue()).containsEntry(AiUsageMetric.INPUT_TOKEN,BigDecimal.valueOf(1_000_000))
            .containsEntry(AiUsageMetric.OUTPUT_TOKEN,BigDecimal.valueOf(8192));
    }

    @Test
    void inputBudgetUsesMostExpensiveCacheCategory() {
        tokenReservation();
        when(settlements.calculateModelPoints(eq(9L),eq(Map.of(AiUsageMetric.CACHE_WRITE_TOKEN,BigDecimal.ONE)),anyMap()))
            .thenReturn(BigDecimal.TEN);
        budget.authorize(context,new AiTextRequest(null,"prompt",0.2,1024,null));
        ArgumentCaptor<Map<AiUsageMetric,BigDecimal>> usage=ArgumentCaptor.forClass(Map.class);
        verify(settlements).authorizeUsage(eq(8L),usage.capture(),anyString());
        assertThat(usage.getValue().get(AiUsageMetric.CACHE_WRITE_TOKEN)).isNotNull().isPositive();
        assertThat(usage.getValue().get(AiUsageMetric.INPUT_TOKEN)).isZero();
    }

    @Test
    void legacyCallPricingDoesNotRequireTokenBudget() {
        var reservation = new AiPointReservationEntity();reservation.id=8L;reservation.tenantId=1L;
        when(reservations.selectByExecutionId(6L)).thenReturn(reservation);
        budget.authorize(context,new AiTextRequest(null,"prompt",0.2,null,null));
        verify(settlements,never()).authorizeUsage(anyLong(),anyMap(),anyString());
    }
}
