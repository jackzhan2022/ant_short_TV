package com.antshorttv.workflowagent.skill;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class RemainingAnalysisBehaviorFixtureTest {
    @Test
    void definesBehavioralBaselinesBeforeTheFrameworkSkills() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
            "/workflowagent/remaining-analysis-behavior-fixtures.json")) {
            assertThat(input).isNotNull();
            JsonNode fixtures = new ObjectMapper().readTree(input);
            assertThat(fixtures.fieldNames()).toIterable().contains(
                "episodeBoundarySelection", "summaryFidelity", "stableEntityNaming",
                "sameNameAmbiguity", "characterLooks", "propStates");
            assertThat(fixtures.path("episodeBoundarySelection").path("expectedInvariants")).hasSize(4);
            assertThat(fixtures.path("summaryFidelity").path("forbiddenFacts")).isNotEmpty();
            assertThat(fixtures.path("sameNameAmbiguity").path("existingCatalog")).hasSize(2);
            assertThat(fixtures.path("characterLooks").path("expectedLooks")).hasSize(2);
            assertThat(fixtures.path("propStates").path("expectedVariants")).hasSize(2);
            assertThat(fixtures.path("episodeSplittingCases")).hasSize(7);
            assertThat(fixtures.path("summaryCases")).hasSize(6);
            assertThat(fixtures.path("assetRecognitionCases")).hasSize(12);
            assertThat(fixtures.path("episodeSplittingCases").get(1).path("source").asText())
                .contains("她推开门", "录音机");
        }
    }
}
