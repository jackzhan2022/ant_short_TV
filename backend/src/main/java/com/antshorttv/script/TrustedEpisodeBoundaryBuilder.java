package com.antshorttv.script;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class TrustedEpisodeBoundaryBuilder {
    public Optional<List<EpisodeSplitBoundaryResolver.Boundary>> build(
        String source,
        List<ScriptSplitChunkPlanner.TrustedAnchor> anchors
    ) {
        if (source == null || source.isEmpty() || anchors == null) {
            return Optional.empty();
        }
        List<ScriptSplitChunkPlanner.TrustedAnchor> headings = anchors.stream()
            .filter(anchor -> "EPISODE_HEADING".equals(anchor.signal()))
            .filter(anchor -> anchor.offset() >= 0 && anchor.offset() < source.length())
            .sorted(Comparator.comparingInt(ScriptSplitChunkPlanner.TrustedAnchor::offset))
            .collect(java.util.stream.Collectors.collectingAndThen(
                java.util.stream.Collectors.toMap(
                    ScriptSplitChunkPlanner.TrustedAnchor::offset, anchor -> anchor,
                    (left, right) -> left, java.util.LinkedHashMap::new),
                values -> List.copyOf(values.values())));
        if (headings.size() < 2) {
            return Optional.empty();
        }
        String finalMarker = uniqueFinalMarker(source);
        if (finalMarker == null) {
            return Optional.empty();
        }
        List<EpisodeSplitBoundaryResolver.Boundary> boundaries = new java.util.ArrayList<>();
        for (int index = 0; index < headings.size(); index++) {
            var heading = headings.get(index);
            String title = heading.marker().strip().replace('\r', ' ').replace('\n', ' ');
            if (title.length() > 200) title = title.substring(0, 200);
            String endMarker = index + 1 < headings.size()
                ? headings.get(index + 1).marker() : finalMarker;
            boundaries.add(new EpisodeSplitBoundaryResolver.Boundary(
                title, heading.marker(), endMarker));
        }
        return Optional.of(List.copyOf(boundaries));
    }

    private String uniqueFinalMarker(String source) {
        int end = source.length();
        while (end > 0 && Character.isWhitespace(source.charAt(end - 1))) end--;
        if (end == 0) return null;
        int start = Math.max(0, source.lastIndexOf('\n', end - 1) + 1);
        int lowerBound = Math.max(0, end - 2000);
        while (start > lowerBound) {
            String marker = source.substring(start, end);
            if (source.indexOf(marker) == source.lastIndexOf(marker)) return marker;
            int previous = source.lastIndexOf('\n', Math.max(0, start - 2));
            start = Math.max(lowerBound, previous + 1);
        }
        String marker = source.substring(lowerBound, end);
        return source.indexOf(marker) == source.lastIndexOf(marker) ? marker : null;
    }
}
