package com.antshorttv.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;

class AiModelBillingResolverQueryTest {
    @Test void loadsEachComponentTableOnceAndKeepsTheMostSpecificPricesForEveryMetric() {
        var costs=mock(AiModelPriceVersionMapper.class);
        var costComponents=mock(AiModelPriceComponentMapper.class);
        var points=mock(AiModelPointPriceVersionMapper.class);
        var pointComponents=mock(AiModelPointPriceComponentMapper.class);
        var costVersion=new AiModelPriceVersionEntity();costVersion.id=10L;
        var pointVersion=new AiModelPointPriceVersionEntity();pointVersion.id=20L;
        when(costs.selectEffective(eq(9L),any())).thenReturn(costVersion);
        when(points.selectEffective(eq(9L),any())).thenReturn(pointVersion);
        List<AiModelPriceComponentEntity> costRows=new ArrayList<>();
        List<AiModelPointPriceComponentEntity> pointRows=new ArrayList<>();
        var metrics=Set.of(AiUsageMetric.CALL,AiUsageMetric.IMAGE,AiUsageMetric.INPUT_IMAGE);
        for(var metric:metrics) {
            var cost=new AiModelPriceComponentEntity();cost.metric=metric.name();cost.dimensionsJson="{}";
            var point=new AiModelPointPriceComponentEntity();point.metric=metric.name();point.dimensionsJson="{}";
            costRows.add(cost);pointRows.add(point);
        }
        var specific=new AiModelPointPriceComponentEntity();specific.metric="IMAGE";specific.dimensionsJson="{\"quality\":\"high\"}";
        pointRows.add(specific);
        when(costComponents.selectByVersion(10L)).thenReturn(costRows);
        when(pointComponents.selectByVersion(20L)).thenReturn(pointRows);
        var result=new AiModelBillingResolver(costs,costComponents,points,pointComponents)
            .requireComplete(9L,metrics,Map.of("quality","high"),LocalDateTime.now());
        assertThat(result.costComponents()).containsOnlyKeys(metrics);
        assertThat(result.pointComponents()).containsOnlyKeys(metrics);
        assertThat(result.pointComponents().get(AiUsageMetric.IMAGE)).isSameAs(specific);
        verify(costComponents,times(1)).selectByVersion(10L);
        verify(pointComponents,times(1)).selectByVersion(20L);
        verifyNoMoreInteractions(costComponents,pointComponents);
    }
}
