package com.antshorttv.operations;

import static org.assertj.core.api.Assertions.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

class OperationsTimeTest {
    @Test void mysqlDatetimeAndH2TimestampHaveTheSameUtcMeaningRegardlessOfJvmZone() {
        TimeZone previous=TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
            Instant expected=Instant.parse("2026-11-05T01:10:00Z");
            assertThat(OperationsTime.utc(LocalDateTime.parse("2026-11-05T01:10:00"))).isEqualTo(expected);
            assertThat(OperationsTime.utc(Timestamp.from(expected))).isEqualTo(expected);
            assertThat(OperationsTime.utc(expected)).isEqualTo(expected);
            assertThat(OperationsTime.utc(null)).isNull();
        } finally { TimeZone.setDefault(previous); }
    }
}
