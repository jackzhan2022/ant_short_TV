package com.antshorttv.script;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Recomputed for the current workspace so later episode edits do not retain stale notices. */
public final class EpisodeSplitWarnings {
    public List<Warning> inspect(String source, List<ScriptEpisodeResponse> episodes) {
        var classified = new EpisodeHeadingClassifier().scan(source);
        var headings = classified.stream()
            .filter(item -> item.type() == EpisodeHeadingClassifier.Type.SINGLE
                || item.type() == EpisodeHeadingClassifier.Type.AMBIGUOUS)
            .toList();
        Set<Warning> warnings = new LinkedHashSet<>();
        Set<Integer> seen = new HashSet<>();
        int previous = 0;
        for (var heading : headings) {
            int number = heading.episodeNo();
            if (!seen.add(number)) {
                warnings.add(new Warning("SOURCE_NUMBER_DUPLICATE",
                    "原稿存在重复的第 " + number + " 集标题，可按当前分段继续处理。"));
            }
            if (previous > 0 && number > previous + 1) {
                String missing = number == previous + 2 ? "第 " + (previous + 1) + " 集标题"
                    : "第 " + (previous + 1) + "～" + (number - 1) + " 集标题";
                warnings.add(new Warning("SOURCE_NUMBER_GAP",
                    "原稿标题从第 " + previous + " 集跳到第 " + number + " 集，未识别到"
                        + missing + "；这不代表正文缺失。"));
            } else if (previous > 0 && number < previous) {
                warnings.add(new Warning("SOURCE_NUMBER_ORDER",
                    "原稿标题从第 " + previous + " 集回到第 " + number + " 集，编号未按顺序排列。"));
            }
            previous = number;
        }
        if (!headings.isEmpty() && episodes != null && !episodes.isEmpty()
            && boundariesDiffer(source, classified, headings, episodes)) {
            warnings.add(new Warning("EPISODE_BOUNDARY_DIFFERENCE",
                "原稿识别到 " + headings.size() + " 个单集标题，当前为 " + episodes.size()
                    + " 个分段；分段与原稿标题并非一一对应，可按需拆分或合并。"));
        }
        return List.copyOf(warnings);
    }

    private boolean boundariesDiffer(
        String source,
        List<EpisodeHeadingClassifier.Heading> classified,
        List<EpisodeHeadingClassifier.Heading> headings,
        List<ScriptEpisodeResponse> episodes
    ) {
        // Legacy previews strip headings/whitespace; their lengths are not original-source offsets.
        String combined = episodes.stream().map(episode -> episode.content() == null ? "" : episode.content())
            .collect(java.util.stream.Collectors.joining());
        if (!combined.equals(source)) return false;
        if (headings.size() != episodes.size()) return true;
        int cursor = 0;
        for (int index = 0; index < episodes.size(); index++) {
            var heading = headings.get(index);
            int end = cursor + episodes.get(index).content().length();
            if (heading.startOffset() < cursor || heading.startOffset() >= end) return true;
            if (index > 0) {
                int expectedStart = heading.startOffset();
                int classifiedIndex = classified.indexOf(heading);
                if (classifiedIndex > 0) {
                    var previous = classified.get(classifiedIndex - 1);
                    if (previous.type() == EpisodeHeadingClassifier.Type.GROUP
                        && previous.episodeNo() == heading.episodeNo()) {
                        expectedStart = previous.startOffset();
                    }
                }
                if (cursor > expectedStart || !source.substring(cursor, expectedStart).isBlank()) return true;
            }
            cursor = end;
        }
        return false;
    }

    public record Warning(String code, String message) {}
}
