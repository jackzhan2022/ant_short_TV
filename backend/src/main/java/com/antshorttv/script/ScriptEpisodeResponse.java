package com.antshorttv.script;

public record ScriptEpisodeResponse(
    Long episodeId,
    Integer episodeNo,
    String title,
    String content,
    String summary,
    String contentFingerprint,
    Long generatedByRunId,
    ScriptEpisodeSummaryDocument formalSummary
) {
    public ScriptEpisodeResponse(Integer episodeNo, String title, String content) {
        this(null, episodeNo, title, content, null, null, null, null);
    }

    public ScriptEpisodeResponse(Long episodeId, Integer episodeNo, String title, String content, String summary) {
        this(episodeId, episodeNo, title, content, summary, null, null, null);
    }
}
