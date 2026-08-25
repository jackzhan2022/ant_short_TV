package com.antshorttv.execution;

import java.util.List;

public abstract class AiExecutionHandler {
    public abstract String scene();

    public List<String> scenes() {
        return List.of(scene());
    }

    public void validate(AiExecutionTaskEntity task) {
    }

    public List<String> phases() {
        return List.of("SUBMIT");
    }

    public AiExecutionRetryPolicy retryPolicy() {
        return AiExecutionRetryPolicy.none();
    }

    public List<AiExecutionUsageExpectation> usageExpectations() {
        return List.of();
    }

    public abstract AiExecutionHandlerResult execute(AiExecutionContext context);
}
