package com.antshorttv.script;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class EpisodeGranularityValidator {
    private final EpisodeHeadingClassifier classifier = new EpisodeHeadingClassifier();

    public void validate(String source, List<ScriptEpisodeResponse> episodes) {
        List<EpisodeHeadingClassifier.Heading> classified = classifier.scan(source);
        if (classified.stream().anyMatch(item -> item.type() == EpisodeHeadingClassifier.Type.AMBIGUOUS)) {
            throw invalid("显式集标题存在重复或缺号歧义，请核对后重试。");
        }
        List<EpisodeHeadingClassifier.Heading> headings = classified.stream()
            .filter(item -> item.type() == EpisodeHeadingClassifier.Type.SINGLE).toList();
        if (headings.isEmpty()) return;
        validateSequence(headings);
        if (episodes == null || episodes.size() != headings.size()) {
            throw invalid("检测到 " + headings.size() + " 个明确单集边界，但提交了 "
                + (episodes == null ? 0 : episodes.size()) + " 集，不能将多集合并为剧情分组。");
        }
        int cursor = 0;
        for (int index = 0; index < episodes.size(); index++) {
            ScriptEpisodeResponse episode = episodes.get(index);
            int end = cursor + (episode.content() == null ? 0 : episode.content().length());
            EpisodeHeadingClassifier.Heading heading = headings.get(index);
            if (episode.episodeNo() == null || episode.episodeNo() != heading.episodeNo()
                || heading.startOffset() < cursor || heading.startOffset() >= end) {
                throw invalid("第 " + (index + 1) + " 个显式单集边界未被对齐保留。");
            }
            int contained = 0;
            for (EpisodeHeadingClassifier.Heading candidate : headings) {
                if (candidate.startOffset() >= cursor && candidate.startOffset() < end) contained++;
            }
            if (contained != 1) {
                throw invalid("每个保存剧集必须且只能包含一个明确单集边界。");
            }
            cursor = end;
        }
    }

    private void validateSequence(List<EpisodeHeadingClassifier.Heading> headings) {
        Set<Integer> seen = new HashSet<>();
        int previous = 0;
        for (EpisodeHeadingClassifier.Heading heading : headings) {
            if (!seen.add(heading.episodeNo()) || (previous > 0 && heading.episodeNo() != previous + 1)) {
                throw invalid("显式集标题存在重复或缺号歧义，请核对后重试。");
            }
            previous = heading.episodeNo();
        }
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
