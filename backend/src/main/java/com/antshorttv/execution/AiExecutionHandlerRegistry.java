package com.antshorttv.execution;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AiExecutionHandlerRegistry {
    private final Map<String, AiExecutionHandler> handlers;

    public AiExecutionHandlerRegistry(List<AiExecutionHandler> handlers) {
        Map<String, AiExecutionHandler> indexed = new HashMap<>();
        for (AiExecutionHandler handler : handlers) {
            for (String scene : handler.scenes()) {
                AiExecutionHandler previous = indexed.put(scene, handler);
                if (previous != null) {
                    throw new IllegalStateException("Duplicate AI execution handler scene: " + scene);
                }
            }
        }
        this.handlers = Map.copyOf(indexed);
    }

    public AiExecutionHandler require(String scene) {
        AiExecutionHandler handler = handlers.get(scene);
        if (handler == null) {
            throw new IllegalArgumentException("No AI execution handler registered for scene: " + scene);
        }
        return handler;
    }
}
