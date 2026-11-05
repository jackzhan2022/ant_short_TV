package com.antshorttv.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DatabaseQueryObservationTest {
    @Test void nestedJdbcAndMybatisTimingCountsOnePhysicalBoundary() {
        try(var observation=DatabaseQueryObservation.open()) {
            try(var jdbc=DatabaseQueryObservation.query()) {try(var mybatis=DatabaseQueryObservation.query()) {assertThat(DatabaseQueryObservation.snapshot()[0]).isZero();}}
            assertThat(DatabaseQueryObservation.snapshot()[0]).isEqualTo(1);
        }
    }
    @Test
    void nestedScopesRestoreTheEnclosingRequestAndNestedQueryBoundariesCountOnce() {
        try (var outer = DatabaseQueryObservation.open()) {
            DatabaseQueryObservation.recordQuery(10);
            try (var inner = DatabaseQueryObservation.open()) {
                DatabaseQueryObservation.recordQuery(20);
                assertThat(DatabaseQueryObservation.snapshot()).containsExactly(1, 20);
            }
            assertThat(DatabaseQueryObservation.snapshot()).containsExactly(2, 30);
        }
    }
    @Test
    void recordsOnlyQueryCountAndDurationWithinTheActiveScope() {
        try (var scope = DatabaseQueryObservation.open()) {
            DatabaseQueryObservation.recordQuery(1_500_000L);
            DatabaseQueryObservation.recordQuery(2_500_000L);

            assertThat(DatabaseQueryObservation.snapshot()).containsExactly(2L, 4_000_000L);
        }

        assertThat(DatabaseQueryObservation.snapshot()).containsExactly(0L, 0L);
    }
}
