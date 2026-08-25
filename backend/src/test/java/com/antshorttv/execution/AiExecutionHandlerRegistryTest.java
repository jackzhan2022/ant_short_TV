package com.antshorttv.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AiExecutionHandlerRegistryTest {

    @Test
    void oneHandlerCanOwnMultipleStableScenes() {
        AiExecutionHandler handler = new AiExecutionHandler() {
            @Override
            public String scene() {
                return "script_generate";
            }

            public List<String> scenes() {
                return List.of("script_generate", "script_rewrite");
            }

            @Override
            public AiExecutionHandlerResult execute(AiExecutionContext context) {
                return null;
            }
        };

        AiExecutionHandlerRegistry registry = new AiExecutionHandlerRegistry(List.of(handler));

        assertThat(registry.require("script_generate")).isSameAs(handler);
        assertThat(registry.require("script_rewrite")).isSameAs(handler);
    }
}
