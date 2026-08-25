package com.antshorttv.execution;

import java.time.Duration;

public record AiExecutionRetryPolicy(int maxAttempts, Duration delay) {
    public static AiExecutionRetryPolicy none() {
        return new AiExecutionRetryPolicy(1, Duration.ZERO);
    }
}
