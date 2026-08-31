package com.antshorttv.script;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ScriptSplitChunkPlanner {
    private static final Pattern EPISODE = Pattern.compile(
        "(?m)^[\\t ]*(?:第[零一二三四五六七八九十百千万0-9]+集|EPISODE\\s+\\d+)\\b.*$");
    private static final Pattern SCENE = Pattern.compile(
        "(?m)^[\\t ]*(?:内景|外景|内外景|INT\\.?|EXT\\.?)[^\\r\\n]{0,200}$",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern PARAGRAPH = Pattern.compile("(?:\\r?\\n){2,}");
    private static final Pattern LINE = Pattern.compile("\\r?\\n");

    public List<ChunkPlan> plan(String source, ChunkSettings settings) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        validate(settings);
        List<Signal> signals = signals(source);
        List<ChunkPlan> chunks = new ArrayList<>();
        int coreStart = 0;
        int chunkNo = 1;
        while (coreStart < source.length()) {
            int remaining = source.length() - coreStart;
            Cut cut;
            if (remaining <= settings.targetMax()) {
                cut = new Cut(source.length(), "END");
            } else {
                int min = safeBoundary(source, coreStart + settings.targetMin(), false);
                int max = safeBoundary(source,
                    Math.min(source.length(), coreStart + settings.targetMax()), false);
                cut = chooseCut(signals, min, max);
                if (cut == null) {
                    int hard = safeBoundary(source,
                        Math.min(source.length(), coreStart + settings.hardMax()), false);
                    cut = new Cut(hard, "HARD_LIMIT");
                }
            }
            int coreEnd = Math.max(coreStart + 1, cut.offset());
            coreEnd = safeBoundary(source, coreEnd, false);
            int spare = Math.max(0, settings.hardMax() - (coreEnd - coreStart));
            int before = Math.min(settings.overlap(), spare / 2);
            int after = Math.min(settings.overlap(), spare - before);
            int contextStart = safeBoundary(source, Math.max(0, coreStart - before), true);
            int contextEnd = safeBoundary(source,
                Math.min(source.length(), coreEnd + after), false);
            if (contextEnd - contextStart > settings.hardMax()) {
                contextStart = safeBoundary(source, contextEnd - settings.hardMax(), false);
            }
            chunks.add(new ChunkPlan(chunkNo++, coreStart, coreEnd, contextStart, contextEnd,
                cut.signal(), anchors(source, signals, contextStart, contextEnd)));
            coreStart = coreEnd;
        }
        return List.copyOf(chunks);
    }

    private Cut chooseCut(List<Signal> signals, int min, int max) {
        for (String type : List.of("EPISODE_HEADING", "SCENE_HEADING", "PARAGRAPH", "LINE")) {
            Signal selected = signals.stream()
                .filter(signal -> signal.type().equals(type))
                .filter(signal -> signal.offset() >= min && signal.offset() <= max)
                .max(Comparator.comparingInt(Signal::offset)).orElse(null);
            if (selected != null) {
                return new Cut(selected.offset(), selected.type());
            }
        }
        return null;
    }

    private List<Signal> signals(String source) {
        List<Signal> result = new ArrayList<>();
        addSignals(result, EPISODE.matcher(source), "EPISODE_HEADING", true);
        addSignals(result, SCENE.matcher(source), "SCENE_HEADING", true);
        addSignals(result, PARAGRAPH.matcher(source), "PARAGRAPH", false);
        addSignals(result, LINE.matcher(source), "LINE", false);
        return result;
    }

    private void addSignals(List<Signal> result, Matcher matcher, String type, boolean atStart) {
        while (matcher.find()) {
            result.add(new Signal(atStart ? matcher.start() : matcher.end(), type));
        }
    }

    private List<TrustedAnchor> anchors(
        String source, List<Signal> signals, int contextStart, int contextEnd
    ) {
        return signals.stream()
            .filter(signal -> signal.offset() >= contextStart && signal.offset() < contextEnd)
            .filter(signal -> signal.type().endsWith("HEADING"))
            .limit(40)
            .map(signal -> {
                int end = signal.offset();
                while (end < contextEnd && end < source.length()
                    && source.charAt(end) != '\n' && end - signal.offset() < 500) {
                    end++;
                }
                return new TrustedAnchor(signal.offset(),
                    source.substring(signal.offset(), end), signal.type());
            }).filter(anchor -> !anchor.marker().isBlank()).toList();
    }

    private int safeBoundary(String source, int requested, boolean moveBackward) {
        int boundary = Math.max(0, Math.min(source.length(), requested));
        if (boundary > 0 && boundary < source.length()
            && Character.isHighSurrogate(source.charAt(boundary - 1))
            && Character.isLowSurrogate(source.charAt(boundary))) {
            return moveBackward ? boundary - 1 : boundary + 1;
        }
        return boundary;
    }

    private void validate(ChunkSettings settings) {
        if (settings.targetMin() <= 0 || settings.targetMax() < settings.targetMin()
            || settings.hardMax() < settings.targetMax() || settings.overlap() < 0) {
            throw new IllegalArgumentException("分块参数无效。");
        }
    }

    private record Signal(int offset, String type) {}
    private record Cut(int offset, String signal) {}

    public record ChunkSettings(int targetMin, int targetMax, int hardMax, int overlap) {}
    public record TrustedAnchor(int offset, String marker, String signal) {}
    public record ChunkPlan(
        int chunkNo, int coreStart, int coreEnd, int contextStart, int contextEnd,
        String boundarySignal, List<TrustedAnchor> anchors
    ) {}
}
