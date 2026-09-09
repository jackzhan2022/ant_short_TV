package com.antshorttv.script;

import java.util.List;

/** Saving requires lossless source coverage; heading/numbering differences are advisory only. */
public final class EpisodeGranularityValidator {
    public void validate(String source, List<ScriptEpisodeResponse> episodes) {
        if (source == null || source.isBlank() || episodes == null || episodes.isEmpty()) {
            throw new IllegalArgumentException("原文和分段结果不能为空。");
        }
        int cursor = 0;
        for (ScriptEpisodeResponse episode : episodes) {
            String content = episode == null ? null : episode.content();
            if (content == null || content.isEmpty() || !source.startsWith(content, cursor)) {
                throw new IllegalArgumentException("分段必须按顺序完整覆盖原文，不能遗漏、重复或改写原文。");
            }
            cursor += content.length();
        }
        if (cursor != source.length()) {
            throw new IllegalArgumentException("分段必须完整覆盖原文，不能遗漏原文末尾。");
        }
    }
}
