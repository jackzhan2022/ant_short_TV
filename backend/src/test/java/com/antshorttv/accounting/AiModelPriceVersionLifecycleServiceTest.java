package com.antshorttv.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class AiModelPriceVersionLifecycleServiceTest {
    private static final LocalDateTime BASE = LocalDateTime.of(2030, 1, 1, 0, 0);

    @Autowired
    private AiModelPriceService costService;

    @Autowired
    private AiModelPointPriceService pointService;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("delete from ai_model_price_component");
        jdbc.update("delete from ai_model_point_price_component");
        jdbc.update("delete from ai_model_price_version");
        jdbc.update("delete from ai_model_point_price_version");
        jdbc.update("delete from ai_model_price_version_sequence");
    }

    @Test
    void allocatesCostAndPointVersionsIndependentlyUnderConcurrentPublication() throws Exception {
        long modelId = 9901L;
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Integer>> costPublications = new ArrayList<>();
            List<Callable<Integer>> pointPublications = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                int slot = index;
                costPublications.add(() -> publishCost(modelId, slot).versionNo);
                pointPublications.add(() -> pointService.publish(
                    modelId,
                    BASE.plusDays(slot * 2L),
                    BASE.plusDays(slot * 2L + 1),
                    List.of(pointComponent("CALL", "1", "2")),
                    1L
                ).versionNo);
            }

            List<Integer> costVersions = executor.invokeAll(costPublications).stream()
                .map(future -> get(future)).sorted().toList();
            List<Integer> pointVersions = executor.invokeAll(pointPublications).stream()
                .map(future -> get(future)).sorted().toList();

            assertThat(costVersions).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
            assertThat(pointVersions).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void continuesFromPersistedHistoryWhenSequenceRowIsMissing() {
        long modelId = 9903L;
        jdbc.update("""
            insert into ai_model_price_version (
              model_id, version_no, status, effective_from, effective_to, published_at, created_at
            ) values (?, 1, 'PUBLISHED', ?, ?, ?, ?)
            """, modelId, BASE.minusDays(2), BASE.minusDays(1), BASE.minusDays(2), BASE.minusDays(2));
        jdbc.update("""
            insert into ai_model_point_price_version (
              model_id, version_no, status, effective_from, effective_to, published_at, created_at
            ) values (?, 1, 'PUBLISHED', ?, ?, ?, ?)
            """, modelId, BASE.minusDays(2), BASE.minusDays(1), BASE.minusDays(2), BASE.minusDays(2));

        AiModelPriceVersionEntity costVersion = publishCost(modelId, 0);
        AiModelPointPriceVersionEntity pointVersion = pointService.publish(
            modelId,
            BASE,
            BASE.plusDays(1),
            List.of(pointComponent("CALL", "1", "2")),
            1L
        );

        assertThat(costVersion.versionNo).isEqualTo(2);
        assertThat(pointVersion.versionNo).isEqualTo(2);
    }

    @Test
    void rejectsInvalidCostAndPointComponentsWithoutPersistingVersions() {
        assertThatThrownBy(() -> costService.publish(costVersion(9902L, 0), List.of(
            costComponent("UNKNOWN", "1", "1", "USD")
        ))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> costService.publish(costVersion(9902L, 0), List.of(
            costComponent("CALL", "0", "1", "USD")
        ))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> costService.publish(costVersion(9902L, 0), List.of(
            costComponent("CALL", "1", "-1", "USD")
        ))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> costService.publish(costVersion(9902L, 0), List.of(
            costComponent("CALL", "1", "1", " ")
        ))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pointService.publish(
            9902L, BASE, BASE.plusDays(1), List.of(pointComponent("CALL", "0", "1")), 1L
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pointService.publish(
            9902L, BASE, BASE.plusDays(1), List.of(pointComponent("CALL", "1", "-1")), 1L
        )).isInstanceOf(IllegalArgumentException.class);

        assertThat(jdbc.queryForObject("select count(*) from ai_model_price_version", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from ai_model_point_price_version", Integer.class)).isZero();
    }

    private AiModelPriceVersionEntity publishCost(long modelId, int slot) {
        return costService.publish(
            costVersion(modelId, slot),
            List.of(costComponent("CALL", "1", "0.01", "USD"))
        );
    }

    private AiModelPriceVersionEntity costVersion(long modelId, int slot) {
        var version = new AiModelPriceVersionEntity();
        version.modelId = modelId;
        version.effectiveFrom = BASE.plusDays(slot * 2L);
        version.effectiveTo = BASE.plusDays(slot * 2L + 1);
        return version;
    }

    private AiModelPriceComponentEntity costComponent(
        String metric,
        String unitSize,
        String unitPrice,
        String currency
    ) {
        var component = new AiModelPriceComponentEntity();
        component.metric = metric;
        component.unitSize = new BigDecimal(unitSize);
        component.unitPrice = new BigDecimal(unitPrice);
        component.currency = currency;
        component.dimensionsJson = AiAccountingJson.write(Map.of());
        component.dimensionsKey = AiAccountingJson.canonicalKey(Map.of());
        return component;
    }

    private AiModelPointPriceComponentEntity pointComponent(String metric, String unitSize, String pointRate) {
        var component = new AiModelPointPriceComponentEntity();
        component.metric = metric;
        component.unitSize = new BigDecimal(unitSize);
        component.pointRate = new BigDecimal(pointRate);
        component.dimensions = Map.of();
        return component;
    }

    private static int get(java.util.concurrent.Future<Integer> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
