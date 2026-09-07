package com.antshorttv.workflowagent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void rejectsUnknownReversedAndDecreasingCreativeReferences() throws Exception {
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

        ((ObjectNode) submitted.get(0)).put("sourceTo", "S0003");
        ((ObjectNode) submitted.get(0).path("shots").get(0)).put("sourceAnchor", "S0002");
        ((ObjectNode) submitted.get(0).path("shots").get(1)).put("sourceAnchor", "S0001");
        assertThatThrownBy(() -> normalizer.normalize(submitted, segments))
            .isInstanceOfSatisfying(WorkflowToolValidationException.class,
                failure -> assertThat(failure.details().get("validationCode"))
                    .isEqualTo("SOURCE_ANCHOR_REVERSED"));
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
