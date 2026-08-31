package com.antshorttv.review;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ReviewUnitPlanner {
    private static final Pattern EPISODE = Pattern.compile(
        "(?im)^(?:第\\s*(\\d{1,3})\\s*集|EP\\s*0*(\\d{1,3}))\\b.*$");

    public List<Unit> plan(String source, String scopeType, Map<String, Object> scope,
        ReviewContentService.FrozenReview frozen, int maxCharacters, int overlapCharacters) {
        String script = source == null ? "" : source;
        int maximum = Math.max(16, maxCharacters);
        int overlap = Math.min(Math.max(0, overlapCharacters), maximum / 3);
        List<Range> ranges = ranges(script, scopeType, scope, frozen);
        List<Unit> result = new ArrayList<>();
        int number = 1;
        for (Range range : ranges) {
            int start = range.start;
            while (start < range.end) {
                int hardEnd = Math.min(range.end, start + maximum);
                int end = hardEnd == range.end ? hardEnd : boundary(script, start, hardEnd);
                if (end <= start) end = hardEnd;
                String value = script.substring(start, end);
                result.add(new Unit(number++, "offset-%d-%d".formatted(start, end), start, end,
                    value, ReviewContentService.hash(value)));
                if (end == range.end) break;
                start = Math.max(start + 1, end - overlap);
            }
        }
        if (result.isEmpty() && !script.isEmpty()) throw invalid("审核范围无法生成有效单元。");
        return List.copyOf(result);
    }

    private List<Range> ranges(String script, String scopeType, Map<String, Object> scope,
        ReviewContentService.FrozenReview frozen) {
        String type = scopeType == null ? "ALL" : scopeType.toUpperCase();
        if ("ALL".equals(type)) return script.isEmpty() ? List.of() : List.of(new Range(0, script.length()));
        if ("SCENES".equals(type)) return merge(frozen.segments().stream()
            .map(segment -> new Range(segment.startOffset(), segment.endOffset())).toList());
        if (!"EPISODES".equals(type)) throw invalid("未知审核范围。");
        Set<String> selected = values(scope, "episodeNos");
        List<Start> starts = new ArrayList<>();
        Matcher matcher = EPISODE.matcher(script);
        while (matcher.find()) starts.add(new Start(
            Integer.parseInt(matcher.group(1) == null ? matcher.group(2) : matcher.group(1)), matcher.start()));
        if (starts.isEmpty()) return selected.contains("1") ? List.of(new Range(0, script.length())) : List.of();
        List<Range> ranges = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            Start start = starts.get(i);
            if (selected.contains(String.valueOf(start.episode))) {
                ranges.add(new Range(start.offset, i + 1 < starts.size() ? starts.get(i + 1).offset : script.length()));
            }
        }
        return ranges;
    }

    private int boundary(String source, int start, int hardEnd) {
        int paragraph = source.lastIndexOf("\n\n", hardEnd);
        if (paragraph > start + (hardEnd - start) / 2) return paragraph + 2;
        int newline = source.lastIndexOf('\n', hardEnd);
        return newline > start + (hardEnd - start) / 2 ? newline + 1 : hardEnd;
    }

    private List<Range> merge(List<Range> input) {
        List<Range> sorted = input.stream().sorted(Comparator.comparingInt(Range::start)).toList();
        List<Range> result = new ArrayList<>();
        for (Range next : sorted) {
            if (result.isEmpty() || next.start > result.get(result.size() - 1).end) result.add(next);
            else {
                Range previous = result.remove(result.size() - 1);
                result.add(new Range(previous.start, Math.max(previous.end, next.end)));
            }
        }
        return result;
    }

    private Set<String> values(Map<String, Object> scope, String key) {
        if (scope == null || !(scope.get(key) instanceof List<?> list)) return Set.of();
        return list.stream().map(String::valueOf).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }

    public record Unit(int unitNo, String unitKey, int startOffset, int endOffset,
                       String content, String fingerprint) {}
    private record Range(int start, int end) {}
    private record Start(int episode, int offset) {}
}
