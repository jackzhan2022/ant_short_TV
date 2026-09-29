package com.antshorttv.workflowagent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StoryboardFallbackCompleterTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void completesTrustedRangesDescriptionsDefaultsAndKeepsStructuredMaterials() throws Exception {
        WorkflowToolRunState state = new WorkflowToolRunState();
        state.put("currentEpisodeFingerprint", "fp-41");
        state.put("currentEpisodeSourceSegments",
            new EpisodeSourceSegmenter().segment("走廊开场\nSerena: No...\n手机落地\n卧室结束"));
        ToolExecutionContext context = new ToolExecutionContext(
            1L, 2L, 3L, 4L, 5L, 6L, null, 7L, 8L, 9L, 1,
            Set.of("SCRIPT:EDIT"), Instant.now().plusSeconds(30), state);
        var submitted = json.readTree("""
            {"schemaVersion":3,"episodeFingerprint":"fp-41","storyboards":[{
              "storyboardNo":9,"sourceTo":"S9999",
              "usedAssetKeys":{"characters":[{"assetKey":"c_12","variantKey":"v_33","role":"VISIBLE"}],
                               "scenes":[],"props":[]},
              "shots":[{"positioning":"","action":""}]
            }]}
            """);
        var failure = new WorkflowToolValidationException("bad anchor", Map.of(
            "validationCode", "SOURCE_SEGMENT_UNKNOWN", "severity", "REPAIRABLE"));

        var completed = new StoryboardFallbackCompleter(json).complete(context, submitted, failure);

        assertThat(completed.path("_serverFallback").asBoolean()).isTrue();
        assertThat(completed.path("episodeFingerprint").asText()).isEqualTo("fp-41");
        assertThat(completed.path("storyboards").get(0).path("sourceTo").asText()).isEqualTo("S0004");
        assertThat(completed.path("storyboards").get(0).path("shots")).hasSize(3);
        assertThat(completed.path("storyboards").get(0).path("shots").get(0)
            .path("positioning").asText()).isNotBlank();
        assertThat(completed.path("storyboards").get(0).path("shots").get(0)
            .path("action").asText()).isEqualTo("走廊开场");
        assertThat(completed.path("storyboards").get(0).path("usedAssetKeys")
            .path("characters").get(0).path("variantKey").asText()).isEqualTo("v_33");
    }
}
