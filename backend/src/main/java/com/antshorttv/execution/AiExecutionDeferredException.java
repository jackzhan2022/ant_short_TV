package com.antshorttv.execution;

/** Resource contention is rescheduled without provider failure or settlement. */
public class AiExecutionDeferredException extends RuntimeException {
    public AiExecutionDeferredException(String message) { super(message); }
}
