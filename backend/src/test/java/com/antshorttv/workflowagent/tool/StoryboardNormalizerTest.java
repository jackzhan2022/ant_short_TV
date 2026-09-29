package com.antshorttv.workflowagent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class StoryboardNormalizerTest {
    private final ObjectMapper json = new ObjectMapper();
    private final EpisodeSourceSegmenter segmenter = new EpisodeSourceSegmenter();

    @Test
    void canonicalizesNumbersRangesAnchorsAndSoundOwnershipIdempotently() throws Exception {
        var segments = segmenter.segment("开场\nSerena: one\n转场\n旁白 VO: two\n结束");
        ArrayNode submitted = (ArrayNode) json.readTree("""
            [{
              "storyboardNo":9,"sourceFrom":"S9999","sourceTo":"S0002",
              "shots":[
                {"shotNo":8,"durationSeconds":3,"sourceAnchor":"S0001","soundSegmentIds":["S0004"]},
                {"shotNo":9,"durationSeconds":3,"sourceAnchor":"S0002"}
              ]
            },{
              "storyboardNo":20,"sourceFrom":"S0001","sourceTo":"S0004",
              "shots":[
                {"shotNo":5,"durationSeconds":2},
                {"shotNo":7,"durationSeconds":4}
              ]
            }]
            """);

        StoryboardNormalizer.Result first = new StoryboardNormalizer(json).normalize(submitted, segments);
        ArrayNode boards = first.storyboards();

        assertThat(boards.get(0).path("storyboardNo").asInt()).isEqualTo(1);
        assertThat(boards.get(1).path("storyboardNo").asInt()).isEqualTo(2);
        assertThat(boards.get(0).path("sourceFrom").asText()).isEqualTo("S0001");
        assertThat(boards.get(0).path("sourceTo").asText()).isEqualTo("S0002");
        assertThat(boards.get(1).path("sourceFrom").asText()).isEqualTo("S0003");
        assertThat(boards.get(1).path("sourceTo").asText()).isEqualTo("S0005");
        assertThat(boards.get(0).path("shots").get(0).path("shotNo").asInt()).isEqualTo(1);
        assertThat(boards.get(1).path("shots").get(1).path("shotNo").asInt()).isEqualTo(2);
        assertThat(boards.get(0).path("shots").get(1).path("soundSegmentIds").toString())
            .isEqualTo("[\"S0002\"]");
        assertThat(boards.get(1).path("shots").get(1).path("soundSegmentIds").toString())
            .isEqualTo("[\"S0004\"]");
        assertThat(first.derivedSoundCount()).isEqualTo(2);
        assertThat(first.normalizedFieldCount()).isGreaterThan(0);

        StoryboardNormalizer.Result second = new StoryboardNormalizer(json).normalize(boards, segments);
        assertThat(second.storyboards()).isEqualTo(boards);
        assertThat(second.normalizedFieldCount()).isZero();
        assertThat(second.derivedSoundCount()).isEqualTo(2);
    }

    @Test
    void project41NonMonotonicAnchorsAreRepairedWhileUnknownCreativeEndsRemainInvalid() throws Exception {
        var segments = segmenter.segment("开场\nSerena: one\n结束");
        ArrayNode submitted = (ArrayNode) json.readTree("""
            [{"storyboardNo":1,"sourceTo":"S9999","shots":[
              {"shotNo":1,"durationSeconds":3},{"shotNo":2,"durationSeconds":3}
            ]}]
            """);
        StoryboardNormalizer normalizer = new StoryboardNormalizer(json);

        assertThatThrownBy(() -> normalizer.normalize(submitted, segments))
            .isInstanceOfSatisfying(WorkflowToolValidationException.class,
                failure -> assertThat(failure.details().get("validationCode"))
                    .isEqualTo("SOURCE_SEGMENT_UNKNOWN"));

        ((ObjectNode) submitted.get(0)).put("sourceTo", "S0002");
        ((ObjectNode) submitted.get(0).path("shots").get(0)).put("sourceAnchor", "S0003");
        ((ObjectNode) submitted.get(0).path("shots").get(1)).put("sourceAnchor", "S0001");
        ObjectNode finalBoard = ((ObjectNode) submitted.get(0)).deepCopy();
        finalBoard.put("sourceTo", "S0003");
        ((ObjectNode) finalBoard.path("shots").get(0)).remove("sourceAnchor");
        ((ObjectNode) finalBoard.path("shots").get(1)).remove("sourceAnchor");
        submitted.add(finalBoard);

        StoryboardNormalizer.Result result = normalizer.normalize(submitted, segments);

        assertThat(result.storyboards().get(0).path("shots").get(0).path("sourceAnchor").asText())
            .isEqualTo("S0002");
        assertThat(result.storyboards().get(0).path("shots").get(1).path("sourceAnchor").asText())
            .isEqualTo("S0002");
        assertThat(result.findings()).extracting(StoryboardValidationResult.Finding::code)
            .contains("SOURCE_ANCHOR_CLAMPED", "SOURCE_ANCHOR_REORDERED");
    }

    @Test
    void appliesShotTypeAndDurationDefaultsAndClampsDurationBounds() throws Exception {
        var segments = segmenter.segment("开场\nSerena: one\n结束");
        ArrayNode submitted = (ArrayNode) json.readTree("""
            [{"sourceTo":"S0003","shots":[
              {"durationSeconds":0.5,"positioning":"wide","action":"open"},
              {"positioning":"medium","action":"react"},
              {"durationSeconds":8,"positioning":"close","action":"finish"}
            ]}]
            """);

        StoryboardNormalizer.Result result = new StoryboardNormalizer(json).normalize(submitted, segments);
        JsonNode board = result.storyboards().get(0);

        assertThat(board.path("shotType").asText()).isEqualTo("MULTI_SHOT");
        assertThat(board.path("shots").get(0).path("shotNo").asInt()).isEqualTo(1);
        assertThat(board.path("shots").get(0).path("durationSeconds").decimalValue())
            .isEqualByComparingTo("1.5");
        assertThat(board.path("shots").get(1).path("durationSeconds").decimalValue())
            .isEqualByComparingTo("3");
        assertThat(board.path("shots").get(2).path("durationSeconds").decimalValue())
            .isEqualByComparingTo("4");
        assertThat(result.findings())
            .allSatisfy(finding -> assertThat(finding.severity())
                .isEqualTo(StoryboardValidationResult.Severity.REPAIRABLE));
        assertThat(result.findings()).extracting(StoryboardValidationResult.Finding::jsonPath)
            .contains("$.storyboards[0].shotType",
                "$.storyboards[0].shots[0].durationSeconds",
                "$.storyboards[0].shots[1].durationSeconds",
                "$.storyboards[0].shots[2].durationSeconds");
    }

    @Test
    void replacesAlteredAndRepeatedUtteranceInputWithOneTrustedAssignment() throws Exception {
        var segments = segmenter.segment("开场\nSerena: one\n结束");
        ArrayNode submitted = (ArrayNode) json.readTree("""
            [{"sourceTo":"S0003","shots":[
              {"durationSeconds":3,"positioning":"wide","action":"open",
               "dialogue":"changed","soundSegmentIds":["S0002"]},
              {"durationSeconds":3,"positioning":"close","action":"finish",
               "dialogue":"changed again","soundSegmentIds":["S0002"]},
              {"durationSeconds":4,"positioning":"close","action":"hold"}
            ]}]
            """);

        StoryboardNormalizer.Result result = new StoryboardNormalizer(json).normalize(submitted, segments);
        JsonNode shots = result.storyboards().get(0).path("shots");

        assertThat(shots.findValues("dialogue")).isEmpty();
        assertThat(shots.get(0).path("soundSegmentIds")).isEmpty();
        assertThat(shots.get(1).path("soundSegmentIds").toString()).isEqualTo("[\"S0002\"]");
        assertThat(shots.get(2).path("soundSegmentIds")).isEmpty();
        assertThat(result.derivedSoundCount()).isEqualTo(1);
    }

    @Test
    void assignsSoundsAfterTheFinalExplicitAnchorToTheLastShotExactlyOnce() throws Exception {
        var segments = segmenter.segment("开场\nSerena: one\n旁白 VO: two\n结束");
        ArrayNode submitted = (ArrayNode) json.readTree("""
            [{"storyboardNo":1,"sourceTo":"S0004","shots":[
              {"shotNo":1,"durationSeconds":3,"sourceAnchor":"S0001"},
              {"shotNo":2,"durationSeconds":3,"sourceAnchor":"S0001"}
            ]}]
            """);

        StoryboardNormalizer.Result result = new StoryboardNormalizer(json).normalize(submitted, segments);

        assertThat(result.storyboards().get(0).path("shots").get(0).path("soundSegmentIds")).isEmpty();
        assertThat(result.storyboards().get(0).path("shots").get(1).path("soundSegmentIds").toString())
            .isEqualTo("[\"S0002\",\"S0003\"]");
        assertThat(result.derivedSoundCount()).isEqualTo(2);
    }
}
