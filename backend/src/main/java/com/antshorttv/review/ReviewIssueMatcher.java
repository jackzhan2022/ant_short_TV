package com.antshorttv.review;

import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.Set;

final class ReviewIssueMatcher {
    private ReviewIssueMatcher() {
    }

    static String signature(IssueSnapshot issue) {
        return normalize(issue.dimension())
            + "|" + normalize(issue.title())
            + "|" + normalize(issue.excerpt())
            + "|" + normalize(issue.problem());
    }

    static String classify(IssueSnapshot previous, IssueSnapshot current, String previousStatus) {
        if (!normalize(previous.title()).equals(normalize(current.title()))) {
            return "shifted";
        }
        if (!sameAnchor(previous.position(), current.position())) {
            return "shifted";
        }
        if (signature(previous).equals(signature(current))) {
            return "persists";
        }
        return "persists";
    }

    static Match match(List<PriorIssue> previous, CurrentIssue current) {
        if (previous == null || previous.isEmpty()) return new Match("new", null);
        List<Scored> matches = previous.stream()
            .filter(prior -> normalize(prior.issue.dimension()).equals(normalize(current.issue.dimension())))
            .map(prior -> new Scored(prior, score(prior, current)))
            .filter(scored -> scored.score >= 4)
            .sorted(java.util.Comparator.comparingInt(Scored::score).reversed()
                .thenComparing(scored -> scored.prior.issueNo()))
            .toList();
        if (matches.isEmpty()) return new Match("new", null);
        int best = matches.get(0).score;
        if (matches.size() > 1 && matches.get(1).score == best) return new Match("uncertain", null);
        PriorIssue selected = matches.get(0).prior;
        return new Match(classify(selected.issue, current.issue, selected.status), selected.issueNo);
    }

    private static int score(PriorIssue prior, CurrentIssue current) {
        int score = 0;
        if (signature(prior.issue).equals(signature(current.issue))) score += 5;
        if (normalize(prior.issue.title).equals(normalize(current.issue.title))) score += 2;
        if (normalize(prior.issue.excerpt).equals(normalize(current.issue.excerpt))) score += 2;
        if (normalize(prior.issue.problem).equals(normalize(current.issue.problem))) score += 1;
        Set<String> left = prior.anchors == null ? Set.of() : prior.anchors;
        Set<String> right = current.anchors == null ? Set.of() : current.anchors;
        if (left.stream().anyMatch(right::contains)) score += 4;
        return score;
    }

    private static boolean sameAnchor(Map<String, Object> previous, Map<String, Object> current) {
        return value(previous, "episode").equals(value(current, "episode"))
            && value(previous, "scene").equals(value(current, "scene"));
    }

    private static String value(Map<String, Object> position, String key) {
        Object value = position == null ? null : position.get(key);
        return normalize(value == null ? "" : value.toString());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    record IssueSnapshot(
        String dimension,
        String title,
        Map<String, Object> position,
        String excerpt,
        String problem
    ) {
    }

    record PriorIssue(
        String issueNo, IssueSnapshot issue, String status, boolean manuallyResolved, Set<String> anchors
    ) {}

    record CurrentIssue(IssueSnapshot issue, Set<String> anchors) {}

    record Match(String status, String relatedIssueNo) {}
    private record Scored(PriorIssue prior, int score) {}
}
