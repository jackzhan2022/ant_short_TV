package com.antshorttv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void publishesOnlyCurrentWorkflowContracts() throws Exception {
        String document = mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var schema = json.readTree(document);
        var paths = schema.path("paths");
        assertThat(paths.has("/api/projects/{projectId}/script-page-workspace")).isTrue();
        assertThat(paths.has("/api/projects/{projectId}/asset-reextraction")).isTrue();
        for (String retired : java.util.List.of("script-workspace", "asset-settings-workspace",
            "scripts/ai-extract-elements", "asset-candidates")) {
            assertThat(paths.has("/api/projects/{projectId}/" + retired)).isFalse();
        }
        assertThat(schema.path("components").path("schemas").has("ScriptWorkspaceResponse")).isFalse();
        assertThat(paths.path("/api/projects/{projectId}/scripts/current").path("put")
            .path("responses").path("200").path("content").path("*/*")
            .path("schema").path("$ref").asText()).endsWith("ApiResponseVoid");
        String output = System.getProperty("openapi.output");
        if (output != null) Files.writeString(Path.of(output), document);
    }
}
