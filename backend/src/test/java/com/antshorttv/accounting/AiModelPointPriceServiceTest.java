package com.antshorttv.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class AiModelPointPriceServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 26, 10, 0);

    @Autowired
    private AiModelPointPriceService service;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("delete from ai_model_point_price_component");
        jdbc.update("delete from ai_model_point_price_version");
    }

    @Test
    void generatesIndependentVersionsAndListsComponents() {
        var first = service.publish(901L, NOW, null, List.of(component("CALL", "1", "2")), 11L);
        var second = service.publish(901L, NOW.plusDays(1), null, List.of(component("CALL", "1", "3")), 11L);

        assertThat(first.versionNo).isEqualTo(1);
        assertThat(second.versionNo).isEqualTo(2);
        assertThat(service.list(901L)).extracting(v -> v.versionNo).containsExactly(1, 2);
        assertThat(service.components(second.id)).singleElement()
            .satisfies(c -> assertThat(c.pointRate).isEqualByComparingTo("3"));
    }

    @Test
    void rejectsOverlappingIntervalsAndRevokesOnlyFutureVersions() {
        service.publish(902L, NOW, NOW.plusDays(2), List.of(component("IMAGE", "1", "5")), 11L);
        assertThatThrownBy(() -> service.publish(
            902L, NOW.plusDays(1), NOW.plusDays(3), List.of(component("IMAGE", "1", "6")), 11L
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("overlap");

        var future = service.publish(902L, NOW.plusDays(3), null, List.of(component("IMAGE", "1", "7")), 11L);
        service.revoke(future.id, NOW.plusDays(2));
        assertThat(service.require(future.id).status).isEqualTo("REVOKED");
        assertThatThrownBy(() -> service.revoke(future.id, NOW.plusDays(4)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("future");
    }

    @Test
    void revokedVersionNumbersAreNeverReusedAndRepeatedRevocationIsRejected() {
        var first = service.publish(
            903L, NOW.plusDays(1), null, List.of(component("CALL", "1", "2")), 11L
        );
        service.revoke(first.id, NOW);

        assertThatThrownBy(() -> service.revoke(first.id, NOW))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("future");
        var replacement = service.publish(
            903L, NOW.plusDays(2), null, List.of(component("CALL", "1", "3")), 11L
        );
        assertThat(replacement.versionNo).isEqualTo(2);
    }

    private AiModelPointPriceComponentEntity component(String metric, String unitSize, String pointRate) {
        var component = new AiModelPointPriceComponentEntity();
        component.metric = metric;
        component.unitSize = new BigDecimal(unitSize);
        component.pointRate = new BigDecimal(pointRate);
        component.dimensions = Map.of();
        return component;
    }
}
