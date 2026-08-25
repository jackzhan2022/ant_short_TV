package com.antshorttv.execution;

public class AiExecutionClaimLostException extends RuntimeException {
    public AiExecutionClaimLostException(Long executionId) {
        super("AI execution claim was lost: " + executionId);
    }
}
