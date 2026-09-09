package com.antshorttv.workflowagent.tool;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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

    private static final Pattern STRUCTURAL_METADATA = Pattern.compile(
        "^(出场人物|登场人物|人物列表|角色列表|演员)[：:].*");
    private static final Pattern STRUCTURAL_ACTION = Pattern.compile(
        "^(镜头|画面|动作|时间|地点)[：:].*");

    public List<EpisodeSourceSegment> segment(String source) {
        return segment(source, SegmentationContext.legacy());
    }

    public List<EpisodeSourceSegment> segment(String source, SegmentationContext context) {
        if (source == null || source.isEmpty()) return List.of();
        SegmentationContext effectiveContext = context == null
            ? SegmentationContext.legacy() : context;
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
                Classification classification;
                if (pendingSpeechType != null) {
                    classification = new Classification(pendingSpeechType, null);
                    pendingSpeechType = null;
                } else {
                    classification = classify(text, effectiveContext);
                    pendingSpeechType = speechCueType(text.strip(), effectiveContext);
                }
                segments.add(new EpisodeSourceSegment(
                    "S%04d".formatted(ordinal++), classification.type(), text, start, end,
                    classification.type() != SourceSegmentType.METADATA,
                    classification.warning()));
            }
            if (end >= source.length()) break;
            start = end + 1;
            if (source.charAt(end) == '\r' && start < source.length() && source.charAt(start) == '\n') {
                start++;
            }
        }
        return List.copyOf(segments);
    }

    private Classification classify(String text, SegmentationContext context) {
        String value = text.strip();
        String withoutHeading = value.replaceFirst("^#+\\s*", "");
        String upper = withoutHeading.toUpperCase(Locale.ROOT);
        if (isMetadata(value, withoutHeading)
            || STRUCTURAL_METADATA.matcher(withoutHeading).matches()) {
            return new Classification(SourceSegmentType.METADATA, null);
        }
        if (withoutHeading.matches("^(场景|场次)[：:].+")
            || upper.matches("^(INT|EXT|INT/EXT|EXT/INT)[.． ].*")) {
            return new Classification(SourceSegmentType.SCENE, null);
        }
        if (isSubtitleOrActionLabel(withoutHeading)
            || withoutHeading.matches("^[△▲].*")
            || STRUCTURAL_ACTION.matcher(withoutHeading).matches()) {
            return new Classification(SourceSegmentType.ACTION, null);
        }
        var speech = SPEECH_LINE.matcher(withoutHeading);
        if (speech.matches()) {
            SourceSegmentType explicit = explicitSpeechType(speech.group(1));
            if (explicit != null) return new Classification(explicit, null);
            if (context.permissive() || context.isKnownSpeaker(speech.group(1))) {
                return new Classification(SourceSegmentType.DIALOGUE, null);
            }
            return new Classification(SourceSegmentType.ACTION, "SOURCE_SPEAKER_UNCONFIRMED");
        }
        return new Classification(SourceSegmentType.ACTION, null);
    }

    private SourceSegmentType speechCueType(String value, SegmentationContext context) {
        String withoutHeading = value.replaceFirst("^#+\\s*", "");
        if (STRUCTURAL_METADATA.matcher(withoutHeading).matches()
            || STRUCTURAL_ACTION.matcher(withoutHeading).matches()
            || isSubtitleOrActionLabel(withoutHeading)
            || withoutHeading.matches("^(场景|场次)[：:].*")) {
            return null;
        }
        var cue = SPEECH_CUE.matcher(withoutHeading);
        if (!cue.matches()) return null;
        SourceSegmentType explicit = explicitSpeechType(cue.group(1));
        if (explicit != null) return explicit;
        return context.permissive() || context.isKnownSpeaker(cue.group(1))
            ? SourceSegmentType.DIALOGUE : null;
    }

    private SourceSegmentType explicitSpeechType(String prefix) {
        if (INNER_OS_MARKER.matcher(prefix).find()) return SourceSegmentType.INNER_OS;
        if (NARRATION_MARKER.matcher(prefix).find()
            || prefix.contains("旁白") || prefix.contains("画外音")) {
            return SourceSegmentType.NARRATION;
        }
        return null;
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
        boolean requiredCoverage,
        String classificationWarning
    ) {}

    public record SegmentationContext(
        Set<String> speakerNames,
        Set<String> speakerAliases,
        boolean permissive
    ) {
        public SegmentationContext(Set<String> speakerNames, Set<String> speakerAliases) {
            this(speakerNames, speakerAliases, false);
        }

        public SegmentationContext {
            speakerNames = normalized(speakerNames);
            speakerAliases = normalized(speakerAliases);
        }

        static SegmentationContext legacy() {
            return new SegmentationContext(Set.of(), Set.of(), true);
        }

        boolean isKnownSpeaker(String prefix) {
            String candidate = normalizeSpeaker(prefix);
            return speakerNames.contains(candidate) || speakerAliases.contains(candidate);
        }

        private static Set<String> normalized(Set<String> values) {
            if (values == null || values.isEmpty()) return Set.of();
            Set<String> result = new LinkedHashSet<>();
            values.stream().map(SegmentationContext::normalizeSpeaker)
                .filter(value -> !value.isEmpty()).forEach(result::add);
            return Set.copyOf(result);
        }

        private static String normalizeSpeaker(String value) {
            if (value == null) return "";
            return value.replaceFirst("[（(].*$", "").strip().toLowerCase(Locale.ROOT);
        }
    }

    private record Classification(SourceSegmentType type, String warning) {}
}
