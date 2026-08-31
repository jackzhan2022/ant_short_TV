package com.antshorttv.script;

import java.util.ArrayList;
import java.util.List;

public final class EpisodeSplitBoundaryResolver {
    public List<ScriptEpisodeResponse> resolve(String source, List<Boundary> boundaries) {
        if (source == null || source.isBlank()) {
            throw invalid("当前剧本为空，无法分集。");
        }
        if (boundaries == null || boundaries.isEmpty()) {
            throw invalid("分集结果不能为空。");
        }
        List<ResolvedBoundary> resolved = new ArrayList<>();
        for (int index = 0; index < boundaries.size(); index++) {
            Boundary boundary = boundaries.get(index);
            if (boundary == null || blank(boundary.title()) || blank(boundary.startMarker())
                || blank(boundary.endMarker())) {
                throw invalid("第 " + (index + 1) + " 集标题和边界标记不能为空。");
            }
            MarkerMatch start = uniqueMatch(source, boundary.startMarker());
            MarkerMatch end = uniqueMatch(source, boundary.endMarker());
            if (end.start() < start.start()) {
                throw invalid("第 " + (index + 1) + " 集边界顺序颠倒。");
            }
            resolved.add(new ResolvedBoundary(
                boundary, start.start(), end.start(), end.endExclusive()));
        }

        for (int index = 0; index < resolved.size(); index++) {
            ResolvedBoundary current = resolved.get(index);
            if (index > 0 && current.start() <= resolved.get(index - 1).start()) {
                throw invalid("分集起始边界顺序错误或重叠。");
            }
            int spanEnd = index + 1 < resolved.size() ? resolved.get(index + 1).start() : source.length();
            boolean endsAtNextStart = index + 1 < resolved.size()
                && current.endStart() == spanEnd;
            if (current.endExclusive() > spanEnd && !endsAtNextStart) {
                throw invalid("第 " + (index + 1) + " 集边界与下一集重叠。");
            }
            int coveredUntil = endsAtNextStart ? spanEnd : current.endExclusive();
            if (!source.substring(coveredUntil, spanEnd).isBlank()) {
                throw invalid("第 " + (index + 1) + " 集结束边界后存在未覆盖缺口。");
            }
        }

        List<ScriptEpisodeResponse> episodes = new ArrayList<>();
        for (int index = 0; index < resolved.size(); index++) {
            int contentStart = index == 0 ? 0 : resolved.get(index).start();
            int contentEnd = index + 1 < resolved.size() ? resolved.get(index + 1).start() : source.length();
            episodes.add(new ScriptEpisodeResponse(index + 1, resolved.get(index).boundary().title(),
                source.substring(contentStart, contentEnd)));
        }
        return List.copyOf(episodes);
    }

    private MarkerMatch uniqueMatch(String source, String marker) {
        int first = source.indexOf(marker);
        if (first >= 0) {
            if (source.indexOf(marker, first + 1) >= 0) {
                throw invalid("边界标记在剧本中重复，无法唯一定位：" + marker);
            }
            return new MarkerMatch(first, first + marker.length());
        }

        CanonicalText canonicalSource = canonicalize(source, true);
        CanonicalText canonicalMarker = canonicalize(marker, false);
        if (canonicalMarker.text().isEmpty()) {
            throw invalid("剧本中找不到边界标记：" + marker);
        }
        int normalizedFirst = canonicalSource.text().indexOf(canonicalMarker.text());
        if (normalizedFirst < 0) {
            throw invalid("剧本中找不到边界标记：" + marker);
        }
        if (canonicalSource.text().indexOf(canonicalMarker.text(), normalizedFirst + 1) >= 0) {
            throw invalid("边界标记在剧本中重复，无法唯一定位：" + marker);
        }
        int start = canonicalSource.sourceOffsets().get(normalizedFirst);
        int lastCanonical = normalizedFirst + canonicalMarker.text().length() - 1;
        int endExclusive = canonicalSource.sourceOffsets().get(lastCanonical) + 1;
        while (endExclusive < source.length()
            && !Character.isLetterOrDigit(source.charAt(endExclusive))) {
            endExclusive++;
        }
        return new MarkerMatch(start, endExclusive);
    }

    private CanonicalText canonicalize(String value, boolean retainOffsets) {
        StringBuilder text = new StringBuilder();
        List<Integer> offsets = new ArrayList<>();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isLetterOrDigit(character)) {
                text.append(character);
                if (retainOffsets) {
                    offsets.add(index);
                }
            }
        }
        return new CanonicalText(text.toString(), List.copyOf(offsets));
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    public record Boundary(String title, String startMarker, String endMarker) {}

    private record MarkerMatch(int start, int endExclusive) {}

    private record CanonicalText(String text, List<Integer> sourceOffsets) {}

    private record ResolvedBoundary(
        Boundary boundary, int start, int endStart, int endExclusive
    ) {}
}
