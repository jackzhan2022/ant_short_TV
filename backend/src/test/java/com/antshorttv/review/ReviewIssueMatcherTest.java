package com.antshorttv.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ReviewIssueMatcherTest {

    @Test
    void buildsSameSignatureForEquivalentIssueText() {
        ReviewIssueMatcher.IssueSnapshot left = new ReviewIssueMatcher.IssueSnapshot(
            "台词合理性",
            "人名混乱",
            Map.of("episode", 1),
            "林晚看向周野",
            "同一场中人物称呼不一致"
        );
        ReviewIssueMatcher.IssueSnapshot right = new ReviewIssueMatcher.IssueSnapshot(
            " 台词合理性 ",
            "人名  混乱",
            Map.of("episode", 1),
            "林晚看向周野",
            "同一场中人物称呼不一致"
        );

        assertThat(ReviewIssueMatcher.signature(left))
            .isEqualTo(ReviewIssueMatcher.signature(right));
    }

    @Test
    void classifiesSameIssueAsPersistsAndChangedLocationAsShifted() {
        ReviewIssueMatcher.IssueSnapshot previous = new ReviewIssueMatcher.IssueSnapshot(
            "人物关系一致性",
            "人名混乱",
            Map.of("episode", 1, "scene", "3"),
            "周野",
            "同一人物出现多个名字"
        );
        ReviewIssueMatcher.IssueSnapshot same = new ReviewIssueMatcher.IssueSnapshot(
            "人物关系一致性",
            "人名混乱",
            Map.of("episode", 1, "scene", "3"),
            "周野",
            "同一人物出现多个名字"
        );
        ReviewIssueMatcher.IssueSnapshot moved = new ReviewIssueMatcher.IssueSnapshot(
            "人物关系一致性",
            "人名混乱",
            Map.of("episode", 2, "scene", "1"),
            "周野",
            "同一人物出现多个名字"
        );

        assertThat(ReviewIssueMatcher.classify(previous, same, "persists")).isEqualTo("persists");
        assertThat(ReviewIssueMatcher.classify(previous, moved, "persists")).isEqualTo("shifted");
    }
}
