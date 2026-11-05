package com.antshorttv.operations;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OperationsQueryTest {
    @Test void boundsTimePageAndRejectsMalformedOrUnknownFilter() {
        var now=Instant.parse("2026-11-05T00:00:00Z");
        assertThat(OperationsQuery.parse(Map.of(),now).pageSize()).isEqualTo(20);
        assertThatThrownBy(() -> OperationsQuery.parse(Map.of("pageSize","101"),now)).isInstanceOf(com.antshorttv.common.BusinessException.class);
        assertThatThrownBy(() -> OperationsQuery.parse(Map.of("from","2020-01-01T00:00:00Z"),now)).isInstanceOf(com.antshorttv.common.BusinessException.class);
        assertThatThrownBy(() -> OperationsQuery.parse(Map.of("tenantId","1 or 1=1"),now)).isInstanceOf(com.antshorttv.common.BusinessException.class);
        assertThatThrownBy(() -> OperationsQuery.parse(Map.of("table","user"),now)).isInstanceOf(com.antshorttv.common.BusinessException.class);
    }
}
