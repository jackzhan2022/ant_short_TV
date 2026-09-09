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
        List<EpisodeHeadingClassifier.Heading> classified = new EpisodeHeadingClassifier().scan(source);
        if (classified.stream().anyMatch(item -> item.type() == EpisodeHeadingClassifier.Type.AMBIGUOUS)) {
            return Optional.empty();
        }
        List<EpisodeHeadingClassifier.Heading> headings = classified.stream()
            .filter(item -> item.type() == EpisodeHeadingClassifier.Type.SINGLE)
            .sorted(Comparator.comparingInt(EpisodeHeadingClassifier.Heading::startOffset))
            .toList();
        if (headings.size() < 2) {
            return Optional.empty();
        }
        String finalMarker = uniqueFinalMarker(source);
        if (finalMarker == null) {
            return Optional.empty();
        }
        List<EpisodeSplitBoundaryResolver.Boundary> boundaries = new java.util.ArrayList<>();
        for (int index = 0; index < headings.size(); index++) {
            EpisodeHeadingClassifier.Heading heading = headings.get(index);
            String title = heading.marker().strip().replace('\r', ' ').replace('\n', ' ');
            if (title.length() > 200) title = title.substring(0, 200);
            String endMarker = index + 1 < headings.size()
                ? startMarker(classified, headings.get(index + 1)) : finalMarker;
            boundaries.add(new EpisodeSplitBoundaryResolver.Boundary(
                title, startMarker(classified, heading), endMarker));
        }
        return Optional.of(List.copyOf(boundaries));
    }

    private String startMarker(
        List<EpisodeHeadingClassifier.Heading> classified,
        EpisodeHeadingClassifier.Heading episode
    ) {
        EpisodeHeadingClassifier.Heading previous = null;
        for (EpisodeHeadingClassifier.Heading candidate : classified) {
            if (candidate.startOffset() >= episode.startOffset()) break;
            previous = candidate;
        }
        if (previous != null && previous.type() == EpisodeHeadingClassifier.Type.GROUP
            && previous.episodeNo() == episode.episodeNo()) {
            return previous.marker();
        }
        return episode.marker();
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
