package com.antshorttv.ai;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest
class BuiltInAgentCatalogControllerTest {
    @Autowired @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    @Test
    void retiredManagementPathsAreAbsentFromTheRunningApplication() {
        var paths = mappings.getHandlerMethods().keySet().stream()
            .flatMap(mapping -> mapping.getPatternValues().stream()).toList();
        assertThat(paths).noneMatch(path -> path.startsWith("/api/platform/ai/definitions")
            || path.startsWith("/api/platform/ai/agents") || path.startsWith("/api/platform/ai/skills"));
    }
}
