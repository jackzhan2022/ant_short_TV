package com.antshorttv.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class AiModelBillingResolverTest {
    private static final LocalDateTime AT = LocalDateTime.of(2026, 8, 26, 12, 0);

    @Autowired private AiModelBillingResolver resolver;
    @Autowired private AiModelPriceService costService;
    @Autowired private AiModelPointPriceService pointService;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("delete from ai_model_point_price_component");
        jdbc.update("delete from ai_model_point_price_version");
        jdbc.update("delete from ai_model_price_component");
        jdbc.update("delete from ai_model_price_version");
        jdbc.update("delete from ai_model_price_version_sequence");
    }

    @Test
    void resolvesMatchingCostAndPointComponents() {
        publishCost(701L, AiUsageMetric.IMAGE, Map.of("size", "1024"));
        publishPoint(701L, AiUsageMetric.IMAGE, Map.of("size", "1024"));

        ModelBillingSnapshot snapshot = resolver.requireComplete(
            701L, Set.of(AiUsageMetric.IMAGE), Map.of("size", "1024"), AT
        );

        assertThat(snapshot.costVersionId()).isNotNull();
        assertThat(snapshot.pointVersionId()).isNotNull();
        assertThat(snapshot.costComponents()).containsKey(AiUsageMetric.IMAGE);
        assertThat(snapshot.pointComponents()).containsKey(AiUsageMetric.IMAGE);
    }

    @Test
    void rejectsWhenEitherPriceIsMissing() {
        publishCost(702L, AiUsageMetric.CALL, Map.of());

        assertThatThrownBy(() -> resolver.requireComplete(
            702L, Set.of(AiUsageMetric.CALL), Map.of(), AT
        )).isInstanceOf(ModelBillingMissingException.class)
            .hasMessageContaining("point price");
    }

    @Test
    void rejectsAnEmptyRequiredMetricSet() {
        publishCost(703L, AiUsageMetric.CALL, Map.of());
        publishPoint(703L, AiUsageMetric.CALL, Map.of());

        assertThatThrownBy(() -> resolver.requireComplete(703L, Set.of(), Map.of(), AT))
            .isInstanceOf(ModelBillingMissingException.class)
            .hasMessageContaining("metric");
    }

    private void publishCost(Long modelId, AiUsageMetric metric, Map<String, String> dimensions) {
        var version = new AiModelPriceVersionEntity();
        version.modelId = modelId;
        version.effectiveFrom = AT.minusHours(1);
        var component = new AiModelPriceComponentEntity();
        component.metric = metric.name();
        component.unitSize = BigDecimal.ONE;
        component.unitPrice = BigDecimal.ONE;
        component.currency = "USD";
        component.dimensionsJson = AiAccountingJson.write(dimensions);
        component.dimensionsKey = AiAccountingJson.canonicalKey(dimensions);
        costService.publish(version, List.of(component));
    }

    private void publishPoint(Long modelId, AiUsageMetric metric, Map<String, String> dimensions) {
        var component = new AiModelPointPriceComponentEntity();
        component.metric = metric.name();
        component.unitSize = BigDecimal.ONE;
        component.pointRate = BigDecimal.ONE;
        component.dimensions = dimensions;
        pointService.publish(modelId, AT.minusHours(1), null, List.of(component), 1L);
    }
}
