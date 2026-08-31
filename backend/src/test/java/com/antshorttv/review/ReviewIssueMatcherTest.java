package com.antshorttv.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.List;
import java.util.Set;
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

    @Test
    void matchesNewPersistingShiftedReopenedAndAmbiguousIssuesDeterministically() {
        ReviewIssueMatcher.IssueSnapshot current = snapshot("台词合理性", "称谓错误", "1-2", "哥哥");
        ReviewIssueMatcher.PriorIssue prior = new ReviewIssueMatcher.PriorIssue(
            "R1-01", current, "fixed", true, Set.of("episode:1/scene:1-2"));
        assertThat(ReviewIssueMatcher.match(List.of(prior),
            new ReviewIssueMatcher.CurrentIssue(current, Set.of("episode:1/scene:1-2"))))
            .extracting(ReviewIssueMatcher.Match::status, ReviewIssueMatcher.Match::relatedIssueNo)
            .containsExactly("persists", "R1-01");

        ReviewIssueMatcher.IssueSnapshot moved = snapshot("台词合理性", "称谓错误", "2-1", "哥哥");
        assertThat(ReviewIssueMatcher.match(List.of(prior),
            new ReviewIssueMatcher.CurrentIssue(moved, Set.of("episode:2/scene:2-1"))).status())
            .isEqualTo("shifted");
        assertThat(ReviewIssueMatcher.match(List.of(),
            new ReviewIssueMatcher.CurrentIssue(current, Set.of())).status()).isEqualTo("new");

        ReviewIssueMatcher.PriorIssue equallyLikely = new ReviewIssueMatcher.PriorIssue(
            "R1-02", current, "new", false, Set.of("episode:1/scene:1-2"));
        assertThat(ReviewIssueMatcher.match(List.of(prior, equallyLikely),
            new ReviewIssueMatcher.CurrentIssue(current, Set.of("episode:1/scene:1-2"))).status())
            .isEqualTo("uncertain");
    }

    private ReviewIssueMatcher.IssueSnapshot snapshot(String dimension, String title, String scene, String excerpt) {
        return new ReviewIssueMatcher.IssueSnapshot(dimension, title,
            Map.of("episode", 1, "scene", scene), excerpt, "问题");
    }
}
