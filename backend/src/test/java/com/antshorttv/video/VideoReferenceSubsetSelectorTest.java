package com.antshorttv.video;

import static org.assertj.core.api.Assertions.assertThat;

import com.antshorttv.script.StoryboardPromptCompiler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class VideoReferenceSubsetSelectorTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void ranksRolesDeduplicatesMixedMediaAndReportsModelLimitOmissions() throws Exception {
        var references = List.of(
            resolved("IMAGE", 4L, "TRANSITION", 3, true, null),
            resolved("VIDEO", 20L, null, 4, false, new BigDecimal("5")),
            resolved("IMAGE", 1L, "MAIN", 0, true, null),
            resolved("IMAGE", 2L, "VISIBLE", 0, true, null),
            resolved("IMAGE", 3L, "SUPPORTING", 1, true, null),
            resolved("IMAGE", 1L, "MAIN", 8, true, null),
            resolved("AUDIO", 30L, null, 5, false, new BigDecimal("4")));
        var constraints = json.readTree("""
            {"image":{"maxCount":2},"video":{"maxCount":1,"maxTotalDurationSeconds":10},
             "audio":{"maxCount":1,"maxTotalDurationSeconds":10}}
            """);

        VideoReferenceSubsetSelector.Selection selection =
            new VideoReferenceSubsetSelector().select(references, constraints);

        assertThat(selection.kept()).extracting(item -> item.reference().sourceId())
            .containsExactly(1L, 2L, 20L, 30L);
        assertThat(selection.omitted()).extracting(
            VideoReferenceSubsetSelector.OmittedReference::sourceId,
            VideoReferenceSubsetSelector.OmittedReference::reason)
            .contains(
                org.assertj.core.groups.Tuple.tuple(1L, "DUPLICATE_MEDIA"),
                org.assertj.core.groups.Tuple.tuple(3L, "MODEL_COUNT_LIMIT"),
                org.assertj.core.groups.Tuple.tuple(4L, "MODEL_COUNT_LIMIT"));
    }

    private VideoTaskReferenceResolver.ResolvedReference resolved(
        String mediaType,
        Long sourceId,
        String role,
        int sortOrder,
        boolean formal,
        BigDecimal duration
    ) {
        var reference = new StoryboardPromptCompiler.Reference(
            mediaType, sortOrder + 1, mediaType + (sortOrder + 1),
            "reference_" + mediaType.toLowerCase(),
            mediaType + "_MATERIAL", sourceId, null,
            formal ? "CHARACTER" : null, formal ? sourceId : null,
            "ref-" + sourceId, sortOrder, role, formal);
        return new VideoTaskReferenceResolver.ResolvedReference(
            reference, "ref-" + sourceId, "/" + sourceId,
            "https://cdn/" + sourceId, mediaType.equals("AUDIO") ? "mp3" : "png",
            100L, 720, 1280, duration, null);
    }
}
