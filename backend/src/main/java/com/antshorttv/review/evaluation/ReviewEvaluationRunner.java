package com.antshorttv.review.evaluation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ReviewEvaluationRunner {

    public Report evaluate(Benchmark benchmark, List<Finding> candidates, List<Finding> formal,
        List<Finding> historyVariantFormal, String actualVersionHash) {
        if (!benchmark.versionHash().equals(actualVersionHash)) {
            throw new IllegalArgumentException("人工基准与待评测剧本版本不一致。");
        }
        MatchResult candidateMatches = match(benchmark.issues(), candidates);
        MatchResult formalMatches = match(benchmark.issues(), formal);
        Set<String> dimensions = new LinkedHashSet<>();
        benchmark.issues().forEach(issue -> dimensions.add(issue.dimension()));
        candidates.forEach(finding -> dimensions.add(finding.dimension()));
        formal.forEach(finding -> dimensions.add(finding.dimension()));
        Map<String, DimensionReport> byDimension = new LinkedHashMap<>();
        for (String dimension : dimensions) {
            List<ExpectedIssue> expected = benchmark.issues().stream()
                .filter(issue -> dimension.equals(issue.dimension())).toList();
            List<Finding> dimensionCandidates = candidates.stream()
                .filter(finding -> dimension.equals(finding.dimension())).toList();
            List<Finding> dimensionFormal = formal.stream()
                .filter(finding -> dimension.equals(finding.dimension())).toList();
            byDimension.put(dimension, new DimensionReport(
                expected.size(), countMatchedExpected(expected, candidateMatches),
                countMatchedExpected(expected, formalMatches),
                expected.stream().map(ExpectedIssue::id)
                    .filter(id -> !formalMatches.expectedIds().contains(id)).toList(),
                dimensionCandidates.stream().map(Finding::id)
                    .filter(id -> !candidateMatches.findingIds().contains(id)).toList(),
                dimensionFormal.stream().map(Finding::id)
                    .filter(id -> !formalMatches.findingIds().contains(id)).toList()
            ));
        }
        return new Report(
            ratio(candidateMatches.expectedIds().size(), benchmark.issues().size()),
            ratio(formalMatches.expectedIds().size(), benchmark.issues().size()),
            falsePositiveRatio(formalMatches.extraCount(), formal.size()),
            isolation(formal, historyVariantFormal),
            Map.copyOf(byDimension)
        );
    }

    private MatchResult match(List<ExpectedIssue> expected, List<Finding> findings) {
        Set<String> expectedIds = new LinkedHashSet<>();
        Set<String> findingIds = new LinkedHashSet<>();
        for (ExpectedIssue issue : expected) {
            for (Finding finding : findings) {
                if (findingIds.contains(finding.id()) || !issue.dimension().equals(finding.dimension())) continue;
                if (overlaps(issue.evidenceRefs(), finding.evidenceRefs())) {
                    expectedIds.add(issue.id());
                    findingIds.add(finding.id());
                    break;
                }
            }
        }
        return new MatchResult(expectedIds, findingIds, findings.size() - findingIds.size());
    }

    private int countMatchedExpected(List<ExpectedIssue> expected, MatchResult matches) {
        return (int) expected.stream().filter(issue -> matches.expectedIds().contains(issue.id())).count();
    }

    private boolean overlaps(List<String> expected, List<String> actual) {
        for (String left : expected) {
            String normalizedLeft = normalize(left);
            for (String right : actual) {
                String normalizedRight = normalize(right);
                if (!normalizedLeft.isEmpty() && !normalizedRight.isEmpty()
                    && (normalizedLeft.contains(normalizedRight)
                    || normalizedRight.contains(normalizedLeft))) return true;
            }
        }
        return false;
    }

    private double isolation(List<Finding> baseline, List<Finding> variant) {
        Set<String> left = signatures(baseline);
        Set<String> right = signatures(variant);
        if (left.isEmpty() && right.isEmpty()) return 1.0;
        Set<String> union = new LinkedHashSet<>(left);
        union.addAll(right);
        Set<String> intersection = new LinkedHashSet<>(left);
        intersection.retainAll(right);
        return ratio(intersection.size(), union.size());
    }

    private Set<String> signatures(List<Finding> findings) {
        Set<String> signatures = new LinkedHashSet<>();
        for (Finding finding : findings) {
            List<String> evidence = new ArrayList<>(finding.evidenceRefs().stream().map(this::normalize).toList());
            evidence.sort(String::compareTo);
            signatures.add(normalize(finding.dimension()) + "|" + String.join("|", evidence));
        }
        return signatures;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private double ratio(int numerator, int denominator) {
        return denominator == 0 ? 1.0 : (double) numerator / denominator;
    }

    private double falsePositiveRatio(int numerator, int denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    public record Benchmark(String versionHash, List<ExpectedIssue> issues) {
        public Benchmark {
            if (versionHash == null || versionHash.isBlank()) throw new IllegalArgumentException("基准版本不能为空。");
            issues = List.copyOf(issues);
        }
    }

    public record ExpectedIssue(String id, String dimension, List<String> evidenceRefs) {
        public ExpectedIssue { evidenceRefs = List.copyOf(evidenceRefs); }
    }

    public record Finding(String id, String dimension, List<String> evidenceRefs) {
        public Finding { evidenceRefs = List.copyOf(evidenceRefs); }
    }

    public record DimensionReport(int expectedCount, int candidateMatched, int formalMatched,
                                  List<String> missedExpectedIds, List<String> extraCandidateIds,
                                  List<String> extraFormalIds) {}

    public record Report(double candidateRecall, double formalRecall, double falsePositiveRate,
                         double historyIsolationAccuracy, Map<String, DimensionReport> dimensions) {}

    private record MatchResult(Set<String> expectedIds, Set<String> findingIds, int extraCount) {}
}
