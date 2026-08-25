package com.antshorttv.ai;

@FunctionalInterface
public interface AiProviderOperation<T> {
    AiProviderExecutionOutcome<T> execute(AiModelRoute route) throws Exception;
}
