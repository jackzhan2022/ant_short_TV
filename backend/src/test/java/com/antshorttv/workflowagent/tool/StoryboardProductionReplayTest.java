package com.antshorttv.workflowagent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StoryboardProductionReplayTest {
    private final ObjectMapper json = new ObjectMapper();
    private final EpisodeSourceSegmenter segmenter = new EpisodeSourceSegmenter();

    @Test
    void replaysAllSanitizedProject26CasesOfflineWithoutAProvider() throws Exception {
        JsonNode fixture;
        try (InputStream input = getClass().getResourceAsStream(
            "/storyboard-replay/project-26-first-round.json")) {
            fixture = json.readTree(input);
        }
        assertThat(fixture.path("sanitized").asBoolean()).isTrue();
        assertThat(fixture.path("episodeCount").asInt()).isEqualTo(57);

        Set<Integer> replayedEpisodes = new HashSet<>();
        int warningSuccesses = 0;
        int normalizedMissingSounds = 0;
        int normalizedSoundRanges = 0;
        int normalizedCoverageGaps = 0;
        var segments = segmenter.segment("开场\nSerena: No...\n结束");

        for (JsonNode category : fixture.path("cases")) {
            String issue = category.path("issue").asText();
            for (JsonNode episode : category.path("episodeNos")) {
                assertThat(replayedEpisodes.add(episode.asInt())).isTrue();
                ArrayNode payload = payload(issue);
                StoryboardNormalizer.Result result = new StoryboardNormalizer(json)
                    .normalize(payload, segments);
                JsonNode board = result.storyboards().get(0);
                assertThat(board.path("storyboardNo").asInt()).isEqualTo(1);
                assertThat(board.path("sourceFrom").asText()).isEqualTo("S0001");
                assertThat(board.path("sourceTo").asText()).isEqualTo("S0003");
                assertThat(board.path("shots").get(1).path("soundSegmentIds").toString())
                    .isEqualTo("[\"S0002\"]");

                switch (issue) {
                    case "ACTION_DENSITY" -> {
                        warningSuccesses++;
                        assertThat(board.path("shots").get(0).path("action").asText())
                            .contains("然后");
                    }
                    case "MISSING_SOUND" -> normalizedMissingSounds++;
                    case "SOUND_OUT_OF_RANGE" -> normalizedSoundRanges++;
                    case "SOURCE_COVERAGE_GAP" -> normalizedCoverageGaps++;
                    default -> assertThat(issue).isEqualTo("NONE");
                }
            }
        }

        assertThat(replayedEpisodes).containsExactlyInAnyOrderElementsOf(
            java.util.stream.IntStream.rangeClosed(1, 57).boxed().toList());
        assertThat(warningSuccesses).isEqualTo(18);
        assertThat(normalizedMissingSounds).isEqualTo(15);
        assertThat(normalizedSoundRanges).isEqualTo(1);
        assertThat(normalizedCoverageGaps).isEqualTo(1);
    }

    private ArrayNode payload(String issue) throws Exception {
        ArrayNode boards = (ArrayNode) json.readTree("""
            [{
              "storyboardNo":9,
              "sourceTo":"S0003",
              "shots":[
                {"shotNo":7,"durationSeconds":3,"positioning":"门口","action":"她转身"},
                {"shotNo":8,"durationSeconds":3,"positioning":"近景","action":"她停下"}
              ]
            }]
            """);
        ObjectNode board = (ObjectNode) boards.get(0);
        if ("ACTION_DENSITY".equals(issue)) {
            ((ObjectNode) board.path("shots").get(0)).put("action", "她转身，然后走向门口");
        } else if ("SOUND_OUT_OF_RANGE".equals(issue)) {
            ((ObjectNode) board.path("shots").get(0)).putArray("soundSegmentIds").add("S9999");
        } else if ("SOURCE_COVERAGE_GAP".equals(issue)) {
            board.put("sourceTo", "S0001");
        }
        return boards;
    }
}
