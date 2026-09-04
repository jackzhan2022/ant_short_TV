package com.antshorttv.workflowagent.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class EpisodeSourceSegmenter {
    private static final Pattern EPISODE_TITLE = Pattern.compile(
        "^第[0-9一二三四五六七八九十百零〇两]+集(?:[:：].*)?$");
    private static final Pattern EPISODE_RANGE_HEADING = Pattern.compile(
        "^第.+集到第.+集剧情$");
    private static final Pattern SPEECH_LINE = Pattern.compile("^([^\\r\\n:：]{1,80})[：:]\\s*(.+)$");
    private static final Pattern SPEECH_CUE = Pattern.compile("^([^\\r\\n:：]{1,80})[：:]\\s*$");
    private static final Pattern INNER_OS_MARKER = Pattern.compile(
        "(^|[^A-Z0-9])O\\.?S\\.?(?=[^A-Z0-9]|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NARRATION_MARKER = Pattern.compile(
        "(^|[^A-Z0-9])V\\.?(?:O|S)\\.?(?=[^A-Z0-9]|$)", Pattern.CASE_INSENSITIVE);

    public List<EpisodeSourceSegment> segment(String source) {
        if (source == null || source.isEmpty()) return List.of();
        List<EpisodeSourceSegment> segments = new ArrayList<>();
        int start = 0;
        int ordinal = 1;
        SourceSegmentType pendingSpeechType = null;
        while (start < source.length()) {
            int end = start;
            while (end < source.length() && source.charAt(end) != '\r' && source.charAt(end) != '\n') {
                end++;
            }
            String text = source.substring(start, end);
            if (!text.isBlank()) {
                SourceSegmentType type;
                if (pendingSpeechType != null) {
                    type = pendingSpeechType;
                    pendingSpeechType = null;
                } else {
                    type = classify(text);
                    pendingSpeechType = speechCueType(text.strip());
                }
                segments.add(new EpisodeSourceSegment(
                    "S%04d".formatted(ordinal++), type, text, start, end,
                    type != SourceSegmentType.METADATA));
            }
            if (end >= source.length()) break;
            start = end + 1;
            if (source.charAt(end) == '\r' && start < source.length() && source.charAt(start) == '\n') {
                start++;
            }
        }
        return List.copyOf(segments);
    }

    private SourceSegmentType classify(String text) {
        String value = text.strip();
        String withoutHeading = value.replaceFirst("^#+\\s*", "");
        String upper = withoutHeading.toUpperCase(Locale.ROOT);
        if (isMetadata(value, withoutHeading)) return SourceSegmentType.METADATA;
        if (withoutHeading.matches("^(场景|场次)[：:].+")
            || upper.matches("^(INT|EXT|INT/EXT|EXT/INT)[.． ].*")) {
            return SourceSegmentType.SCENE;
        }
        if (isSubtitleOrActionLabel(withoutHeading)) return SourceSegmentType.ACTION;
        var speech = SPEECH_LINE.matcher(withoutHeading);
        if (speech.matches()) return speechType(speech.group(1));
        return SourceSegmentType.ACTION;
    }

    private SourceSegmentType speechCueType(String value) {
        var cue = SPEECH_CUE.matcher(value.replaceFirst("^#+\\s*", ""));
        return cue.matches() ? speechType(cue.group(1)) : null;
    }

    private SourceSegmentType speechType(String prefix) {
        if (INNER_OS_MARKER.matcher(prefix).find()) return SourceSegmentType.INNER_OS;
        if (NARRATION_MARKER.matcher(prefix).find()
            || prefix.contains("旁白") || prefix.contains("画外音")) {
            return SourceSegmentType.NARRATION;
        }
        return SourceSegmentType.DIALOGUE;
    }

    private boolean isSubtitleOrActionLabel(String value) {
        return value.matches("^[【\\[]字幕[：:].*[】\\]]$")
            || value.matches("^(结尾钩子|钩子|时间|地点|画面|动作)[：:].*");
    }

    private boolean isMetadata(String value, String withoutHeading) {
        if (value.matches("^-{3,}$")) return true;
        if (EPISODE_TITLE.matcher(withoutHeading).matches()
            || EPISODE_RANGE_HEADING.matcher(withoutHeading).matches()) {
            return true;
        }
        if (!value.startsWith("#")) return false;
        return withoutHeading.matches("^(目标时长|平台|风格|节拍|剧情梗概)[：:].*")
            || !withoutHeading.matches("^(场景|场次)[：:].*");
    }

    public enum SourceSegmentType {
        METADATA,
        SCENE,
        ACTION,
        DIALOGUE,
        NARRATION,
        INNER_OS
    }

    public record EpisodeSourceSegment(
        String id,
        SourceSegmentType type,
        String text,
        int startOffset,
        int endOffset,
        boolean requiredCoverage
    ) {}
}
