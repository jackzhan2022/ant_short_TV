package com.antshorttv.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DatabaseQueryObservationTest {
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
