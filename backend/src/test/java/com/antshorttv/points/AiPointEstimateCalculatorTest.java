package com.antshorttv.points;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.antshorttv.accounting.*;
import com.antshorttv.commercial.CommercialEntitlementResolver;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiPointEstimateCalculatorTest {
    @Test
    void appliesPublishedModelPriceAndTheSameCommercialDiscountAsExecution() {
        var billing = mock(AiModelBillingResolver.class);
        var settlements = mock(AiPointSettlementService.class);
        var entitlements = mock(CommercialEntitlementResolver.class);
        when(billing.requireComplete(eq(9L), anySet(), anyMap(), any()))
            .thenReturn(new ModelBillingSnapshot(10L, 20L, Map.of(), Map.of()));
        var usage = Map.of(AiUsageMetric.IMAGE, new BigDecimal("4"));
        var dimensions = Map.of("quality", "STANDARD", "aspectRatio", "16:9");
        when(settlements.calculateModelPoints(20L, usage, dimensions)).thenReturn(new BigDecimal("31.25"));
        when(entitlements.resolveGlobalDiscount(eq(7L), any())).thenReturn(
            new CommercialEntitlementResolver.CommercialDiscountSnapshot(1L, 2L, new BigDecimal("0.8")));
        var result = new AiPointEstimateCalculator(billing, settlements, entitlements).calculate(7L,
            new AiPointEstimatePlan("四张图片", 9L, usage, dimensions, false));
        assertThat(result.points()).isEqualByComparingTo("25");
        assertThat(result.pointPriceVersionId()).isEqualTo(20L);
        verify(settlements).calculateModelPoints(20L, usage, dimensions);
        verifyNoMoreInteractions(settlements);
    }

    @Test
    void retriesUseFrozenPriceAndDiscountInsteadOfNewPublishedPrices() {
        var billing = mock(AiModelBillingResolver.class);
        var settlements = mock(AiPointSettlementService.class);
        var entitlements = mock(CommercialEntitlementResolver.class);
        var reservation = new AiPointReservationEntity();
        reservation.pointPriceVersionId=3L; reservation.discountRate=new BigDecimal("0.5");
        reservation.trialEligible=false;
        var usage=Map.of(AiUsageMetric.CALL, BigDecimal.ONE);
        when(settlements.calculateModelPoints(3L,usage,Map.of())).thenReturn(new BigDecimal("0.00000002"));
        var result=new AiPointEstimateCalculator(billing,settlements,entitlements).calculate(7L,
            new AiPointEstimatePlan("重试",9L,usage,Map.of(),true).frozen(reservation));
        assertThat(result.points()).isEqualByComparingTo("0.00000001");
        assertThat(result.trialEligible()).isFalse();
        verifyNoInteractions(billing,entitlements);
    }

    @Test
    void workbenchEstimateAppliesTwentyPercentAfterTheFrozenPackageDiscount() {
        var billing = mock(AiModelBillingResolver.class);
        var settlements = mock(AiPointSettlementService.class);
        var entitlements = mock(CommercialEntitlementResolver.class);
        var reservation = new AiPointReservationEntity();
        reservation.pointPriceVersionId = 3L; reservation.discountRate = new BigDecimal("0.5"); reservation.trialEligible = true;
        var usage = Map.of(AiUsageMetric.CALL, BigDecimal.ONE);
        when(settlements.calculateModelPoints(3L, usage, Map.of())).thenReturn(new BigDecimal("200"));
        var plan = new AiPointEstimatePlan("剧本分析", 9L, usage, Map.of(), true).workbench(50L).frozen(reservation);
        var result = new AiPointEstimateCalculator(billing, settlements, entitlements).calculate(7L, plan);
        assertThat(result.points()).isEqualByComparingTo("20");
        assertThat(plan.projectId()).isEqualTo(50L);
        assertThat(plan.workbench()).isTrue();
        verifyNoInteractions(billing, entitlements);
    }

    @Test
    void aMissingPriceCannotBePresentedAsAFreeOperation() {
        var billing=mock(AiModelBillingResolver.class);
        var settlements=mock(AiPointSettlementService.class);
        var entitlements=mock(CommercialEntitlementResolver.class);
        when(billing.requireComplete(any(),anySet(),anyMap(),any())).thenThrow(new ModelBillingMissingException("missing"));
        assertThatThrownBy(()->new AiPointEstimateCalculator(billing,settlements,entitlements).calculate(7L,
            new AiPointEstimatePlan("生成",9L,Map.of(AiUsageMetric.CALL,BigDecimal.ONE),Map.of(),false)))
            .isInstanceOf(ModelBillingMissingException.class);
        verifyNoInteractions(settlements);
    }
}
