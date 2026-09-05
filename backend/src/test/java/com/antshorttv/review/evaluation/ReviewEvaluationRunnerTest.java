package com.antshorttv.review.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReviewEvaluationRunnerTest {

    private final ReviewEvaluationRunner runner = new ReviewEvaluationRunner();

    @Test
    void reportsVersionBoundRecallMissesExtrasAndHistoryIsolationByDimension() {
        ReviewEvaluationRunner.Benchmark benchmark = new ReviewEvaluationRunner.Benchmark("version-a", List.of(
            expected("H-1", "台词合理性", "episode:1/scene:1"),
            expected("H-2", "时间线连续性", "episode:2/scene:3")
        ));
        List<ReviewEvaluationRunner.Finding> candidates = List.of(
            finding("C-1", "台词合理性", "episode:1/scene:1"),
            finding("C-X", "道具连续性", "episode:4/scene:2")
        );
        List<ReviewEvaluationRunner.Finding> formal = List.of(
            finding("R-1", "台词合理性", "episode:1/scene:1")
        );

        ReviewEvaluationRunner.Report report = runner.evaluate(
            benchmark, candidates, formal, List.copyOf(formal), "version-a");

        assertThat(report.candidateRecall()).isEqualTo(0.5);
        assertThat(report.formalRecall()).isEqualTo(0.5);
        assertThat(report.falsePositiveRate()).isZero();
        assertThat(report.historyIsolationAccuracy()).isEqualTo(1.0);
        assertThat(report.dimensions().get("时间线连续性").missedExpectedIds())
            .containsExactly("H-2");
        assertThat(report.dimensions().get("道具连续性").extraCandidateIds())
            .containsExactly("C-X");
    }

    @Test
    void rejectsEvaluationAgainstADifferentScriptVersion() {
        ReviewEvaluationRunner.Benchmark benchmark = new ReviewEvaluationRunner.Benchmark(
            "version-a", List.of(expected("H-1", "台词合理性", "scene:1")));

        assertThatThrownBy(() -> runner.evaluate(
            benchmark, List.of(), List.of(), List.of(), "version-b"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("版本");
    }

    @Test
    void emptyEvidenceCannotMatchAHumanIssue() {
        ReviewEvaluationRunner.Benchmark benchmark = new ReviewEvaluationRunner.Benchmark(
            "version-a", List.of(expected("H-1", "台词合理性", "scene:1")));

        ReviewEvaluationRunner.Report report = runner.evaluate(benchmark,
            List.of(new ReviewEvaluationRunner.Finding("C-1", "台词合理性", List.of(""))),
            List.of(), List.of(), "version-a");

        assertThat(report.candidateRecall()).isZero();
        assertThat(report.dimensions().get("台词合理性").extraCandidateIds())
            .containsExactly("C-1");
    }

    @Test
    void zeroFormalFindingsHasZeroFalsePositiveRate() {
        ReviewEvaluationRunner.Benchmark benchmark = new ReviewEvaluationRunner.Benchmark(
            "version-a", List.of(expected("H-1", "台词合理性", "scene:1")));

        ReviewEvaluationRunner.Report report = runner.evaluate(
            benchmark, List.of(), List.of(), List.of(), "version-a");

        assertThat(report.falsePositiveRate()).isZero();
    }

    private ReviewEvaluationRunner.ExpectedIssue expected(String id, String dimension, String evidence) {
        return new ReviewEvaluationRunner.ExpectedIssue(id, dimension, List.of(evidence));
    }

    private ReviewEvaluationRunner.Finding finding(String id, String dimension, String evidence) {
        return new ReviewEvaluationRunner.Finding(id, dimension, List.of(evidence));
    }
}
