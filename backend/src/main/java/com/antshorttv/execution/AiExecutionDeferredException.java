package com.antshorttv.execution;

import java.time.Duration;

public class AiExecutionDeferredException extends RuntimeException {
    private final String code;
    private final Duration delay;

    public AiExecutionDeferredException(String code, String message, Duration delay) {
        super(message);
        this.code = code;
        this.delay = delay;
    }

    public String code() {
        return code;
    }

    public Duration delay() {
        return delay;
    }
}
