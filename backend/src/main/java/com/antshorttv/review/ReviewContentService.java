package com.antshorttv.review;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ReviewContentService {
    private static final Pattern EPISODE = Pattern.compile(
        "^(?:第\\s*(\\d{1,3})\\s*集|EP\\s*0*(\\d{1,3}))\\b.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCENE = Pattern.compile(
        "^(?:(?:第\\s*)?([0-9]{1,3}(?:[-.]\\d{1,3})?)(?:\\s*场)?|场景\\s*([^\\s:：]+)|SCENE\\s*([^\\s:：]+))(?:\\s+|[:：]).*$",
        Pattern.CASE_INSENSITIVE);

    public FrozenReview freeze(String content, String scopeType, Map<String, Object> scope, List<String> dimensions) {
        String source = content == null ? "" : content;
        String type = scopeType == null ? "ALL" : scopeType.trim().toUpperCase();
        if (!Set.of("ALL", "EPISODES", "SCENES").contains(type)) {
            throw invalid("未知审核范围。");
        }
        List<Line> lines = lines(source);
        List<Span> episodes = episodeSpans(source, lines);
        List<Segment> scenes = sceneSegments(source, lines);
        List<Segment> selectedSegments;
        String scoped;
        if ("EPISODES".equals(type)) {
            Set<String> selected = values(scope, "episodeNos");
            if (selected.isEmpty()) throw invalid("指定集范围不能为空。");
            List<Span> spans = episodes.stream().filter(span -> selected.contains(String.valueOf(span.episodeNo))).toList();
            if (spans.isEmpty() || spans.size() != selected.size()) throw invalid("指定集不在当前剧本中。");
            scoped = spans.stream().map(span -> source.substring(span.start, span.end).trim())
                .collect(java.util.stream.Collectors.joining("\n\n"));
            selectedSegments = scenes.stream().filter(segment ->
                segment.episodeNo() != null && selected.contains(String.valueOf(segment.episodeNo()))).toList();
        } else if ("SCENES".equals(type)) {
            Set<String> selected = values(scope, "sceneKeys");
            if (selected.isEmpty()) selected = values(scope, "sceneNos");
            if (selected.isEmpty()) throw invalid("指定场范围不能为空。");
            Set<String> finalSelected = selected;
            selectedSegments = scenes.stream().filter(segment -> finalSelected.contains(segment.sceneKey())).toList();
            if (selectedSegments.isEmpty() || selectedSegments.stream().map(Segment::sceneKey).distinct().count() != selected.size()) {
                throw invalid("指定场不在当前剧本中。");
            }
            scoped = selectedSegments.stream().map(Segment::content)
                .collect(java.util.stream.Collectors.joining("\n\n"));
        } else {
            scoped = source;
            selectedSegments = scenes;
        }
        if (selectedSegments.isEmpty() && !scoped.isBlank()) {
            selectedSegments = List.of(segment(source, null, "offset-0", 0, source.length()));
        }
        List<String> orderedDimensions = dimensions == null ? List.of()
            : dimensions.stream().filter(java.util.Objects::nonNull).map(String::trim)
                .filter(value -> !value.isEmpty()).distinct().sorted().toList();
        String versionHash = hash(source);
        String scopeHash = hash(type + "|" + canonicalScope(scope));
        String dimensionsHash = hash(String.join("|", orderedDimensions));
        return new FrozenReview(scoped, versionHash, scopeHash, dimensionsHash,
            hash(versionHash + scopeHash + dimensionsHash), List.copyOf(selectedSegments),
            source.isEmpty() ? 0 : lines.size());
    }

    public void requireQuickBudget(FrozenReview review, int safeCharacters) {
        if (review.content().length() > safeCharacters) {
            throw invalid("REVIEW_SCOPE_TOO_LARGE_FOR_QUICK：请缩小审核范围或改用 DEEP。");
        }
    }

    private List<Line> lines(String source) {
        List<Line> result = new ArrayList<>();
        int start = 0;
        int lineNo = 1;
        for (int index = 0; index <= source.length(); index++) {
            if (index == source.length() || source.charAt(index) == '\n') {
                int textEnd = index > start && source.charAt(index - 1) == '\r' ? index - 1 : index;
                result.add(new Line(lineNo++, start, index == source.length() ? index : index + 1,
                    source.substring(start, textEnd)));
                start = index + 1;
            }
        }
        return result;
    }

    private List<Span> episodeSpans(String source, List<Line> lines) {
        List<Span> result = new ArrayList<>();
        Integer currentNo = null;
        int currentStart = 0;
        for (Line line : lines) {
            Matcher matcher = EPISODE.matcher(line.text.trim());
            if (!matcher.matches()) continue;
            if (currentNo != null) result.add(new Span(currentNo, currentStart, line.start));
            currentNo = Integer.parseInt(matcher.group(1) == null ? matcher.group(2) : matcher.group(1));
            currentStart = line.start;
        }
        if (currentNo == null) return List.of(new Span(1, 0, source.length()));
        result.add(new Span(currentNo, currentStart, source.length()));
        return result;
    }

    private List<Segment> sceneSegments(String source, List<Line> lines) {
        List<SceneStart> starts = new ArrayList<>();
        Integer episodeNo = null;
        for (Line line : lines) {
            Matcher episode = EPISODE.matcher(line.text.trim());
            if (episode.matches()) {
                episodeNo = Integer.parseInt(episode.group(1) == null ? episode.group(2) : episode.group(1));
                continue;
            }
            Matcher scene = SCENE.matcher(line.text.trim());
            if (scene.matches()) {
                String key = scene.group(1) != null ? scene.group(1).replace('.', '-')
                    : scene.group(2) != null ? scene.group(2) : scene.group(3);
                starts.add(new SceneStart(key, episodeNo, line.start));
            }
        }
        List<Segment> result = new ArrayList<>();
        for (int index = 0; index < starts.size(); index++) {
            SceneStart start = starts.get(index);
            int end = index + 1 < starts.size() ? starts.get(index + 1).offset : source.length();
            result.add(segment(source, start.episodeNo, start.key, start.offset, end));
        }
        return result;
    }

    private Segment segment(String source, Integer episodeNo, String key, int start, int end) {
        String value = source.substring(start, end).trim();
        int lineStart = 1 + (int) source.substring(0, start).chars().filter(c -> c == '\n').count();
        int lineEnd = lineStart + (int) value.chars().filter(c -> c == '\n').count();
        int paragraphStart = 1 + paragraphBreaks(source.substring(0, start));
        int paragraphEnd = paragraphStart + paragraphBreaks(value);
        String anchor = "episode:" + (episodeNo == null ? "?" : episodeNo)
            + "/scene:" + key + "/offset:" + start;
        return new Segment(anchor, episodeNo, key, lineStart, lineEnd, paragraphStart, paragraphEnd,
            start, end, value, hash(value));
    }

    private int paragraphBreaks(String value) {
        return value.split("(?:\\r?\\n){2,}", -1).length - 1;
    }

    private Set<String> values(Map<String, Object> scope, String key) {
        if (scope == null || !(scope.get(key) instanceof List<?> values)) return Set.of();
        return values.stream().filter(java.util.Objects::nonNull).map(Object::toString)
            .map(String::trim).filter(value -> !value.isEmpty())
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private String canonicalScope(Map<String, Object> scope) {
        if (scope == null || scope.isEmpty()) return "{}";
        Map<String, List<String>> normalized = new java.util.TreeMap<>();
        scope.forEach((key, value) -> {
            if (value instanceof List<?> list) {
                normalized.put(key, list.stream().filter(java.util.Objects::nonNull)
                    .map(Object::toString).map(String::trim).distinct().sorted().toList());
            } else if (value != null) {
                normalized.put(key, List.of(value.toString().trim()));
            }
        });
        return normalized.toString();
    }

    static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }

    private record Line(int number, int start, int end, String text) {}
    private record Span(int episodeNo, int start, int end) {}
    private record SceneStart(String key, Integer episodeNo, int offset) {}

    public record Segment(
        String anchor, Integer episodeNo, String sceneKey, int lineStart, int lineEnd,
        int paragraphStart, int paragraphEnd,
        int startOffset, int endOffset, String content, String fingerprint
    ) {}

    public record FrozenReview(
        String content, String versionHash, String scopeHash, String dimensionsHash,
        String snapshotKey, List<Segment> segments, int lineCount
    ) {}
}
