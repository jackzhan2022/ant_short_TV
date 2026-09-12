package com.antshorttv.script;

/** Fields required to render episode navigation without loading persisted episode bodies. */
record ScriptEpisodeNavigation(
    Long episodeId,
    Integer episodeNo,
    String title,
    String summary,
    String contentFingerprint,
    Long generatedByRunId,
    ScriptEpisodeSummaryDocument formalSummary
) {
    static ScriptEpisodeNavigation from(ScriptEpisodeEntity entity, ScriptEpisodeSummaryDocument formalSummary) {
        return new ScriptEpisodeNavigation(entity.getId(), entity.getEpisodeNo(), entity.getTitle(),
            ScriptEpisodeSummaryDocument.summaryText(formalSummary), entity.getContentFingerprint(), entity.getGeneratedByRunId(), formalSummary);
    }

    ScriptEpisodeResponse withoutContent() {
        return new ScriptEpisodeResponse(episodeId, episodeNo, title, null, summary, contentFingerprint,
            generatedByRunId, formalSummary);
    }
}
