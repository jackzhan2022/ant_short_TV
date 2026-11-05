package com.antshorttv.operations;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Operations DATETIME values are UTC; source business DATETIME values use their own explicit zone. */
public final class OperationsTime {
    private OperationsTime() {}
    public static Instant utc(Object value) {
        if(value==null)return null;
        if(value instanceof Instant instant)return instant;
        if(value instanceof Timestamp timestamp)return timestamp.toInstant();
        if(value instanceof LocalDateTime local)return local.toInstant(ZoneOffset.UTC);
        throw new IllegalArgumentException("Unsupported operations timestamp type");
    }
}
